package com.example.yunpan_app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class StreamUtils {
    private StreamUtils() {
    }

    static byte[] readAllBytesCompat(InputStream input) throws IOException {
        if (input == null) {
            return new byte[0];
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    static byte[] readExactBytes(InputStream input, int length) throws IOException {
        if (length <= 0) {
            return new byte[0];
        }
        byte[] body = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(body, offset, length - offset);
            if (read == -1) {
                throw new IOException("unexpected end of stream");
            }
            offset += read;
        }
        return body;
    }

    static String readUtf8(InputStream input) throws IOException {
        return new String(readAllBytesCompat(input), StandardCharsets.UTF_8);
    }
}
