package org.example.yunpan.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StorageRouterTest {
    @Test
    void selectsRequestedOnlineNodeWhenCapacityIsEnough() throws Exception {
        Object router = newRouter(List.of(
                node("phone-a", 1_000, 100),
                node("phone-b", 2_000, 200)
        ));

        Object selected = selectNode(router, 500, "phone-b");

        assertEquals("phone-b", invoke(selected, "getId"));
    }

    @Test
    void rejectsRequestedNodeWhenCapacityIsInsufficient() throws Exception {
        Object router = newRouter(List.of(node("phone-a", 1_000, 900)));

        Exception ex = assertThrows(Exception.class, () -> selectNode(router, 500, "phone-a"));

        assertTrue(ex.getCause() instanceof ResponseStatusException);
    }

    @Test
    void fallsBackToAutomaticSelectionWhenNoNodeRequested() throws Exception {
        Object router = newRouter(List.of(
                node("fuller", 1_000, 800),
                node("emptier", 1_000, 100)
        ));

        Object selected = selectNode(router, 100, "");

        assertEquals("emptier", invoke(selected, "getId"));
    }

    @Test
    void selectsOnlyOwnedNodeWhenOwnerIsProvided() throws Exception {
        Object router = newRouter(List.of(
                node("alice-phone", "alice@example.com", 1_000, 100),
                node("bob-phone", "bob@example.com", 1_000, 10)
        ));

        Object selected = selectOwnedNode(router, 100, "alice@example.com");

        assertEquals("alice-phone", invoke(selected, "getId"));
    }

    @Test
    void returnsNullWhenOwnerHasNoAvailableNode() throws Exception {
        Object router = newRouter(List.of(
                node("bob-phone", "bob@example.com", 1_000, 10)
        ));

        Object selected = selectOwnedNode(router, 100, "alice@example.com");

        assertNull(selected);
    }

    private Object newRouter(List<Object> nodes) throws Exception {
        Class<?> mapperType = Class.forName("org.example.yunpan.mapper.NodeMapper");
        Object mapper = Proxy.newProxyInstance(
                mapperType.getClassLoader(),
                new Class<?>[]{ mapperType },
                (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) return nodes;
                    return null;
                });
        Class<?> registryType = Class.forName("org.example.yunpan.service.NodeRegistry");
        Object registry = registryType.getConstructor(mapperType).newInstance(mapper);
        Class<?> routerType = Class.forName("org.example.yunpan.service.StorageRouter");
        return routerType.getConstructor(registryType).newInstance(registry);
    }

    private Object selectNode(Object router, long fileSize, String requestedNodeId) throws Exception {
        Method method = router.getClass().getMethod("selectNode", long.class, String.class);
        return method.invoke(router, fileSize, requestedNodeId);
    }

    private Object selectOwnedNode(Object router, long fileSize, String owner) throws Exception {
        Method method = router.getClass().getMethod("selectOwnedNode", long.class, String.class);
        return method.invoke(router, fileSize, owner);
    }

    private Object node(String id, long quota, long usedCapacity) throws Exception {
        return node(id, "", quota, usedCapacity);
    }

    private Object node(String id, String owner, long quota, long usedCapacity) throws Exception {
        Class<?> type = Class.forName("org.example.yunpan.entity.Node");
        Object node = type.getConstructor().newInstance();
        invoke(node, "setId", id);
        invoke(node, "setOwner", owner);
        invoke(node, "setStatus", "online");
        invoke(node, "setQuota", quota);
        invoke(node, "setUsedCapacity", usedCapacity);
        return node;
    }

    private Object invoke(Object target, String methodName, Object... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i] instanceof Long ? Long.class : args[i].getClass();
        }
        Method method = target.getClass().getMethod(methodName, types);
        return method.invoke(target, args);
    }
}
