package org.example.yunpan.controller;

import org.example.yunpan.model.FileInfoVO;
import org.example.yunpan.service.ChunkUploadService;
import org.example.yunpan.service.FileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FileController {

    private final FileService fileService;
    private final ChunkUploadService chunkUploadService;
    private final org.example.yunpan.config.UserStore userStore;
    private final org.example.yunpan.service.StorageRouter storageRouter;
    private final org.example.yunpan.mapper.ShareLinkMapper shareLinkMapper;
    private final org.example.yunpan.config.SessionManager sessionManager;
    private final org.example.yunpan.service.CryptoService cryptoService;
    private final org.example.yunpan.util.JwtUtils jwtUtils;
    private final long maxCapacity;

    @Value("${yunpan.upload.chunk-size:10485760}")
    private long chunkSize;

    @Value("${yunpan.cloud.host:localhost:8080}")
    private String cloudHost;

    @Value("${yunpan.security.master-key}")
    private String masterKey;

    public FileController(FileService fileService,
                          ChunkUploadService chunkUploadService,
                          org.example.yunpan.config.UserStore userStore,
                          org.example.yunpan.service.StorageRouter storageRouter,
                          org.example.yunpan.mapper.ShareLinkMapper shareLinkMapper,
                          org.example.yunpan.config.SessionManager sessionManager,
                          org.example.yunpan.service.CryptoService cryptoService,
                          org.example.yunpan.util.JwtUtils jwtUtils,
                          @Value("${yunpan.storage.max-capacity:85899345920}") long maxCapacity) {
        this.fileService = fileService;
        this.chunkUploadService = chunkUploadService;
        this.userStore = userStore;
        this.storageRouter = storageRouter;
        this.shareLinkMapper = shareLinkMapper;
        this.sessionManager = sessionManager;
        this.cryptoService = cryptoService;
        this.jwtUtils = jwtUtils;
        this.maxCapacity = maxCapacity;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // ==================== 文件列表 ====================

    @GetMapping("/files/list")
    public ResponseEntity<List<FileInfoVO>> listFiles(@RequestParam(defaultValue = "") String path) throws IOException {
        return ResponseEntity.ok(fileService.listFiles(path));
    }

    // ==================== 下载签名（防盗链） ====================

    @PostMapping("/files/download-token")
    public ResponseEntity<Map<String, Object>> getDownloadToken(@RequestBody Map<String, String> body) {
        String path = body.get("path");
        if (path == null || path.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "path 不能为空"));
        }
        String email = org.example.yunpan.config.UserContext.get();
        // 生成 5 分钟有效的下载签名 token
        String dlToken = jwtUtils.generateDownloadToken(email, path);
        return ResponseEntity.ok(Map.of("code", 200, "data", Map.of("token", dlToken,
                "url", "/api/files/dl/" + dlToken)));
    }

    @GetMapping("/files/dl/{token}")
    public ResponseEntity<org.springframework.core.io.Resource> signedDownload(@PathVariable String token) throws IOException {
        var claims = jwtUtils.parseDownloadToken(token);
        String email = claims.getSubject();
        String path = claims.get("path", String.class);
        if (path == null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "无效的下载令牌");
        // 尝试恢复密钥
        String fileKey = sessionManager.getFileKey(email);
        if (fileKey == null) {
            var userOpt = userStore.findByEmail(email);
            if (userOpt.isPresent() && userOpt.get().getRecoveryKey() != null) {
                try {
                    byte[] fkBytes = cryptoService.decryptBase64WithHexKey(
                            masterKey,
                            userOpt.get().getRecoveryKey());
                    String fkBase64 = new String(fkBytes, java.nio.charset.StandardCharsets.UTF_8);
                    byte[] rawKey = java.util.Base64.getDecoder().decode(fkBase64);
                    fileKey = bytesToHex(rawKey);
                    sessionManager.cacheFileKey(email, fileKey);
                } catch (Exception ignored) {}
            }
        }
        org.example.yunpan.config.UserContext.set(email);
        try { return fileService.downloadFile(path); }
        finally { org.example.yunpan.config.UserContext.clear(); }
    }

    // ==================== 流式上传（小文件直传） ====================

    @PostMapping("/files/upload")
    public ResponseEntity<FileInfoVO> uploadFile(
            @RequestParam(defaultValue = "") String path,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(fileService.uploadFile(path, file));
    }

    // ==================== 流式下载 ====================

    @GetMapping("/files/download")
    public ResponseEntity<Resource> downloadFile(@RequestParam String path) throws IOException {
        return fileService.downloadFile(path);
    }

    // ==================== 文件夹操作 ====================

    @PostMapping("/files/folder")
    public ResponseEntity<FileInfoVO> createFolder(
            @RequestParam(defaultValue = "") String path,
            @RequestParam String name) throws IOException {
        return ResponseEntity.ok(fileService.createFolder(path, name));
    }

    @DeleteMapping("/files")
    public ResponseEntity<Map<String, String>> delete(@RequestParam String path) throws IOException {
        fileService.delete(path);
        return ResponseEntity.ok(Map.of("message", "已移入回收站"));
    }

    @GetMapping("/files/trash")
    public ResponseEntity<List<FileInfoVO>> listTrash() throws IOException {
        return ResponseEntity.ok(fileService.listTrash());
    }

    @PostMapping("/files/restore")
    public ResponseEntity<Map<String, String>> restore(@RequestParam String path) throws IOException {
        fileService.restore(path);
        return ResponseEntity.ok(Map.of("message", "已恢复"));
    }

    @DeleteMapping("/files/permanent")
    public ResponseEntity<Map<String, String>> permanentDelete(@RequestParam String path) throws IOException {
        fileService.permanentDelete(path);
        return ResponseEntity.ok(Map.of("message", "已永久删除"));
    }

    @PutMapping("/files/rename")
    public ResponseEntity<FileInfoVO> rename(
            @RequestParam String path,
            @RequestParam String name) throws IOException {
        return ResponseEntity.ok(fileService.rename(path, name));
    }

    // ==================== 容量 ====================

    @GetMapping("/files/capacity")
    public ResponseEntity<Map<String, Object>> capacity() throws IOException {
        long used = fileService.calculateUsedCapacity();

        // 按用户会员等级返回个人配额
        String email = org.example.yunpan.config.UserContext.get();
        long personalQuota = maxCapacity; // 默认全局上限
        if (email != null) {
            var user = userStore.findByEmail(email);
            if (user.isPresent() && user.get().getQuota() != null && user.get().getQuota() > 0) {
                personalQuota = user.get().getQuota();
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCapacity", personalQuota);
        result.put("usedCapacity", used);
        result.put("usagePercentage", personalQuota > 0
                ? Math.round(used * 10000.0 / personalQuota) / 100.0 : 0);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/files/exists")
    public ResponseEntity<Map<String, Boolean>> exists(@RequestParam String path) throws IOException {
        return ResponseEntity.ok(Map.of("exists", fileService.exists(path)));
    }

    // ==================== 分片上传 ====================

    /**
     * 初始化分片上传
     */
    @PostMapping("/upload/init")
    public ResponseEntity<Map<String, Object>> initUpload(@RequestBody Map<String, Object> body) {
        String filePath = (String) body.get("filePath");
        long fileSize = body.get("fileSize") instanceof Number
                ? ((Number) body.get("fileSize")).longValue() : 0;
        int totalChunks = body.get("totalChunks") instanceof Number
                ? ((Number) body.get("totalChunks")).intValue() : 0;
        String requestedNodeId = body.get("nodeId") == null ? "" : body.get("nodeId").toString().trim();

        if (filePath == null || filePath.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", 400, "message", "filePath 不能为空"));
        }
        if (!requestedNodeId.isBlank() && !isCurrentUserAdmin()) {
            return ResponseEntity.status(403)
                    .body(Map.of("code", 403, "message", "仅管理员可以指定存储节点"));
        }

        String uploadId = chunkUploadService.initUpload(filePath, fileSize);

        // 选存储节点：管理员指定节点时严格校验；自动模式只优先当前用户自己的节点，找不到则回落服务器云盘。
        var node = requestedNodeId.isBlank()
                ? storageRouter.selectOwnedNode(fileSize, org.example.yunpan.config.UserContext.get())
                : storageRouter.selectNode(fileSize, requestedNodeId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uploadId", uploadId);
        result.put("chunkSize", chunkSize);
        result.put("totalChunks", totalChunks);
        if (node != null) {
            result.put("nodeId", node.getId());
            result.put("nodePort", node.getFrpPort());
        }
        return ResponseEntity.ok(Map.of("code", 200, "data", result));
    }

    private boolean isCurrentUserAdmin() {
        String email = org.example.yunpan.config.UserContext.get();
        return email != null
                && userStore.findByEmail(email)
                .map(user -> "ADMIN".equals(user.getRole()))
                .orElse(false);
    }

    /**
     * 上传一个分片
     */
    @PostMapping("/upload/chunk")
    public ResponseEntity<Map<String, Object>> uploadChunk(
            @RequestParam String uploadId,
            @RequestParam int chunkIndex,
            @RequestParam String filePath,
            @RequestParam("chunk") MultipartFile chunk) throws IOException {

        chunkUploadService.saveChunk(uploadId, chunkIndex, filePath,
                chunk.getInputStream(), chunk.getSize());

        return ResponseEntity.ok(Map.of("code", 200, "message", "ok"));
    }

    /**
     * 合并分片
     */
    @PostMapping("/upload/merge")
    public ResponseEntity<Map<String, Object>> mergeChunks(@RequestBody Map<String, Object> body) throws IOException {
        String uploadId = (String) body.get("uploadId");
        String filePath = (String) body.get("filePath");
        int totalChunks = body.get("totalChunks") instanceof Number
                ? ((Number) body.get("totalChunks")).intValue() : 0;
        long expectedSize = body.get("expectedSize") instanceof Number
                ? ((Number) body.get("expectedSize")).longValue() : 0;

        if (uploadId == null || filePath == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", 400, "message", "参数不完整"));
        }

        long finalSize = chunkUploadService.mergeChunks(uploadId, filePath, totalChunks, expectedSize);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("filePath", filePath);
        result.put("size", finalSize);
        return ResponseEntity.ok(Map.of("code", 200, "data", result, "message", "上传完成"));
    }

    /**
     * 查询已上传分片（断点续传）
     */
    // ==================== 分享链接 ====================

    @PostMapping("/files/share")
    public ResponseEntity<Map<String, Object>> createShare(@RequestBody Map<String, Object> body) {
        String filePath = (String) body.get("path");
        int expireHours = body.get("expireHours") instanceof Number ? ((Number) body.get("expireHours")).intValue() : 24;
        String email = org.example.yunpan.config.UserContext.get();
        String token = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        org.example.yunpan.entity.ShareLink sl = new org.example.yunpan.entity.ShareLink();
        sl.setFilePath(filePath);
        sl.setUsername(email);
        sl.setToken(token);
        sl.setExpiresAt(java.time.LocalDateTime.now().plusHours(expireHours));
        shareLinkMapper.insert(sl);

        return ResponseEntity.ok(Map.of("code", 200, "data", Map.of("token", token, "url",
                "http://" + cloudHost + "/api/share/" + token)));
    }

    @GetMapping("/share/{token}")
    public ResponseEntity<org.springframework.core.io.Resource> downloadShare(@PathVariable String token) throws IOException {
        var sl = shareLinkMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<org.example.yunpan.entity.ShareLink>()
                .eq(org.example.yunpan.entity.ShareLink::getToken, token));
        if (sl == null || sl.getExpiresAt().isBefore(java.time.LocalDateTime.now()))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "链接不存在或已过期");
        // 临时设置用户上下文
        org.example.yunpan.config.UserContext.set(sl.getUsername());
        try {
            // 尝试从缓存获取密钥，没有则从 recovery_key 恢复
            String fileKey = sessionManager.getFileKey(sl.getUsername());
            if (fileKey == null) {
                var userOpt = userStore.findByEmail(sl.getUsername());
                if (userOpt.isPresent() && userOpt.get().getRecoveryKey() != null) {
                    String mKey = masterKey;
                    try {
                        byte[] fkBytes = cryptoService.decryptBase64WithHexKey(mKey, userOpt.get().getRecoveryKey());
                        String fkBase64 = new String(fkBytes, java.nio.charset.StandardCharsets.UTF_8);
                        byte[] rawKey = java.util.Base64.getDecoder().decode(fkBase64);
                        fileKey = bytesToHex(rawKey);
                        sessionManager.cacheFileKey(sl.getUsername(), fileKey);
                    } catch (Exception ignored) {}
                }
            }
            return fileService.downloadFile(sl.getFilePath());
        } finally {
            org.example.yunpan.config.UserContext.clear();
        }
    }

    @GetMapping("/upload/resume")
    public ResponseEntity<Map<String, Object>> resumeUpload(@RequestParam String filePath) {
        List<Integer> uploaded = chunkUploadService.getUploadedChunks(filePath);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uploadedChunks", uploaded);
        return ResponseEntity.ok(Map.of("code", 200, "data", result));
    }
}
