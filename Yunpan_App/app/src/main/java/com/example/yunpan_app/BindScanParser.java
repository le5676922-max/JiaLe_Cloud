package com.example.yunpan_app;

public final class BindScanParser {
    private static final String BIND_DEEP_LINK_PREFIX = "yunpan://bind";

    private BindScanParser() {
    }

    public static String parseScannedBindPayload(String payload) {
        String value = payload == null ? "" : payload.trim();
        if (!value.startsWith(BIND_DEEP_LINK_PREFIX)) {
            throw new IllegalArgumentException("不是有效的节点绑定二维码");
        }
        return value;
    }
}
