package com.viewer.so.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * 前台服务：维持本地 HTTP Server 存活。
 *
 * 原插件由 MT 管理器进程托管，独立 App 必须自行保证后台 socket 不被回收，
 * 因此以 foregroundService(dataSync) 形式常驻。
 */
public class PreviewService extends Service {

    private static final String CHANNEL_ID = "so_viewer_server";
    private static final int NOTIFY_ID = 1001;

    public static void start(Context ctx) {
        Intent i = new Intent(ctx, PreviewService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i);
            } else {
                ctx.startService(i);
            }
        } catch (Throwable ignored) {}
    }

    public static void stop(Context ctx) {
        try {
            ctx.stopService(new Intent(ctx, PreviewService.class));
        } catch (Throwable ignored) {}
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFY_ID, buildNotification());
        App.init(this);
        HttpServer.startIfNeeded(this, null);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        App.init(this);
        HttpServer.startIfNeeded(this, null);
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null) return;
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID, "本地预览服务", NotificationManager.IMPORTANCE_MIN);
                ch.setDescription("维持文件预览所需的本地服务");
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SO文件预览器")
                .setContentText("本地预览服务运行中")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 服务销毁时保留 Server（可能还有预览页在用），由系统回收时自然释放
    }
}