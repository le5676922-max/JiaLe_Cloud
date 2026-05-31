package org.example.yunpan.config;

/**
 * 线程级当前用户上下文，AuthInterceptor 设置，FileService 读取。
 */
public class UserContext {
    private static final ThreadLocal<String> currentUser = new ThreadLocal<>();

    public static void set(String username) { currentUser.set(username); }
    public static String get() { return currentUser.get(); }
    public static void clear() { currentUser.remove(); }
}
