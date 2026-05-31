package org.example.yunpan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.yunpan.config.SessionManager;
import org.example.yunpan.config.UserContext;
import org.example.yunpan.entity.UploadChunk;
import org.example.yunpan.mapper.UploadChunkMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.*;

@Service
public class ChunkUploadService {

    private final UploadChunkMapper uploadChunkMapper;
    private final CryptoService cryptoService;
    private final SessionManager sessionManager;
    private final FileService fileService;

    @Value("${yunpan.upload.chunk-ttl-hours:24}")
    private int chunkTtlHours;

    @Value("${yunpan.storage.root-path}")
    private String storageRootPath;

    public ChunkUploadService(UploadChunkMapper uploadChunkMapper,
                              CryptoService cryptoService,
                              SessionManager sessionManager,
                              FileService fileService) {
        this.uploadChunkMapper = uploadChunkMapper;
        this.cryptoService = cryptoService;
        this.sessionManager = sessionManager;
        this.fileService = fileService;
    }

    /**
     * 初始化上传，返回 uploadId
     */
    public String initUpload(String filePath, long totalSize) {
        String username = UserContext.get();
        String uploadId = UUID.randomUUID().toString().replace("-", "");

        // 清理该路径的旧上传记录
        LambdaQueryWrapper<UploadChunk> del = new LambdaQueryWrapper<>();
        del.eq(UploadChunk::getUsername, username)
           .eq(UploadChunk::getFilePath, filePath);
        uploadChunkMapper.delete(del);

        return uploadId;
    }

    /**
     * 接收并加密一个分片
     */
    public void saveChunk(String uploadId, int chunkIndex, String filePath,
                          InputStream chunkStream, long chunkSize) throws IOException {
        String username = UserContext.get();

        // 检查是否已存在
        if (uploadChunkMapper.selectOne(
                new LambdaQueryWrapper<UploadChunk>()
                    .eq(UploadChunk::getUploadId, uploadId)
                    .eq(UploadChunk::getChunkIndex, chunkIndex)) != null) {
            return; // 已上传，幂等
        }

        // 读分片到内存，加密
        byte[] plaintext = chunkStream.readAllBytes();

        String fileKey = sessionManager.getFileKey(username);
        if (fileKey == null) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "文件密钥未缓存，请重新登录");
        }
        byte[] encrypted = cryptoService.encryptBytesWithHexKey(fileKey, plaintext);

        // 写入临时文件
        Path tempDir = getTempDir(username, uploadId);
        Files.createDirectories(tempDir);
        Path chunkFile = tempDir.resolve("chunk_" + chunkIndex);
        Files.write(chunkFile, encrypted);

        // 记录到数据库
        UploadChunk record = new UploadChunk();
        record.setUsername(username);
        record.setFilePath(filePath);
        record.setUploadId(uploadId);
        record.setChunkIndex(chunkIndex);
        record.setChunkSize((long) encrypted.length); // 记录加密后大小
        uploadChunkMapper.insert(record);
    }

    /**
     * 查询已上传的分片索引列表（断点续传用）
     */
    public List<Integer> getUploadedChunks(String filePath) {
        String username = UserContext.get();
        List<UploadChunk> chunks = uploadChunkMapper.selectList(
                new LambdaQueryWrapper<UploadChunk>()
                    .eq(UploadChunk::getUsername, username)
                    .eq(UploadChunk::getFilePath, filePath)
                    .orderByAsc(UploadChunk::getChunkIndex));

        return chunks.stream().map(UploadChunk::getChunkIndex).toList();
    }

    /**
     * 合并所有分片到最终文件
     */
    public long mergeChunks(String uploadId, String filePath, int totalChunks,
                            long expectedSize) throws IOException {
        String username = UserContext.get();

        List<UploadChunk> records = uploadChunkMapper.selectList(
                new LambdaQueryWrapper<UploadChunk>()
                    .eq(UploadChunk::getUploadId, uploadId)
                    .orderByAsc(UploadChunk::getChunkIndex));

        if (records.size() != totalChunks) {
            throw new ResponseStatusException(BAD_REQUEST,
                String.format("分片不完整: 已上传 %d/%d", records.size(), totalChunks));
        }

        // 校验所有分片文件存在
        Path tempDir = getTempDir(username, uploadId);
        long totalSize = 0;
        for (UploadChunk rec : records) {
            Path chunkFile = tempDir.resolve("chunk_" + rec.getChunkIndex());
            if (!Files.exists(chunkFile)) {
                throw new ResponseStatusException(BAD_REQUEST,
                    "分片 " + rec.getChunkIndex() + " 文件丢失");
            }
            totalSize += Files.size(chunkFile);
        }

        if (expectedSize > 0 && Math.abs(totalSize - expectedSize) > (totalChunks * 28L)) {
            throw new ResponseStatusException(BAD_REQUEST,
                String.format("文件大小不匹配: 期望 %d, 实际 %d", expectedSize, totalSize));
        }

        // 解密分片 → 合并明文流 → 压缩 → 加密 → 写入最终文件
        Path targetPath = fileService.resolveWritePath(filePath);
        Files.createDirectories(targetPath.getParent());

        String fileKey = sessionManager.getFileKey(username);

        try (OutputStream fos = Files.newOutputStream(targetPath)) {
            if (fileKey != null) {
                fos.write(0x01); // 加密标记
                byte[] nonce = new byte[12];
                new SecureRandom().nextBytes(nonce);
                fos.write(nonce);

                try {
                    SecretKeySpec key = new SecretKeySpec(hexToBytes(fileKey), "AES");
                    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                    cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));

                    try (CipherOutputStream cos = new CipherOutputStream(fos, cipher)) {
                        // 压缩后加密
                        boolean doCompress = shouldCompressChunk(filePath,
                                expectedSize > 0 ? expectedSize : totalSize);
                        if (doCompress) {
                            java.util.zip.Deflater deflater = new java.util.zip.Deflater(6);
                            try (java.util.zip.DeflaterOutputStream dos =
                                         new java.util.zip.DeflaterOutputStream(cos, deflater)) {
                                writePlainChunks(records, tempDir, fileKey, dos);
                                dos.finish();
                            }
                            deflater.end();
                        } else {
                            writePlainChunks(records, tempDir, fileKey, cos);
                        }
                    }
                } catch (Exception e) {
                    throw new IOException("加密失败", e);
                }
            } else {
                writePlainChunks(records, tempDir, null, fos);
            }
        }

        // 清理临时文件
        for (UploadChunk rec : records) {
            try {
                Files.deleteIfExists(tempDir.resolve("chunk_" + rec.getChunkIndex()));
            } catch (IOException ignored) {}
        }
        try { Files.deleteIfExists(tempDir); } catch (IOException ignored) {}

        // 清理数据库记录
        LambdaQueryWrapper<UploadChunk> del = new LambdaQueryWrapper<>();
        del.eq(UploadChunk::getUploadId, uploadId);
        uploadChunkMapper.delete(del);

        return Files.size(targetPath);
    }

    /**
     * 定时清理超时的上传会话
     */
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupStaleUploads() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(chunkTtlHours);
        List<UploadChunk> stale = uploadChunkMapper.selectList(
                new LambdaQueryWrapper<UploadChunk>()
                    .lt(UploadChunk::getCreatedAt, cutoff));

        for (UploadChunk rec : stale) {
            try {
                Path tempDir = getTempDir(rec.getUsername(), rec.getUploadId());
                Files.deleteIfExists(tempDir.resolve("chunk_" + rec.getChunkIndex()));
                Files.deleteIfExists(tempDir);
                uploadChunkMapper.deleteById(rec.getId());
            } catch (Exception ignored) {}
        }
    }

    private static final java.util.HashSet<String> SKIP_EXT =
            new java.util.HashSet<>(java.util.Arrays.asList(
                    ".jpg", ".jpeg", ".png", ".gif", ".webp", ".heic", ".avif",
                    ".mp4", ".mkv", ".avi", ".mov", ".webm", ".flv", ".wmv",
                    ".mp3", ".aac", ".ogg", ".wav", ".flac",
                    ".zip", ".rar", ".7z", ".gz", ".bz2", ".xz"));

    private boolean shouldCompressChunk(String filePath, long size) {
        if (size < 1024) return false;
        String lower = filePath.toLowerCase();
        for (String ext : SKIP_EXT) {
            if (lower.endsWith(ext)) return false;
        }
        return true;
    }

    private void writePlainChunks(List<UploadChunk> records, Path tempDir,
                                  String fileKey, OutputStream out) throws IOException {
        for (UploadChunk rec : records) {
            Path chunkFile = tempDir.resolve("chunk_" + rec.getChunkIndex());
            if (fileKey != null) {
                byte[] chunkData = Files.readAllBytes(chunkFile);
                out.write(cryptoService.decryptBytesWithHexKey(fileKey, chunkData));
            } else {
                try (InputStream in = Files.newInputStream(chunkFile)) {
                    byte[] buffer = new byte[65536];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        out.write(buffer, 0, n);
                    }
                }
            }
        }
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

    private Path getTempDir(String username, String uploadId) {
        return Path.of(storageRootPath, username, ".chunks", uploadId);
    }
}
