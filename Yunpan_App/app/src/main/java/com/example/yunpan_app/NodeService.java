package com.example.yunpan_app;

import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.StatFs;
import androidx.core.app.NotificationCompat;

public class NodeService extends Service {
    private static final String CHANNEL_ID = "node_channel";
    private static final int NOTIFY_ID = 1;

    private NodeHttpServer httpServer;
    private FrpcManager frpcManager;
    private Thread heartbeatThread;
    private volatile boolean running;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFY_ID, buildNotification("节点启动中"),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFY_ID, buildNotification("节点启动中"));
            }
        } catch (Exception e) {
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startNode();
        return START_STICKY;
    }

    private synchronized void startNode() {
        if (running) return;
        running = true;
        NodeConfig config = NodeConfig.load(this);
        NativeStorage storage = new NativeStorage(this);
        CloudClient cloud = new CloudClient(config);
        httpServer = new NodeHttpServer(config, storage, cloud);
        frpcManager = new FrpcManager(this);

        new Thread(() -> {
            try {
                httpServer.start();
                frpcManager.start(config);
                cloud.register();
                startHeartbeat(config, storage, cloud);
                updateNotification("节点运行中");
            } catch (Exception e) {
                updateNotification("节点启动失败");
                e.printStackTrace();
            }
        }, "node-starter").start();
    }

    private void startHeartbeat(NodeConfig config, NativeStorage storage, CloudClient cloud) {
        heartbeatThread = new Thread(() -> {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    StatFs stat = new StatFs(storage.storageRoot().getAbsolutePath());
                    cloud.heartbeat(stat, storage.usedBytes());
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ignored) {
                    try { Thread.sleep(30000); } catch (InterruptedException e) { break; }
                }
            }
        }, "node-heartbeat");
        heartbeatThread.start();
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFY_ID, buildNotification(text));
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("JiaLe Cloud 存储节点")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_upload)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "节点监控",
                    NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        if (heartbeatThread != null) heartbeatThread.interrupt();
        if (httpServer != null) httpServer.stop();
        if (frpcManager != null) frpcManager.stop();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
