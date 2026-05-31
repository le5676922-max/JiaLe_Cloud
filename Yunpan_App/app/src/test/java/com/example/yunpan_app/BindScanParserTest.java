package com.example.yunpan_app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BindScanParserTest {
    @Test
    public void parseScannedBindPayloadAcceptsYunpanBindDeepLink() {
        String payload = "  yunpan://bind?ticket=abc123  ";

        String parsed = BindScanParser.parseScannedBindPayload(payload);

        assertEquals("yunpan://bind?ticket=abc123", parsed);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseScannedBindPayloadRejectsNonBindQrCode() {
        BindScanParser.parseScannedBindPayload("https://example.com/not-bind");
    }
}
