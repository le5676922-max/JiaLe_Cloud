package org.example.yunpan.config;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存会话管理：同一用户名只保留最新 loginId，旧会话自动失效。
 */
@Component
public class SessionManager {

    private final Map<String, String> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> fileKeys = new ConcurrentHashMap<>();

    /** 创建新会话，返回 loginId，同时踢掉旧会话 */
    public String createSession(String username) {
        String loginId = UUID.randomUUID().toString();
        sessions.put(username, loginId);
        return loginId;
    }

    /** 检查会话是否有效 */
    public boolean isValid(String username, String loginId) {
        String current = sessions.get(username);
        return current != null && current.equals(loginId);
    }

    /** 销毁会话，同时清除文件密钥 */
    public void destroySession(String username) {
        sessions.remove(username);
        fileKeys.remove(username);
    }

    /** 缓存文件加密密钥 */
    public void cacheFileKey(String username, String key) {
        fileKeys.put(username, key);
    }

    /** 获取文件加密密钥 */
    public String getFileKey(String username) {
        return fileKeys.get(username);
    }
}
