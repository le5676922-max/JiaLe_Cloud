package com.example.yunpan_app;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class FrpcManager {
    private final Context context;
    private Process process;

    public FrpcManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public synchronized void start(NodeConfig config) {
        if (process != null && process.isAlive()) return;
        try {
            File workDir = new File(context.getFilesDir(), "frpc");
            //noinspection ResultOfMethodCallIgnored
            workDir.mkdirs();
            File ini = new File(workDir, "frpc.ini");
            writeConfig(ini, config);
            File frpc = locateFrpc(config, workDir);
            process = new ProcessBuilder(frpc.getAbsolutePath(), "-c", ini.getAbsolutePath())
                    .directory(workDir)
                    .redirectErrorStream(true)
                    .redirectOutput(new File(workDir, "frpc.log"))
                    .start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void stop() {
        if (process != null) {
            process.destroy();
            process = null;
        }
    }

    private void writeConfig(File ini, NodeConfig config) throws IOException {
        String text = "[common]\n"
                + "server_addr = " + config.normalizedFrpHost() + "\n"
                + "server_port = 443\n"
                + "[" + config.nodeId + "]\n"
                + "type = tcp\n"
                + "local_ip = 127.0.0.1\n"
                + "local_port = " + config.localPort + "\n"
                + "remote_port = " + config.remotePort + "\n";
        try (OutputStream out = new FileOutputStream(ini)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private File locateFrpc(NodeConfig config, File workDir) throws IOException {
        ApplicationInfo info = context.getApplicationInfo();
        File bundled = new File(info.nativeLibraryDir, "libfrpc.so");
        if (bundled.exists() && bundled.canExecute()) return bundled;

        File downloaded = new File(workDir, "frpc");
        if (!downloaded.exists()) {
            download(config.apiUrl("/frpc"), downloaded);
        }
        //noinspection ResultOfMethodCallIgnored
        downloaded.setExecutable(true, true);
        return downloaded;
    }

    private void download(String url, File dest) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(30000);
        try (InputStream in = c.getInputStream();
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        } finally {
            c.disconnect();
        }
    }
}
