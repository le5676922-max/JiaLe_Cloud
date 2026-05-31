package org.example.cun;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class AuthService {

    private final SecretKey jwtKey;
    private final String nodeToken;

    public AuthService(@Value("${yunpan.jwt.secret}") String jwtSecret,
                       @Value("${yunpan.node.token}") String nodeToken) {
        this.jwtKey = deriveKey(jwtSecret);
        this.nodeToken = nodeToken == null ? "" : nodeToken.trim();
    }

    public String requireUser(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少用户认证");
        }
        try {
            Claims claims = Jwts.parser().verifyWith(jwtKey).build()
                    .parseSignedClaims(authHeader.substring(7)).getPayload();
            return claims.getSubject();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户认证失败");
        }
    }

    public boolean isNode(String token) {
        if (token == null || token.isBlank() || nodeToken.isBlank()) return false;
        return MessageDigest.isEqual(nodeToken.getBytes(StandardCharsets.UTF_8),
                token.trim().getBytes(StandardCharsets.UTF_8));
    }

    public void requireNode(String token) {
        if (!isNode(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "节点认证失败");
        }
    }

    private SecretKey deriveKey(String rawSecret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawSecret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "HmacSHA256");
        } catch (Exception e) {
            throw new IllegalStateException("JWT 密钥初始化失败", e);
        }
    }
}
