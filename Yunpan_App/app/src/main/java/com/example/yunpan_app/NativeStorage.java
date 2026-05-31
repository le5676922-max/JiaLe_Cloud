package com.example.yunpan_app;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class NativeStorage {
    private final File root;

    public NativeStorage(Context context) {
        this.root = new File(context.getFilesDir(), "storage_data");
        //noinspection ResultOfMethodCallIgnored
        root.mkdirs();
    }

    public long saveChunk(String username, String uploadId, int chunkIndex, InputStream in) throws IOException {
        File dir = safeFile(userRoot(username), ".chunks/" + uploadId);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File chunk = new File(dir, "c_" + chunkIndex);
        try (OutputStream out = new FileOutputStream(chunk)) {
            copy(in, out);
        }
        return chunk.length();
    }

    public long mergeChunks(String username, String uploadId, String filePath, int totalChunks) throws IOException {
        File userRoot = userRoot(username);
        File chunkDir = safeFile(userRoot, ".chunks/" + uploadId);
        File target = safeFile(userRoot, filePath);
        File parent = target.getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        try (OutputStream out = new FileOutputStream(target)) {
            for (int i = 0; i < totalChunks; i++) {
                File chunk = new File(chunkDir, "c_" + i);
                if (!chunk.exists()) throw new FileNotFoundException("missing chunk " + i);
                try (InputStream in = new FileInputStream(chunk)) {
                    copy(in, out);
                }
                //noinspection ResultOfMethodCallIgnored
                chunk.delete();
            }
        }
        //noinspection ResultOfMethodCallIgnored
        chunkDir.delete();
        return target.length();
    }

    public File downloadFile(String username, String filePath) throws IOException {
        File f = safeFile(userRoot(username), filePath);
        if (!f.isFile()) throw new FileNotFoundException(filePath);
        return f;
    }

    public JSONArray listFiles(String username) throws IOException {
        JSONArray arr = new JSONArray();
        File userRoot = userRoot(username);
        listRecursive(userRoot, userRoot, arr);
        return arr;
    }

    public long usedBytes() {
        return sizeOf(root);
    }

    public File storageRoot() {
        return root;
    }

    private File userRoot(String username) throws IOException {
        String safe = sanitize(username == null || username.isBlank() ? "unknown" : username);
        File userRoot = new File(root, safe);
        //noinspection ResultOfMethodCallIgnored
        userRoot.mkdirs();
        return userRoot.getCanonicalFile();
    }

    private File safeFile(File base, String relativePath) throws IOException {
        File f = new File(base, relativePath == null ? "" : relativePath).getCanonicalFile();
        if (!f.getPath().startsWith(base.getCanonicalPath())) {
            throw new SecurityException("path traversal");
        }
        return f;
    }

    private String sanitize(String value) {
        return value.replace("@", "_at_")
                .replace("..", "")
                .replace("/", "_")
                .replace("\\", "_")
                .trim();
    }

    private void listRecursive(File base, File dir, JSONArray arr) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File f : files) {
            if (f.getName().equals(".chunks")) continue;
            if (f.isDirectory()) {
                listRecursive(base, f, arr);
            } else {
                JSONObject item = new JSONObject();
                String rel = base.toPath().relativize(f.toPath()).toString().replace(File.separatorChar, '/');
                try {
                    item.put("fileName", rel);
                    item.put("size", f.length());
                    item.put("lastModified", f.lastModified());
                    arr.put(item);
                } catch (Exception ignored) {}
            }
        }
    }

    private long sizeOf(File file) {
        if (file == null || !file.exists()) return 0;
        if (file.isFile()) return file.length();
        File[] files = file.listFiles();
        if (files == null) return 0;
        long total = 0;
        for (File child : files) total += sizeOf(child);
        return total;
    }

    private void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
    }
}
