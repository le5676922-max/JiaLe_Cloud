package org.example.yunpan.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;

@Component
public class JwtUtils {

    private final SecretKey secretKey;
    private final long expirationMs;

    /**
     * 密钥和过期时间均从配置注入，绝无硬编码。
     *
     * @param secret        application.yml 中的 yunpan.jwt.secret
     * @param expirationMs  application.yml 中的 yunpan.jwt.expiration-ms
     */
    public JwtUtils(@Value("${yunpan.jwt.secret}") String secret,
                    @Value("${yunpan.jwt.expiration-ms}") long expirationMs) {
        this.secretKey = deriveKey(secret);
        this.expirationMs = expirationMs;
    }

    /**
     * 将用户配置的明文密钥派生为符合 HS256 要求的 256 位 AES 密钥。
     * 使用 SHA-256 哈希确保任意长度输入都能产生 32 字节密钥。
     */
    private SecretKey deriveKey(String rawSecret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawSecret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "HmacSHA256");
        } catch (Exception e) {
            throw new IllegalStateException("JWT 密钥初始化失败", e);
        }
    }

    /**
     * 生成 JWT Token（含 loginId 用于单设备登录）。
     */
    public String generateToken(String username, String loginId) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(username)
                .claim("loginId", loginId)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并校验 Token，成功返回 Claims，失败抛出 JwtException。
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Token 中提取用户名。
     */
    public String getUsername(String token) {
        return parseToken(token).getSubject();
    }

    /**
     * 从 Token 中提取 loginId。
     */
    public String getLoginId(String token) {
        return parseToken(token).get("loginId", String.class);
    }

    /**
     * 校验 Token 是否有效（签名正确且未过期）。
     */
    public boolean validateToken(String token) {
        try { parseToken(token); return true; }
        catch (JwtException e) { return false; }
    }

    /** 生成 5 分钟有效的下载签名 token */
    public String generateDownloadToken(String username, String filePath) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("path", filePath)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 300_000))
                .signWith(secretKey)
                .compact();
    }

    /** 解析下载签名 token */
    public Claims parseDownloadToken(String token) {
        return Jwts.parser().verifyWith(secretKey).build()
                .parseSignedClaims(token).getPayload();
    }
}
