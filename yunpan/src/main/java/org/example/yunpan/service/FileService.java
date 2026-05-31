package org.example.yunpan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.yunpan.config.SessionManager;
import org.example.yunpan.config.UserContext;
import org.example.yunpan.entity.FileLocation;
import org.example.yunpan.entity.Node;
import org.example.yunpan.mapper.FileLocationMapper;
import org.example.yunpan.mapper.NodeMapper;
import org.example.yunpan.model.FileInfoVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import static org.springframework.http.HttpStatus.*;

@Service
public class FileService {

    @Value("${yunpan.storage.root-path}")
    private String storageRootPath;

    @Value("${yunpan.storage.max-capacity:85899345920}")
    private long maxCapacity;

    @Value("${yunpan.node.id:phone1}")
    private String nodeId;

    @Value("${yunpan.membership.free-quota:5368709120}")
    private long freeQuota;

    @Value("${yunpan.membership.vip-quota:32212254720}")
    private long vipQuota;

    private final CryptoService cryptoService;
    private final SessionManager sessionManager;
    private final FileLocationMapper fileLocationMapper;
    private final NodeMapper nodeMapper;
    private final org.example.yunpan.config.UserStore userStore;

    @Value("${yunpan.compression.enabled:true}")
    private boolean compressionEnabled;

    @Value("${yunpan.compression.level:6}")
    private int compressionLevel;

    @Value("${yunpan.compression.skip-extensions:jpg,jpeg,png,gif,webp,mp4,mkv,avi,mov,mp3,aac,zip,rar,7z,gz}")
    private String skipExtensions;

    @Value("${yunpan.compression.min-size:1024}")
    private long compressionMinSize;

    @Value("${yunpan.security.master-key}")
    private String masterKey;

    @Value("${yunpan.node.token}")
    private String nodeToken;

    private static final long MIN_FREE_SPACE = 20L * 1024 * 1024 * 1024;
    private static final int BUFFER_SIZE = 65536; // 64KB

    public FileService(CryptoService cryptoService, SessionManager sessionManager,
                       FileLocationMapper fileLocationMapper, NodeMapper nodeMapper,
                       org.example.yunpan.config.UserStore userStore) {
        this.cryptoService = cryptoService;
        this.sessionManager = sessionManager;
        this.fileLocationMapper = fileLocationMapper;
        this.nodeMapper = nodeMapper;
        this.userStore = userStore;
    }

    // ==================== 路径解析 ====================

    private Path getRoot() throws IOException {
        String email = UserContext.get();
        if (email == null) email = "default";
        Path root = Path.of(storageRootPath, sanitizePath(email));
        if (!Files.exists(root)) Files.createDirectories(root);
        return root.toRealPath();
    }

    private Path getUserRoot(String email) throws IOException {
        Path root = Path.of(storageRootPath, sanitizePath(email));
        if (!Files.exists(root)) Files.createDirectories(root);
        return root.toRealPath();
    }

    public Path resolveWritePath(String relativePath) throws IOException {
        String normalized = (relativePath == null || relativePath.isBlank()) ? "" : relativePath;
        Path root = getRoot();
        Path resolved = root.resolve(normalized).normalize();
        if (!resolved.startsWith(root)) {
            throw new ResponseStatusException(FORBIDDEN, "路径穿越检测");
        }
        return resolved;
    }

    private Path resolveSafePath(String relativePath, boolean mustExist) throws IOException {
        Path root = getRoot();
        String normalized = (relativePath == null || relativePath.isBlank()) ? "" : relativePath;
        Path resolved = root.resolve(normalized).normalize();

        if (!resolved.startsWith(root)) {
            throw new ResponseStatusException(FORBIDDEN, "路径穿越检测");
        }

        if (!mustExist) {
            Path parent = resolved.getParent();
            if (parent != null && Files.exists(parent)) {
                Path realParent = parent.toRealPath();
                if (!realParent.startsWith(root)) {
                    throw new ResponseStatusException(FORBIDDEN, "路径穿越检测");
                }
            }
            return resolved;
        }

        if (!Files.exists(resolved)) {
            throw new ResponseStatusException(NOT_FOUND, "路径不存在: " + normalized);
        }

        Path realPath = resolved.toRealPath();
        if (!realPath.startsWith(root)) {
            throw new ResponseStatusException(FORBIDDEN, "路径穿越检测");
        }
        return realPath;
    }

    // ==================== 文件列表 ====================

    public List<FileInfoVO> listFiles(String relativePath) throws IOException {
        Map<String, FileInfoVO> result = new LinkedHashMap<>();
        Path dir = null;
        try {
            dir = resolveSafePath(relativePath, true);
        } catch (ResponseStatusException e) {
            if (!NOT_FOUND.equals(e.getStatusCode())) throw e;
        }
        if (dir != null) {
            if (!Files.isDirectory(dir)) {
                throw new ResponseStatusException(BAD_REQUEST, "不是目录");
            }
            try (Stream<Path> stream = Files.list(dir)) {
                stream.map(path -> {
                try {
                    String fileName = path.getFileName().toString();
                    boolean isDir = Files.isDirectory(path);
                    long size = isDir ? 0 : Files.size(path);
                    return new FileInfoVO(fileName, isDir, size,
                            Files.getLastModifiedTime(path).toInstant());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                }).forEach(vo -> result.put(vo.getFileName(), vo));
            }
        }
        addRemoteFiles(result, UserContext.get(), relativePath);
        return result.values().stream()
                .sorted(Comparator.comparing(FileInfoVO::getIsDirectory).reversed()
                        .thenComparing(FileInfoVO::getFileName))
                .toList();
    }

    public List<FileInfoVO> listFilesForUser(String email, String relativePath) throws IOException {
        Path root = getUserRoot(email);
        String normalized = (relativePath == null || relativePath.isBlank()) ? "" : relativePath;
        Path dir = root.resolve(normalized).normalize();
        if (!dir.startsWith(root)) throw new ResponseStatusException(FORBIDDEN, "路径穿越");
        if (!Files.isDirectory(dir)) throw new ResponseStatusException(BAD_REQUEST, "不是目录");

        try (Stream<Path> stream = Files.list(dir)) {
            return stream.map(path -> {
                try {
                    String fileName = path.getFileName().toString();
                    boolean isDir = Files.isDirectory(path);
                    long size = isDir ? 0 : Files.size(path);
                    return new FileInfoVO(fileName, isDir, size,
                            Files.getLastModifiedTime(path).toInstant());
                } catch (IOException e) { throw new RuntimeException(e); }
            }).sorted(Comparator.comparing(FileInfoVO::getIsDirectory).reversed()
                    .thenComparing(FileInfoVO::getFileName)).toList();
        }
    }

    // ==================== 容量查询 ====================

    public long calculateUsedCapacity() throws IOException {
        Path root = getRoot();
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try { return Files.size(path); } catch (IOException e) { return 0L; }
                    }).sum();
        }
    }

    /**
     * 获取当前用户的个人配额上限。
     * 0 表示不限（管理员）
     */
    public long getPersonalQuota() {
        return maxCapacity;
    }

    // ==================== 流式上传（带加密） ====================

    public FileInfoVO uploadFile(String relativePath, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "上传文件为空");
        }

        String email = UserContext.get();
        long usedBefore = calculateUsedCapacity();
        long incoming = file.getSize();

        // 获取用户个人配额
        long personalQuota = maxCapacity;
        if (email != null && userStore != null) {
            var userOpt = userStore.findByEmail(email);
            if (userOpt.isPresent() && userOpt.get().getQuota() != null && userOpt.get().getQuota() > 0) {
                personalQuota = userOpt.get().getQuota();
            }
        }

        if (usedBefore + incoming > personalQuota) {
            throw new ResponseStatusException(INSUFFICIENT_STORAGE,
                    String.format("存储空间不足，已用 %.1f GB / 上限 %.1f GB",
                            usedBefore / (1024.0 * 1024.0 * 1024.0),
                            personalQuota / (1024.0 * 1024.0 * 1024.0)));
        }

        long actualFree = Files.getFileStore(getRoot()).getUsableSpace();
        if (actualFree - incoming < MIN_FREE_SPACE) {
            throw new ResponseStatusException(INSUFFICIENT_STORAGE,
                    String.format("手机存储不足，需保留 %.0f GB 空闲空间",
                            MIN_FREE_SPACE / (1024.0 * 1024 * 1024)));
        }

        String safeName = sanitizeFileName(file.getOriginalFilename());
        Path targetPath = resolveSafePath(relativePath, false).resolve(safeName);
        Files.createDirectories(targetPath.getParent());

        // 获取文件加密密钥
        String fileKey = sessionManager.getFileKey(email);

        // 流式压缩+加密写入
        boolean doCompress = shouldCompress(safeName, incoming);
        try (InputStream in = file.getInputStream();
             OutputStream fos = Files.newOutputStream(targetPath,
                     StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                     StandardOpenOption.TRUNCATE_EXISTING)) {

            if (fileKey != null) {
                fos.write(0x01); // 加密标记：下载时检测，无密钥则拒返
                byte[] nonce = new byte[12];
                new SecureRandom().nextBytes(nonce);
                fos.write(nonce);

                Cipher cipher = createCipher(Cipher.ENCRYPT_MODE, hexToBytes(fileKey), nonce);

                try (CipherOutputStream cos = new CipherOutputStream(fos, cipher)) {
                    OutputStream out = cos;
                    java.util.zip.Deflater deflater = null;
                    DeflaterOutputStream dos = null;
                    if (doCompress) {
                        deflater = new java.util.zip.Deflater(compressionLevel);
                        dos = new DeflaterOutputStream(cos, deflater);
                        out = dos;
                    }
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    if (dos != null) dos.finish();
                    if (deflater != null) deflater.end();
                }
                // CipherOutputStream.close() 写入 GCM tag
            } else {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
        }

        Path savedPath = targetPath.toRealPath();
        if (!savedPath.startsWith(getRoot())) {
            try { Files.delete(savedPath); } catch (IOException ignored) {}
            throw new ResponseStatusException(FORBIDDEN, "路径异常，已回滚");
        }

        // 记录 file_locations
        recordFileLocation(email, relativePath.isEmpty() ? safeName : relativePath + "/" + safeName,
                Files.isDirectory(savedPath), Files.size(savedPath));

        return new FileInfoVO(savedPath.getFileName().toString(), false,
                Files.size(savedPath), Files.getLastModifiedTime(savedPath).toInstant());
    }

    // ==================== 流式下载（带解密） ====================

    public ResponseEntity<Resource> downloadFile(String relativePath) throws IOException {
        FileLocation location = findActiveLocation(UserContext.get(), relativePath);
        if (location != null && location.getNodeId() != null
                && !location.getNodeId().equals(nodeId)) {
            return downloadFromNode(location);
        }

        Path filePath = resolveSafePath(relativePath, true);
        if (Files.isDirectory(filePath)) {
            throw new ResponseStatusException(BAD_REQUEST, "不能下载目录");
        }

        String email = UserContext.get();
        String fileKey = sessionManager.getFileKey(email);
        String fileName = filePath.getFileName().toString();
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replace("+", "%20");

        try (InputStream fin = Files.newInputStream(filePath)) {
            int marker = fin.read();
            boolean isEncrypted = (marker == 0x01);

            if (isEncrypted) {
                if (fileKey == null) {
                    // 尝试从 recovery_key 自动恢复密钥
                    var userOpt = userStore.findByEmail(email);
                    if (userOpt.isPresent() && userOpt.get().getRecoveryKey() != null) {
                        try {
                            byte[] fkBytes = cryptoService.decryptBase64WithHexKey(
                                    masterKey,
                                    userOpt.get().getRecoveryKey());
                            String fkBase64 = new String(fkBytes, StandardCharsets.UTF_8);
                            byte[] rawKey = java.util.Base64.getDecoder().decode(fkBase64);
                            fileKey = bytesToHex(rawKey);
                            sessionManager.cacheFileKey(email, fileKey);
                        } catch (Exception ignored) {}
                    }
                    if (fileKey == null) {
                        throw new ResponseStatusException(INTERNAL_SERVER_ERROR,
                                "文件密钥未加载，请重新登录后再试");
                    }
                }
                byte[] nonce = new byte[12];
                fin.read(nonce);

                Cipher cipher = createCipher(Cipher.DECRYPT_MODE, hexToBytes(fileKey), nonce);
                boolean doDecompress = shouldCompress(fileName, Files.size(filePath));

                // 流式解密写入临时文件（避免大文件 OOM）
                Path tempFile = Files.createTempFile("yunpan_dl_", ".tmp");
                try {
                    try (CipherInputStream cis = new CipherInputStream(fin, cipher)) {
                        InputStream source = cis;
                        if (doDecompress) source = new InflaterInputStream(cis);
                        try (OutputStream fos = Files.newOutputStream(tempFile)) {
                            byte[] buf = new byte[65536];
                            int n;
                            while ((n = source.read(buf)) != -1) fos.write(buf, 0, n);
                        }
                    }
                    long plainLen = Files.size(tempFile);
                    FileSystemResource resource = new FileSystemResource(tempFile) {
                        @Override
                        public InputStream getInputStream() throws IOException {
                            return new FileInputStream(getFile()) {
                                @Override public void close() throws IOException {
                                    super.close();
                                    try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
                                }
                            };
                        }
                    };
                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .contentLength(plainLen)
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename*=UTF-8''" + encoded)
                            .body(resource);
                } catch (Exception e) {
                    try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
                    throw e;
                }
            }

            // 明文文件（旧文件或无加密），直接返回
            FileSystemResource resource = new FileSystemResource(filePath);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(resource.contentLength())
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encoded)
                    .body(resource);
        }
    }

    // ==================== 创建文件夹 ====================

    public FileInfoVO createFolder(String relativePath, String folderName) throws IOException {
        String safeName = sanitizeFileName(folderName);
        if (safeName.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "文件夹名不能为空");
        }
        Path parentPath = resolveSafePath(relativePath, true);
        if (!Files.isDirectory(parentPath)) {
            throw new ResponseStatusException(BAD_REQUEST, "不是目录");
        }
        Path newFolder = parentPath.resolve(safeName);
        if (Files.exists(newFolder)) {
            throw new ResponseStatusException(CONFLICT, "文件夹已存在: " + safeName);
        }
        Files.createDirectory(newFolder);
        Path realPath = newFolder.toRealPath();
        if (!realPath.startsWith(getRoot())) {
            try { Files.delete(realPath); } catch (IOException ignored) {}
            throw new ResponseStatusException(FORBIDDEN, "路径异常，已回滚");
        }
        return new FileInfoVO(realPath.getFileName().toString(), true, 0,
                Files.getLastModifiedTime(realPath).toInstant());
    }

    // ==================== 删除（软删除→回收站） ====================

    public void delete(String relativePath) throws IOException {
        Path target = resolveSafePath(relativePath, true);
        if (Files.isDirectory(target)) {
            try (Stream<Path> entries = Files.list(target)) {
                if (entries.findAny().isPresent())
                    throw new ResponseStatusException(BAD_REQUEST, "文件夹不为空");
            }
        }

        String email = UserContext.get();
        // 移动到回收站目录
        Path trashDir = getRoot().resolve(".trash");
        Files.createDirectories(trashDir);
        Path trashTarget = trashDir.resolve(System.currentTimeMillis() + "_" + target.getFileName());
        Files.move(target, trashTarget);

        // 标记 DB 记录
        var existing = fileLocationMapper.selectOne(new LambdaQueryWrapper<FileLocation>()
                .eq(FileLocation::getUsername, email).eq(FileLocation::getFilePath, relativePath));
        if (existing != null) {
            existing.setDeleted(true);
            existing.setDeletedAt(LocalDateTime.now());
            fileLocationMapper.updateById(existing);
        } else {
            recordFileLocation(email, relativePath, false, 0);
            var fl = fileLocationMapper.selectOne(new LambdaQueryWrapper<FileLocation>()
                    .eq(FileLocation::getUsername, email).eq(FileLocation::getFilePath, relativePath));
            if (fl != null) { fl.setDeleted(true); fl.setDeletedAt(LocalDateTime.now()); fileLocationMapper.updateById(fl); }
        }
    }

    public void restore(String relativePath) throws IOException {
        String email = UserContext.get();
        var fl = fileLocationMapper.selectOne(new LambdaQueryWrapper<FileLocation>()
                .eq(FileLocation::getUsername, email).eq(FileLocation::getFilePath, relativePath)
                .eq(FileLocation::getDeleted, true));
        if (fl == null) throw new ResponseStatusException(NOT_FOUND, "回收站中未找到该文件");

        Path trashDir = getRoot().resolve(".trash");
        // 找到回收站中的文件（通过时间戳前缀匹配）
        String fileName = Path.of(relativePath).getFileName().toString();
        Path trashFile = null;
        try (Stream<Path> s = Files.list(trashDir)) {
            trashFile = s.filter(p -> p.getFileName().toString().endsWith("_" + fileName)).findFirst().orElse(null);
        }
        if (trashFile == null) throw new ResponseStatusException(NOT_FOUND, "回收站文件丢失");

        Path originalPath = resolveSafePath(relativePath, false);
        Files.createDirectories(originalPath.getParent());
        Files.move(trashFile, originalPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        fl.setDeleted(false); fl.setDeletedAt(null); fileLocationMapper.updateById(fl);
    }

    public void permanentDelete(String relativePath) throws IOException {
        String email = UserContext.get();
        var fl = fileLocationMapper.selectOne(new LambdaQueryWrapper<FileLocation>()
                .eq(FileLocation::getUsername, email).eq(FileLocation::getFilePath, relativePath));
        if (fl != null) fileLocationMapper.deleteById(fl.getId());

        Path trashDir = getRoot().resolve(".trash");
        String fileName = Path.of(relativePath).getFileName().toString();
        try (Stream<Path> s = Files.list(trashDir)) {
            s.filter(p -> p.getFileName().toString().endsWith("_" + fileName))
             .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        }
    }

    public List<FileInfoVO> listTrash() throws IOException {
        String email = UserContext.get();
        List<FileLocation> trash = fileLocationMapper.selectList(new LambdaQueryWrapper<FileLocation>()
                .eq(FileLocation::getUsername, email).eq(FileLocation::getDeleted, true));
        return trash.stream().map(fl -> new FileInfoVO(
                Path.of(fl.getFilePath()).getFileName().toString(), false,
                fl.getSize(), fl.getDeletedAt() != null ? fl.getDeletedAt().atZone(java.time.ZoneId.systemDefault()).toInstant() : null))
                .toList();
    }

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 3 * * *")
    public void cleanupTrash() {
        var cutoff = LocalDateTime.now().minusDays(7);
        List<FileLocation> expired = fileLocationMapper.selectList(new LambdaQueryWrapper<FileLocation>()
                .eq(FileLocation::getDeleted, true).lt(FileLocation::getDeletedAt, cutoff));
        for (FileLocation fl : expired) {
            try { permanentDelete(fl.getFilePath()); } catch (Exception ignored) {}
        }
    }

    // ==================== 重命名 ====================

    public FileInfoVO rename(String relativePath, String newName) throws IOException {
        String safeName = sanitizeFileName(newName);
        if (safeName.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "文件名不能为空");
        }
        Path source = resolveSafePath(relativePath, true);
        Path target = source.resolveSibling(safeName);
        if (Files.exists(target)) {
            throw new ResponseStatusException(CONFLICT, "目标文件已存在: " + safeName);
        }
        Files.move(source, target);
        Path realPath = target.toRealPath();
        if (!realPath.startsWith(getRoot())) {
            try { Files.move(target, source); } catch (IOException ignored) {}
            throw new ResponseStatusException(FORBIDDEN, "路径异常，已回滚");
        }
        return new FileInfoVO(realPath.getFileName().toString(),
                Files.isDirectory(realPath), Files.isDirectory(realPath) ? 0 : Files.size(realPath),
                Files.getLastModifiedTime(realPath).toInstant());
    }

    // ==================== 文件存在检查 ====================

    public boolean exists(String relativePath) throws IOException {
        try {
            resolveSafePath(relativePath, true);
            return true;
        } catch (ResponseStatusException e) {
            return false;
        }
    }

    // ==================== file_locations ====================

    public void recordRemoteFile(String email, String filePath, String nodeId, long size) {
        recordFileLocation(email, filePath, nodeId, false, size);
    }

    private void recordFileLocation(String email, String filePath,
                                     boolean isDirectory, long size) {
        recordFileLocation(email, filePath, nodeId, isDirectory, size);
    }

    private void recordFileLocation(String email, String filePath, String targetNodeId,
                                    boolean isDirectory, long size) {
        // 删除旧记录
        fileLocationMapper.delete(new LambdaQueryWrapper<FileLocation>()
                .eq(FileLocation::getUsername, email)
                .eq(FileLocation::getFilePath, filePath));

        FileLocation fl = new FileLocation();
        fl.setUsername(email);
        fl.setFilePath(filePath);
        fl.setNodeId(targetNodeId);
        fl.setSize(size);
        fl.setIsDirectory(isDirectory);
        fl.setDeleted(false);
        fileLocationMapper.insert(fl);
    }

    private FileLocation findActiveLocation(String email, String relativePath) {
        if (email == null || relativePath == null || relativePath.isBlank()) return null;
        return fileLocationMapper.selectOne(new LambdaQueryWrapper<FileLocation>()
                .eq(FileLocation::getUsername, email)
                .eq(FileLocation::getFilePath, relativePath)
                .eq(FileLocation::getDeleted, false));
    }

    private void addRemoteFiles(Map<String, FileInfoVO> result, String email, String relativePath) {
        if (email == null) return;
        String prefix = (relativePath == null || relativePath.isBlank()) ? "" : relativePath.strip();
        List<FileLocation> locations = fileLocationMapper.selectList(new LambdaQueryWrapper<FileLocation>()
                .eq(FileLocation::getUsername, email)
                .eq(FileLocation::getDeleted, false));
        for (FileLocation location : locations) {
            if (location.getNodeId() == null || location.getNodeId().equals(nodeId)) continue;
            String path = location.getFilePath();
            if (path == null || path.isBlank()) continue;
            String rest;
            if (prefix.isBlank()) {
                rest = path;
            } else if (path.startsWith(prefix + "/")) {
                rest = path.substring(prefix.length() + 1);
            } else {
                continue;
            }
            if (rest.isBlank()) continue;
            int slash = rest.indexOf('/');
            if (slash >= 0) {
                String dirName = rest.substring(0, slash);
                result.putIfAbsent(dirName,
                        new FileInfoVO(dirName, true, 0, java.time.Instant.now()));
            } else {
                java.time.Instant time = location.getCreatedAt() == null
                        ? java.time.Instant.now()
                        : location.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant();
                result.putIfAbsent(rest, new FileInfoVO(rest, false,
                        location.getSize() == null ? 0L : location.getSize(), time));
            }
        }
    }

    private ResponseEntity<Resource> downloadFromNode(FileLocation location) throws IOException {
        Node node = nodeMapper.selectById(location.getNodeId());
        if (node == null || node.getFrpPort() == null) {
            throw new ResponseStatusException(NOT_FOUND, "存储节点不存在");
        }
        String encodedPath = URLEncoder.encode(location.getFilePath(), StandardCharsets.UTF_8);
        String encodedUser = URLEncoder.encode(location.getUsername(), StandardCharsets.UTF_8);
        URI uri = URI.create("http://127.0.0.1:" + node.getFrpPort()
                + "/api/files/download?username=" + encodedUser + "&path=" + encodedPath);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(0);
        String authToken = node.getNodeToken() == null || node.getNodeToken().isBlank()
                ? nodeToken
                : node.getNodeToken();
        conn.setRequestProperty("X-Node-Token", authToken);
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            conn.disconnect();
            throw new ResponseStatusException(NOT_FOUND, "节点文件不可用");
        }
        String fileName = Path.of(location.getFilePath()).getFileName().toString();
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        InputStream input = conn.getInputStream();
        InputStreamResource resource = new InputStreamResource(input) {
            @Override
            public long contentLength() {
                return location.getSize() == null ? -1 : location.getSize();
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .body(resource);
    }

    // ==================== 工具方法 ====================

    private String sanitizeFileName(String fileName) {
        if (fileName == null) return "";
        return fileName.replace("..", "").replace("/", "").replace("\\", "")
                .replace("\0", "").strip();
    }

    private String sanitizePath(String path) {
        return path.replace("@", "_at_").replace("..", "");
    }

    private Cipher createCipher(int mode, byte[] keyBytes, byte[] nonce) {
        try {
            SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, key, new GCMParameterSpec(128, nonce));
            return cipher;
        } catch (Exception e) {
            throw new RuntimeException("AES 加解密初始化失败", e);
        }
    }

    private Set<String> skipExtSet = null;

    private boolean shouldCompress(String fileName, long fileSize) {
        if (!compressionEnabled) return false;
        if (fileSize < compressionMinSize) return false;
        if (skipExtSet == null) {
            skipExtSet = new HashSet<>();
            for (String ext : skipExtensions.split(",")) {
                skipExtSet.add("." + ext.trim().toLowerCase());
            }
        }
        String lower = fileName.toLowerCase();
        for (String ext : skipExtSet) {
            if (lower.endsWith(ext)) return false;
        }
        return true;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return bytes;
    }
}
