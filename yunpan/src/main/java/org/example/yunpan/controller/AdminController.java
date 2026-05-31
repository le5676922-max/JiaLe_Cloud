package org.example.yunpan.controller;

import org.example.yunpan.config.UserStore;
import org.example.yunpan.model.FileInfoVO;
import org.example.yunpan.entity.User;
import org.example.yunpan.service.FileService;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserStore userStore;
    private final FileService fileService;
    private final org.example.yunpan.config.SessionManager sessionManager;
    private final org.example.yunpan.service.CryptoService cryptoService;

    @Value("${yunpan.security.master-key}")
    private String masterKey;

    public AdminController(UserStore userStore, FileService fileService,
                           org.example.yunpan.config.SessionManager sessionManager,
                           org.example.yunpan.service.CryptoService cryptoService) {
        this.userStore = userStore;
        this.fileService = fileService;
        this.sessionManager = sessionManager;
        this.cryptoService = cryptoService;
    }

    /** 系统统计 */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        List<User> all = userStore.findAll();
        long totalUsers = all.size();
        long enabledUsers = all.stream().filter(User::getEnabled).count();
        long vipUsers = all.stream().filter(u -> "VIP".equals(u.getMembership())).count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalUsers", totalUsers);
        result.put("enabledUsers", enabledUsers);
        result.put("vipUsers", vipUsers);
        return ResponseEntity.ok(result);
    }

    /** 用户列表 */
    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> listUsers() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : userStore.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("email", u.getEmail());
            m.put("username", u.getUsername());
            m.put("role", u.getRole());
            m.put("membership", u.getMembership());
            m.put("quota", u.getQuota());
            m.put("enabled", u.getEnabled());
            m.put("createTime", u.getCreateTime());
            result.add(m);
        }
        return ResponseEntity.ok(result);
    }

    /** 创建用户 */
    @PostMapping("/users")
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody Map<String, Object> body) {
        String email = (String) body.get("email");
        String password = (String) body.get("password");
        String role = (String) body.getOrDefault("role", "USER");
        String membership = (String) body.getOrDefault("membership", "FREE");
        long quota = body.get("quota") instanceof Number
                ? ((Number) body.get("quota")).longValue() : 5L * 1024 * 1024 * 1024;

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", 400, "message", "邮箱和密码不能为空"));
        }

        try {
            User user = new User();
            user.setEmail(email.trim());
            // 管理员创建用户时，直接用原始密码哈希（不走客户端 SHA-256）
            // 用户首次登录时需要重置密码
            user.setPassword(UserStore.hash(UserStore.hash(password)));
            user.setRole(role);
            user.setMembership(membership);
            user.setQuota(quota);
            user.setEnabled(true);
            userStore.create(user);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("email", email);
            result.put("role", role);
            result.put("membership", membership);
            result.put("quota", quota);
            return ResponseEntity.ok(Map.of("code", 200, "data", result, "message", "用户创建成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", 400, "message", e.getMessage()));
        }
    }

    /** 更新用户 */
    @PutMapping("/users/{email}")
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable String email,
            @RequestBody Map<String, Object> body) {
        try {
            User updates = new User();
            if (body.containsKey("password")) {
                String pwd = (String) body.get("password");
                if (pwd != null && !pwd.isBlank()) {
                    updates.setPassword(pwd);
                }
            }
            if (body.containsKey("role")) updates.setRole((String) body.get("role"));
            if (body.containsKey("membership")) updates.setMembership((String) body.get("membership"));
            if (body.containsKey("quota")) updates.setQuota(((Number) body.get("quota")).longValue());
            if (body.containsKey("enabled")) updates.setEnabled((Boolean) body.get("enabled"));

            userStore.update(email, updates);
            return ResponseEntity.ok(Map.of("code", 200, "message", "更新成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", 400, "message", e.getMessage()));
        }
    }

    /** 重置用户密码（使用 recovery_key 恢复文件密钥，文件不丢失） */
    @PostMapping("/users/{email}/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@PathVariable String email,
                                                              @RequestBody Map<String, String> body) {
        String newPassword = body.get("password");
        if (newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", 400, "message", "新密码至少6位"));
        }

        var userOpt = userStore.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", 400, "message", "用户不存在"));
        }

        var user = userOpt.get();
        String recoveryKey = user.getRecoveryKey();
        if (recoveryKey == null || recoveryKey.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", 400, "message", "该用户没有恢复密钥，重置后文件将无法解密"));
        }

        try {
            // 用主密钥从 recovery_key 中恢复文件密钥
            byte[] fileKeyBytes = cryptoService.decryptBase64WithHexKey(masterKey, recoveryKey);
            String fileKeyBase64 = new String(fileKeyBytes, java.nio.charset.StandardCharsets.UTF_8);

            // 用新密码加密文件密钥
            String clientHash = UserStore.hash(newPassword); // 模拟客户端 SHA-256
            String newEncryptedKey = cryptoService.encryptWithHexKey(clientHash,
                    fileKeyBase64.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // 更新用户密码和加密密钥
            User updates = new User();
            updates.setPassword(clientHash);
            updates.setEncryptedKey(newEncryptedKey);
            userStore.update(email, updates);

            // 踢掉旧会话
            sessionManager.destroySession(email);

            return ResponseEntity.ok(Map.of("code", 200, "message",
                    "密码已重置为: " + newPassword + "，请通知用户重新登录"));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("code", 500, "message", "重置失败: " + e.getMessage()));
        }
    }

    /** 删除用户，同时踢下线 */
    @DeleteMapping("/users/{email}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable String email) {
        userStore.delete(email);
        sessionManager.destroySession(email);
        return ResponseEntity.ok(Map.of("code", 200, "message", "用户已删除，已强制下线"));
    }

    /** 管理员查看任意用户的文件 */
    @GetMapping("/files")
    public ResponseEntity<List<FileInfoVO>> adminListFiles(
            @RequestParam String user,
            @RequestParam(defaultValue = "") String path) throws Exception {
        return ResponseEntity.ok(fileService.listFilesForUser(user, path));
    }
}
