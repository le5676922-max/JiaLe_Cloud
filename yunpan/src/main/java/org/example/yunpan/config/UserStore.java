package org.example.yunpan.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import org.example.yunpan.entity.User;
import org.example.yunpan.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;

@Component
public class UserStore {

    private final UserMapper userMapper;

    @Value("${yunpan.admin.email:}")
    private String adminEmail;

    @Value("${yunpan.admin.password:}")
    private String adminPassword;

    public UserStore(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @PostConstruct
    public void init() {
        if (adminEmail == null || adminEmail.isBlank()
                || adminPassword == null || adminPassword.isBlank()) {
            return;
        }
        if (findByEmail(adminEmail).isEmpty()) {
            User admin = new User();
            admin.setEmail(adminEmail);
            admin.setUsername("admin");
            // 客户端发来 SHA-256(pwd)，服务端再 SHA-256 → 双重哈希
            admin.setPassword(hash(hash(adminPassword)));
            admin.setRole("ADMIN");
            admin.setMembership("VIP");
            admin.setQuota(0L);
            admin.setEnabled(true);
            userMapper.insert(admin);
        }
    }

    public Optional<User> findByEmail(String email) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        return Optional.ofNullable(user);
    }

    public Optional<User> findByUsername(String username) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        return Optional.ofNullable(user);
    }

    public List<User> findAll() {
        return userMapper.selectList(
                new LambdaQueryWrapper<User>().orderByAsc(User::getEmail));
    }

    public User create(User user) {
        if (findByEmail(user.getEmail()).isPresent()) {
            throw new IllegalArgumentException("邮箱已被注册: " + user.getEmail());
        }
        // 密码已经是客户端发来的 SHA-256，再哈希一次存库
        user.setPassword(hash(user.getPassword()));
        if (user.getMembership() == null) {
            user.setMembership("FREE");
        }
        userMapper.insert(user);
        return user;
    }

    public User update(String email, User updates) {
        User existing = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (existing == null) {
            throw new IllegalArgumentException("用户不存在: " + email);
        }
        if (updates.getPassword() != null && !updates.getPassword().isBlank()) {
            // 新密码也是客户端 SHA-256，再哈希一次
            existing.setPassword(hash(updates.getPassword()));
        }
        if (updates.getEncryptedKey() != null) {
            existing.setEncryptedKey(updates.getEncryptedKey());
        }
        if (updates.getRole() != null) existing.setRole(updates.getRole());
        if (updates.getMembership() != null) existing.setMembership(updates.getMembership());
        if (updates.getQuota() != null && updates.getQuota() >= 0) {
            existing.setQuota(updates.getQuota());
        }
        if (updates.getEnabled() != null) existing.setEnabled(updates.getEnabled());
        userMapper.updateById(existing);
        return existing;
    }

    public void delete(String email) {
        userMapper.delete(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
    }

    /**
     * 验证密码：接收客户端 SHA-256(password)，再哈希一次与库中比对
     */
    public boolean verifyPassword(String email, String clientHash) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null) return false;
        return hash(clientHash).equals(user.getPassword());
    }

    public String getEncryptedKey(String email) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        return user != null ? user.getEncryptedKey() : null;
    }

    public static String hash(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
