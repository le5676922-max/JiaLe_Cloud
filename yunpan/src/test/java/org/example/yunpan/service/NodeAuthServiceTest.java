package org.example.yunpan.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NodeAuthServiceTest {

    @Test
    void acceptsMatchingNodeToken() throws Exception {
        Object auth = newNodeAuthService("node-secret");

        assertTrue(isValid(auth, "node-secret"));
    }

    @Test
    void rejectsBlankOrDifferentNodeToken() throws Exception {
        Object auth = newNodeAuthService("node-secret");

        assertFalse(isValid(auth, null));
        assertFalse(isValid(auth, ""));
        assertFalse(isValid(auth, "other-secret"));
    }

    private Object newNodeAuthService(String token) throws Exception {
        Class<?> type = Class.forName("org.example.yunpan.service.NodeAuthService");
        return type.getConstructor(String.class).newInstance(token);
    }

    private boolean isValid(Object service, String token) throws Exception {
        return (Boolean) service.getClass().getMethod("isValid", String.class).invoke(service, token);
    }
}
