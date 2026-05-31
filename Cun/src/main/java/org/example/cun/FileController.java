package org.example.cun;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api")
public class FileController {
    private final FileStorageService storage;
    private final NodeCallbackClient callbackClient;
    private final AuthService authService;

    public FileController(FileStorageService storage, NodeCallbackClient callbackClient,
                          AuthService authService) {
        this.storage = storage;
        this.callbackClient = callbackClient;
        this.authService = authService;
    }

    @PostMapping("/upload/chunk")
    public Map<String,Object> uploadChunk(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam String uploadId, @RequestParam int chunkIndex,
            @RequestParam("chunk") MultipartFile chunk) throws IOException {
        String username = authService.requireUser(authHeader);
        long size = storage.saveChunk(username, uploadId, chunkIndex, chunk.getInputStream());
        return Map.of("code",200,"message","ok","size",size);
    }

    @PostMapping("/upload/merge")
    public Map<String,Object> mergeChunks(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String,Object> body) throws IOException {
        String username = authService.requireUser(authHeader);
        String uid = (String)body.get("uploadId"), path = (String)body.get("filePath");
        int total = (Integer)body.get("totalChunks");
        String fn = path.contains("/") ? path.substring(path.lastIndexOf('/')+1) : path;
        long size = storage.mergeChunks(username, uid, path, fn, total);
        callbackClient.recordFile(username, path, size);
        return Map.of("code",200,"message","ok","data",Map.of("filePath", path, "size", size));
    }

    @GetMapping("/files/download")
    public ResponseEntity<org.springframework.core.io.Resource> download(
            @RequestHeader(value = "X-Node-Token", required = false) String nodeToken,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(required = false) String username,
            @RequestParam String path) throws IOException {
        if (authService.isNode(nodeToken)) {
            if (username == null || username.isBlank()) {
                return ResponseEntity.badRequest().build();
            }
        } else {
            username = authService.requireUser(authHeader);
        }
        Path file = storage.materializeDownload(username, path);
        String fn = path.contains("/") ? path.substring(path.lastIndexOf('/')+1) : path;
        boolean temp = file.getFileName().toString().startsWith("cun_dl_");
        FileSystemResource resource = new FileSystemResource(file) {
            @Override
            public InputStream getInputStream() throws IOException {
                return new FileInputStream(getFile()) {
                    @Override public void close() throws IOException {
                        super.close();
                        if (temp) Files.deleteIfExists(file);
                    }
                };
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(Files.size(file))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + URLEncoder.encode(fn, StandardCharsets.UTF_8).replace("+","%20"))
                .body(resource);
    }

    @GetMapping("/files/list")
    public List<Map<String,Object>> listFiles(
            @RequestHeader(value = "Authorization", required = false) String authHeader) throws IOException {
        return storage.listFiles(authService.requireUser(authHeader));
    }

    @GetMapping("/health")
    public Map<String,String> health() { return Map.of("status","ok"); }
}
