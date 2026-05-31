package com.example.yunpan_app;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class StreamUtilsTest {
    @Test
    public void readUtf8ReadsFullStreamWithoutJavaNineApi() throws Exception {
        byte[] bytes = "hello yunpan".getBytes(StandardCharsets.UTF_8);

        String body = StreamUtils.readUtf8(new ByteArrayInputStream(bytes));

        assertEquals("hello yunpan", body);
    }

    @Test
    public void readExactBytesReadsRequestedLengthWithoutJavaNineApi() throws Exception {
        byte[] bytes = "abcdef".getBytes(StandardCharsets.UTF_8);

        byte[] body = StreamUtils.readExactBytes(new ByteArrayInputStream(bytes), 4);

        assertEquals("abcd", new String(body, StandardCharsets.UTF_8));
    }
}
