package com.example.yunpan_app;

import org.json.JSONObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class NodeHttpServer {
    private final NodeConfig config;
    private final NativeStorage storage;
    private final NodeAuth auth;
    private final CloudClient cloud;
    private ServerSocket serverSocket;
    private Thread thread;

    public NodeHttpServer(NodeConfig config, NativeStorage storage, CloudClient cloud) {
        this.config = config;
        this.storage = storage;
        this.cloud = cloud;
        this.auth = new NodeAuth(config, cloud);
    }

    public synchronized void start() throws IOException {
        if (thread != null && thread.isAlive()) return;
        serverSocket = new ServerSocket(config.localPort);
        thread = new Thread(this::serveLoop, "node-http-server");
        thread.start();
    }

    public synchronized void stop() {
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        if (thread != null) thread.interrupt();
    }

    private void serveLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Socket socket = serverSocket.accept();
                new Thread(() -> handle(socket), "node-http-client").start();
            } catch (IOException e) {
                if (!serverSocket.isClosed()) e.printStackTrace();
                break;
            }
        }
    }

    private void handle(Socket socket) {
        try (socket;
             BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
             OutputStream out = socket.getOutputStream()) {
            Request req = readRequest(in);
            if (req == null) return;
            if ("OPTIONS".equals(req.method)) {
                send(out, 204, "text/plain", new byte[0]);
                return;
            }
            if ("GET".equals(req.method) && "/api/health".equals(req.path)) {
                sendJson(out, 200, "{\"status\":\"ok\"}");
                return;
            }
            if ("POST".equals(req.method) && "/api/upload/chunk".equals(req.path)) {
                String username = auth.requireUser(req.headers.get("authorization"));
                int chunkIndex = Integer.parseInt(req.query.getOrDefault("chunkIndex", "0"));
                String uploadId = req.query.get("uploadId");
                byte[] chunk = extractMultipartFile(req.body, req.headers.get("content-type"));
                long size = storage.saveChunk(username, uploadId, chunkIndex, new ByteArrayInputStream(chunk));
                sendJson(out, 200, "{\"code\":200,\"message\":\"ok\",\"size\":" + size + "}");
                return;
            }
            if ("POST".equals(req.method) && "/api/upload/merge".equals(req.path)) {
                String username = auth.requireUser(req.headers.get("authorization"));
                JSONObject body = new JSONObject(new String(req.body, StandardCharsets.UTF_8));
                String uploadId = body.getString("uploadId");
                String filePath = body.getString("filePath");
                int totalChunks = body.getInt("totalChunks");
                long size = storage.mergeChunks(username, uploadId, filePath, totalChunks);
                cloud.recordFile(username, filePath, size);
                sendJson(out, 200, "{\"code\":200,\"message\":\"ok\",\"data\":{\"filePath\":\""
                        + escape(filePath) + "\",\"size\":" + size + "}}");
                return;
            }
            if ("GET".equals(req.method) && "/api/files/download".equals(req.path)) {
                String username;
                if (auth.isNodeToken(req.headers.get("x-node-token"))) {
                    username = req.query.get("username");
                } else {
                    username = auth.requireUser(req.headers.get("authorization"));
                }
                File file = storage.downloadFile(username, req.query.get("path"));
                sendFile(out, file);
                return;
            }
            if ("GET".equals(req.method) && "/api/files/list".equals(req.path)) {
                String username;
                if (auth.isNodeToken(req.headers.get("x-node-token"))) {
                    username = req.query.getOrDefault("username", config.email);
                } else {
                    username = auth.requireUser(req.headers.get("authorization"));
                }
                sendJson(out, 200, storage.listFiles(username).toString());
                return;
            }
            sendJson(out, 404, "{\"code\":404,\"message\":\"not found\"}");
        } catch (SecurityException e) {
            try { sendJson(socket, 401, "{\"code\":401,\"message\":\"unauthorized\"}"); } catch (IOException ignored) {}
        } catch (Throwable e) {
            try { sendJson(socket, 500, "{\"code\":500,\"message\":\"" + escape(e.getMessage()) + "\"}"); }
            catch (IOException ignored) {}
        }
    }

    private Request readRequest(BufferedInputStream in) throws IOException {
        StringBuilder headers = new StringBuilder();
        int prev = -1, curr;
        while ((curr = in.read()) != -1) {
            headers.append((char) curr);
            if (prev == '\r' && curr == '\n' && headers.toString().endsWith("\r\n\r\n")) break;
            prev = curr;
        }
        if (headers.length() == 0) return null;
        String[] lines = headers.toString().split("\r\n");
        String[] first = lines[0].split(" ");
        Request req = new Request();
        req.method = first[0];
        String target = first[1];
        int q = target.indexOf('?');
        req.path = q >= 0 ? target.substring(0, q) : target;
        req.query = q >= 0 ? parseQuery(target.substring(q + 1)) : new HashMap<>();
        req.headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) {
                req.headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        lines[i].substring(colon + 1).trim());
            }
        }
        int length = Integer.parseInt(req.headers.getOrDefault("content-length", "0"));
        req.body = StreamUtils.readExactBytes(in, length);
        return req;
    }

    private Map<String, String> parseQuery(String query) throws UnsupportedEncodingException {
        Map<String, String> map = new HashMap<>();
        for (String pair : query.split("&")) {
            if (pair.isBlank()) continue;
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            map.put(URLDecoder.decode(key, "UTF-8"), URLDecoder.decode(value, "UTF-8"));
        }
        return map;
    }

    private byte[] extractMultipartFile(byte[] body, String contentType) throws IOException {
        if (contentType == null || !contentType.contains("boundary=")) return body;
        String boundary = "--" + contentType.substring(contentType.indexOf("boundary=") + 9);
        byte[] marker = ("\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1);
        int start = indexOf(body, marker, 0);
        if (start < 0) return body;
        start += marker.length;
        byte[] endMarker = ("\r\n" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        int end = indexOf(body, endMarker, start);
        if (end < 0) end = body.length;
        byte[] file = new byte[Math.max(0, end - start)];
        System.arraycopy(body, start, file, 0, file.length);
        return file;
    }

    private int indexOf(byte[] data, byte[] pattern, int from) {
        outer:
        for (int i = from; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private void sendJson(Socket socket, int status, String json) throws IOException {
        send(socket.getOutputStream(), status, "application/json;charset=UTF-8",
                json.getBytes(StandardCharsets.UTF_8));
    }

    private void sendJson(OutputStream out, int status, String json) throws IOException {
        send(out, status, "application/json;charset=UTF-8", json.getBytes(StandardCharsets.UTF_8));
    }

    private void sendFile(OutputStream out, File file) throws IOException {
        String header = "HTTP/1.1 200 OK\r\n"
                + corsHeaders()
                + "Content-Type: application/octet-stream\r\n"
                + "Content-Length: " + file.length() + "\r\n"
                + "Content-Disposition: attachment; filename=\"" + file.getName() + "\"\r\n"
                + "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        }
    }

    private void send(OutputStream out, int status, String contentType, byte[] body) throws IOException {
        String header = "HTTP/1.1 " + status + " " + reason(status) + "\r\n"
                + corsHeaders()
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(body);
    }

    private String corsHeaders() {
        return "Access-Control-Allow-Origin: *\r\n"
                + "Access-Control-Allow-Headers: Authorization,Content-Type,X-Node-Token\r\n"
                + "Access-Control-Allow-Methods: GET,POST,OPTIONS\r\n"
                + "Access-Control-Expose-Headers: Content-Disposition\r\n";
    }

    private String reason(int status) {
        if (status == 200) return "OK";
        if (status == 204) return "No Content";
        if (status == 401) return "Unauthorized";
        if (status == 404) return "Not Found";
        return "Internal Server Error";
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static class Request {
        String method;
        String path;
        Map<String, String> query;
        Map<String, String> headers;
        byte[] body;
    }
}
