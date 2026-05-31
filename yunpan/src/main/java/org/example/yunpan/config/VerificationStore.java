package org.example.yunpan.config;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存验证码存储：5分钟有效，60秒内同邮箱不可重发。
 */
@Component
public class VerificationStore {

    private static final long TTL_MS = 5 * 60 * 1000;      // 5分钟
    private static final long RESEND_MS = 60 * 1000;        // 60秒

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    private record Entry(String code, long expireAt, long sentAt) {}

    public void store(String email, String code) {
        long now = System.currentTimeMillis();
        store.put(email, new Entry(code, now + TTL_MS, now));
    }

    public boolean verify(String email, String code) {
        Entry e = store.get(email);
        if (e == null) return false;
        if (System.currentTimeMillis() > e.expireAt) {
            store.remove(email);
            return false;
        }
        if (e.code.equals(code)) {
            store.remove(email); // 一次性使用
            return true;
        }
        return false;
    }

    public boolean canResend(String email) {
        Entry e = store.get(email);
        if (e == null) return true;
        return System.currentTimeMillis() - e.sentAt > RESEND_MS;
    }
}
