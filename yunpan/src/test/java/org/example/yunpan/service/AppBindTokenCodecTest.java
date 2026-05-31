package org.example.yunpan.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class AppBindTokenCodecTest {

    @Test
    void hashesTicketWithoutStoringPlainTicket() throws Exception {
        Object codec = newCodec();
        String hash = invokeString(codec, "hash", "plain-ticket");

        assertNotEquals("plain-ticket", hash);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]+"));
    }

    @Test
    void extractsTicketFromDeepLinkOrPlainText() throws Exception {
        Object codec = newCodec();

        assertEquals("abc123", invokeString(codec, "extractTicket", "yunpan://bind?ticket=abc123"));
        assertEquals("abc123", invokeString(codec, "extractTicket", "abc123"));
        assertEquals("", invokeString(codec, "extractTicket", "yunpan://bind?missing=abc123"));
    }

    @Test
    void randomTokenIsUrlSafe() throws Exception {
        Object codec = newCodec();
        String token = invokeString(codec, "newToken");

        assertFalse(token.isBlank());
        assertFalse(token.contains("+"));
        assertFalse(token.contains("/"));
        assertFalse(token.contains("="));
    }

    private Object newCodec() throws Exception {
        Class<?> type = Class.forName("org.example.yunpan.service.AppBindTokenCodec");
        return type.getConstructor().newInstance();
    }

    private String invokeString(Object target, String method, String arg) throws Exception {
        Method m = target.getClass().getMethod(method, String.class);
        return (String) m.invoke(target, arg);
    }

    private String invokeString(Object target, String method) throws Exception {
        Method m = target.getClass().getMethod(method);
        return (String) m.invoke(target);
    }
}
