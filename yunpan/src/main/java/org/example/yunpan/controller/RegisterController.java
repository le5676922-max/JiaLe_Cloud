package org.example.yunpan.controller;

import org.example.yunpan.config.UserStore;
import org.example.yunpan.entity.User;
import org.example.yunpan.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/register")
public class RegisterController {

    private final EmailService emailService;
    private final UserStore userStore;
    private final org.example.yunpan.service.CryptoService cryptoService;

    @Value("${yunpan.membership.free-quota:5368709120}")
    private long freeQuota;

    @Value("${yunpan.security.master-key}")
    private String masterKey;

    public RegisterController(EmailService emailService, UserStore userStore,
                              org.example.yunpan.service.CryptoService cryptoService) {
        this.emailService = emailService;
        this.userStore = userStore;
        this.cryptoService = cryptoService;
    }

    @PostMapping("/send-code")
    public ResponseEntity<Map<String, Object>> sendCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank() || !email.contains("@")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", 400, "message", "请输入有效的邮箱地址"));
        }

        if (userStore.findByEmail(email.trim()).isPresent()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", 400, "message", "该邮箱已被注册"));
        }

        try {
            emailService.sendCode(email.trim());
            return ResponseEntity.ok(Map.of("code", 200, "message", "验证码已发送"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(429)
                    .body(Map.of("code", 429, "message", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String code = body.get("code");
        String clientHash = body.get("password"); // 客户端 SHA-256(password)
        String encryptedKey = body.get("encryptedKey");

        if (email == null || code == null || clientHash == null
                || email.isBlank() || code.isBlank() || clientHash.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", 400, "message", "所有字段不能为空"));
        }

        if (!emailService.verifyCode(email.trim(), code.trim())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", 400, "message", "验证码错误或已过期"));
        }

        try {
            User user = new User();
            user.setEmail(email.trim());
            user.setPassword(clientHash);
            user.setEncryptedKey(encryptedKey);
            user.setRole("USER");
            user.setMembership("FREE");
            user.setQuota(freeQuota);
            user.setEnabled(true);

            // 生成 recovery_key：用服务端主密钥加密文件密钥，用于重启恢复和密码重置
            if (encryptedKey != null && !encryptedKey.isBlank() && masterKey != null) {
                try {
                    byte[] fileKeyBytes = cryptoService.decryptBase64WithHexKey(clientHash, encryptedKey);
                    String fileKeyBase64 = new String(fileKeyBytes, java.nio.charset.StandardCharsets.UTF_8);
                    String recoveryKey = cryptoService.encryptWithHexKey(masterKey,
                            fileKeyBase64.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    user.setRecoveryKey(recoveryKey);
                } catch (Exception ignored) {}
            }

            userStore.create(user);

            return ResponseEntity.ok(Map.of("code", 200, "message", "注册成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", 400, "message", e.getMessage()));
        }
    }
}
