package org.example.cun;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

@Service
public class FileStorageService {
    private final CryptoService crypto;
    private final Path rootPath;
    private static final Set<String> ALLOWED_EXT = Set.of(".jpg",".jpeg",".png",".gif",".webp",".mp4",".mkv",
            ".avi",".mov",".mp3",".aac",".pdf",".doc",".docx",".xls",".xlsx",".ppt",".pptx",
            ".txt",".csv",".json",".xml",".zip",".rar",".7z",".gz",".tar",".iso",
            ".java",".py",".js",".ts",".html",".css",".c",".cpp",".go",".rs");
    private final boolean compressionEnabled;
    private final int compressionLevel;
    private final Set<String> skipExt;
    private final long minCompressSize;

    public FileStorageService(CryptoService crypto,
            @Value("${yunpan.storage.root-path:./storage_data}") String root,
            @Value("${yunpan.compression.enabled:true}") boolean ce,
            @Value("${yunpan.compression.level:6}") int cl,
            @Value("${yunpan.compression.skip-extensions:jpg,jpeg,png,gif,webp,mp4,mkv,avi,mov,mp3,aac,zip,rar,7z,gz}") String se,
            @Value("${yunpan.compression.min-size:1024}") long mcs) {
        this.crypto = crypto;
        this.rootPath = Path.of(root);
        this.compressionEnabled = ce;
        this.compressionLevel = cl;
        this.minCompressSize = mcs;
        this.skipExt = new HashSet<>();
        for(String e : se.split(",")) skipExt.add("." + e.trim().toLowerCase());
    }

    private boolean shouldCompress(String name, long size) {
        if(!compressionEnabled || size < minCompressSize) return false;
        String l = name.toLowerCase();
        for(String e : skipExt) if(l.endsWith(e)) return false;
        return true;
    }

    /** 保存分片，加密存盘。返回加密后大小 */
    public long saveChunk(String username, String uploadId, int chunkIndex, InputStream in) throws IOException {
        Path dir = userRoot(username).resolve(".chunks").resolve(uploadId);
        Files.createDirectories(dir);
        Path f = dir.resolve("c_" + chunkIndex);
        byte[] nonce = crypto.generateNonce();
        try (OutputStream fos = Files.newOutputStream(f)) {
            fos.write(nonce);
            try (CipherOutputStream cos = new CipherOutputStream(fos, crypto.createEncryptCipher(nonce))) {
                byte[] buf = new byte[65536]; int n; while((n = in.read(buf)) != -1) cos.write(buf, 0, n);
            }
        }
        return Files.size(f);
    }

    /** 合并分片 → 解密 → 压缩 → 加密 → 最终文件（流式） */
    public long mergeChunks(String username, String uploadId, String targetPath,
                            String fileName, int totalChunks) throws IOException {
        Path userRoot = userRoot(username);
        Path dir = userRoot.resolve(".chunks").resolve(uploadId);
        Path tp = resolveSafe(userRoot, targetPath);
        Files.createDirectories(tp.getParent());
        byte[] nonce = crypto.generateNonce();
        // 估算大小用于判断是否压缩
        long estimate = 0;
        for (int i = 0; i < totalChunks; i++) estimate += Files.size(dir.resolve("c_" + i));
        boolean doCompress = shouldCompress(fileName, estimate);
        try (OutputStream fos = Files.newOutputStream(tp)) {
            fos.write(0x01); fos.write(nonce);
            OutputStream chain = fos;
            chain = new javax.crypto.CipherOutputStream(chain, crypto.createEncryptCipher(nonce));
            java.util.zip.Deflater def = null; java.util.zip.DeflaterOutputStream dos = null;
            if (doCompress) {
                def = new java.util.zip.Deflater(compressionLevel);
                dos = new java.util.zip.DeflaterOutputStream(chain, def);
                chain = dos;
            }
            byte[] buf = new byte[65536];
            for (int i = 0; i < totalChunks; i++) {
                Path cf = dir.resolve("c_" + i);
                byte[] data = Files.readAllBytes(cf);
                byte[] cn = Arrays.copyOf(data, 12);
                try (javax.crypto.CipherInputStream cis = new javax.crypto.CipherInputStream(
                        new ByteArrayInputStream(data, 12, data.length - 12),
                        crypto.createDecryptCipher(cn))) {
                    int n; while((n = cis.read(buf)) != -1) chain.write(buf, 0, n);
                }
                Files.deleteIfExists(cf);
            }
            if (dos != null) { dos.finish(); def.end(); }
            chain.flush();
        }
        Files.deleteIfExists(dir);
        return Files.size(tp);
    }

    private Path resolveSafe(Path root, String relPath) {
        Path resolved = root.resolve(relPath).normalize();
        if (!resolved.startsWith(root)) throw new RuntimeException("路径穿越拒绝: " + relPath);
        return resolved;
    }

    /** 下载文件：解密 → 解压 → 写入临时文件，避免大文件进入内存 */
    public Path materializeDownload(String username, String relPath) throws IOException {
        Path f = resolveSafe(userRoot(username), relPath);
        try(InputStream fin = Files.newInputStream(f)) {
            int marker = fin.read();
            if(marker != 0x01) return f; // plaintext
            byte[] nonce = new byte[12]; fin.read(nonce);
            Path temp = Files.createTempFile("cun_dl_", ".tmp");
            try(CipherInputStream cis = new CipherInputStream(fin, crypto.createDecryptCipher(nonce))) {
                InputStream src = cis;
                if(shouldCompress(f.getFileName().toString(), Files.size(f))) src = new InflaterInputStream(cis);
                try (OutputStream out = Files.newOutputStream(temp)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = src.read(buf)) != -1) out.write(buf, 0, n);
                }
                return temp;
            } catch (IOException | RuntimeException e) {
                Files.deleteIfExists(temp);
                throw e;
            }
        }
    }

    public void deleteFile(String username, String relPath) throws IOException {
        Files.deleteIfExists(resolveSafe(userRoot(username), relPath));
    }
    public long fileSize(String username, String relPath) throws IOException {
        return Files.size(resolveSafe(userRoot(username), relPath));
    }

    /** 列出存储目录下所有文件 */
    public List<Map<String,Object>> listFiles(String username) throws IOException {
        List<Map<String,Object>> result = new ArrayList<>();
        Path userRoot = userRoot(username);
        if (!Files.exists(userRoot)) return result;
        try (var walk = Files.walk(userRoot)) {
            walk.filter(f -> !Files.isDirectory(f) && !f.toString().contains(".chunks"))
                .forEach(f -> {
                    try {
                        Map<String,Object> m = new LinkedHashMap<>();
                        Path rel = userRoot.relativize(f);
                        m.put("fileName", rel.toString());
                        m.put("size", Files.size(f));
                        m.put("lastModified", Files.getLastModifiedTime(f).toInstant().toString());
                        result.add(m);
                    } catch (IOException ignored) {}
                });
        }
        result.sort((a,b) -> b.get("lastModified").toString().compareTo(a.get("lastModified").toString()));
        return result;
    }

    private Path userRoot(String username) throws IOException {
        String safe = username == null ? "unknown" : username.replace("@", "_at_").replace("..", "");
        Path root = rootPath.resolve(safe).normalize();
        Files.createDirectories(root);
        return root.toRealPath();
    }
}
