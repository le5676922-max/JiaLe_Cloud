package org.example.yunpan.controller;

import org.example.yunpan.config.SessionManager;
import org.example.yunpan.config.UserStore;
import org.example.yunpan.entity.User;
import org.example.yunpan.service.CryptoService;
import org.example.yunpan.util.JwtUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtUtils jwtUtils;
    private final SessionManager sessionManager;
    private final UserStore userStore;
    private final CryptoService cryptoService;

    @Value("${yunpan.security.master-key}")
    private String masterKey;

    public AuthController(JwtUtils jwtUtils, SessionManager sessionManager,
                          UserStore userStore, CryptoService cryptoService) {
        this.jwtUtils = jwtUtils;
        this.sessionManager = sessionManager;
        this.userStore = userStore;
        this.cryptoService = cryptoService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim();
        String clientHash = body.getOrDefault("password", ""); // 客户端已做 SHA-256

        if (email.isBlank() || clientHash.isBlank()) {
            return ResponseEntity.status(401)
                    .body(Map.of("code", 401, "message", "邮箱或密码不能为空"));
        }

        Optional<User> opt = userStore.findByEmail(email);
        if (opt.isEmpty() || !opt.get().getEnabled()) {
            return ResponseEntity.status(401)
                    .body(Map.of("code", 401, "message", "邮箱或密码错误"));
        }

        if (!userStore.verifyPassword(email, clientHash)) {
            return ResponseEntity.status(401)
                    .body(Map.of("code", 401, "message", "邮箱或密码错误"));
        }

        User user = opt.get();
        String loginId = sessionManager.createSession(email);
        String token = jwtUtils.generateToken(email, loginId);

        // 用客户端哈希解密文件密钥并缓存
        String encryptedKey = user.getEncryptedKey();
        if (encryptedKey != null && !encryptedKey.isBlank()) {
            try {
                byte[] decrypted = cryptoService.decryptBase64WithHexKey(clientHash, encryptedKey);
                String fileKeyBase64 = new String(decrypted, java.nio.charset.StandardCharsets.UTF_8);
                byte[] rawKey = java.util.Base64.getDecoder().decode(fileKeyBase64);
                String fileKeyHex = bytesToHex(rawKey);
                sessionManager.cacheFileKey(email, fileKeyHex);
            } catch (Exception e) {
                sessionManager.cacheFileKey(email, null);
            }
        }

        // 如果加密密钥获取失败，尝试用服务端主密钥从 recovery_key 恢复
        if (sessionManager.getFileKey(email) == null) {
            String recoveryKey = user.getRecoveryKey();
            if (recoveryKey != null && !recoveryKey.isBlank()) {
                try {
                    byte[] fileKeyBytes = cryptoService.decryptBase64WithHexKey(
                            masterKey, recoveryKey);
                    String fileKeyBase64 = new String(fileKeyBytes, java.nio.charset.StandardCharsets.UTF_8);
                    byte[] rawKey = java.util.Base64.getDecoder().decode(fileKeyBase64);
                    String fileKeyHex = bytesToHex(rawKey);
                    sessionManager.cacheFileKey(email, fileKeyHex);
                } catch (Exception ignored) {}
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("role", user.getRole());
        data.put("email", user.getEmail());
        data.put("membership", user.getMembership());

        return ResponseEntity.ok(Map.of("code", 200, "data", data));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                String email = jwtUtils.getUsername(token);
                sessionManager.destroySession(email);
            } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(Map.of("code", 200, "message", "已退出"));
    }
}
