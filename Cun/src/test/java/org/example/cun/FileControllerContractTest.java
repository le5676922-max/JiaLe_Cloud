package org.example.cun;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileControllerContractTest {

    @Test
    @SuppressWarnings("unchecked")
    void mergeResponseMatchesFrontendContract() throws IOException {
        FileStorageService storage = new FileStorageService(null, ".", true, 6, "zip", 1024) {
            @Override
            public long saveChunk(String username, String uploadId, int chunkIndex, InputStream in) {
                return 0;
            }

            @Override
            public long mergeChunks(String username, String uploadId, String targetPath,
                                    String fileName, int totalChunks) {
                return 123L;
            }
        };
        FileController controller = new FileController(storage, callback(), auth());

        Map<String, Object> response = controller.mergeChunks("Bearer token", Map.of(
                "uploadId", "u1",
                "filePath", "docs/a.txt",
                "totalChunks", 1
        ));

        assertEquals(200, response.get("code"));
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        assertNotNull(data);
        assertEquals("docs/a.txt", data.get("filePath"));
        assertEquals(123L, data.get("size"));
    }

    @Test
    void uploadResponseStillReportsChunkSize() throws IOException {
        FileStorageService storage = new FileStorageService(null, ".", true, 6, "zip", 1024) {
            @Override
            public long saveChunk(String username, String uploadId, int chunkIndex, InputStream in) {
                return 10L;
            }
        };
        FileController controller = new FileController(storage, callback(), auth());

        Map<String, Object> response = controller.uploadChunk(
                "Bearer token", "u1", 0, new MockMultipartFile("chunk", "abc".getBytes()));

        assertEquals(200, response.get("code"));
        assertEquals(10L, response.get("size"));
    }

    private AuthService auth() {
        return new AuthService("secret", "node-secret") {
            @Override
            public String requireUser(String authHeader) {
                return "user@example.com";
            }
        };
    }

    private NodeCallbackClient callback() {
        return new NodeCallbackClient("127.0.0.1", "node1", "node-secret") {
            @Override
            public void recordFile(String username, String filePath, long size) {
            }
        };
    }
}
