package org.example.yunpan.service;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AppBindTokenCodec {
    private final SecureRandom random = new SecureRandom();

    public String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String ticket) {
        if (ticket == null || ticket.isBlank()) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(ticket.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("绑定票据哈希失败", e);
        }
    }

    public String extractTicket(String value) {
        if (value == null) return "";
        String text = value.trim();
        if (text.startsWith("yunpan://")) {
            try {
                String query = URI.create(text).getRawQuery();
                if (query == null) return "";
                for (String pair : query.split("&")) {
                    int eq = pair.indexOf('=');
                    if (eq <= 0) continue;
                    String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                    if ("ticket".equals(key)) {
                        return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8).trim();
                    }
                }
                return "";
            } catch (Exception e) {
                return "";
            }
        }
        return text;
    }
}
