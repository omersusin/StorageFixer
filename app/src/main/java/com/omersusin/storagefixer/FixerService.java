package com.omersusin.storagefixer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class FixerService extends Service {

    private static final int NOTIF_ID = 1;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private PackageReceiver pkgReceiver;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotif("Starting..."),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);

        if (!running.compareAndSet(false, true)) {
            FixerLog.w("Already running, skipping");
            return START_NOT_STICKY;
        }

        new Thread(() -> {
            try {
                if (!StorageFixer.isRootAvailable()) {
                    FixerLog.e("Root not available");
                    updateNotif("No root");
                    shutdown();
                    return;
                }

                updateNotif("Waiting for storage...");
                if (!StorageFixer.waitForFuse(60)) {
                    updateNotif("Storage timeout");
                    shutdown();
                    return;
                }

                // Wait extra 5s on boot for vold to finish
                FixerLog.i("Waiting 5s for vold to settle after boot...");
                Thread.sleep(5000);

                updateNotif("Scanning...");
                List<StorageFixer.FixResult> results =
                        StorageFixer.fixAll(this);

                long ok = results.stream()
                        .filter(r -> r.success).count();
                String msg = ok + "/" + results.size() + " fixed";
                updateNotif(msg);

                // Stay alive and watch for new installs/downloads
                registerPackageReceiver();
                updateNotif("Watching for installs (" + msg + ")");

            } catch (Exception e) {
                FixerLog.e("Service error: " + e.getMessage());
                shutdown();
            }
        }).start();

        return START_NOT_STICKY;
    }

    private void shutdown() {
        running.set(false);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void registerPackageReceiver() {
        if (pkgReceiver != null) return;
        pkgReceiver = new PackageReceiver();

        IntentFilter pkg = new IntentFilter();
        pkg.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkg.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pkg.addDataScheme("package");
        ContextCompat.registerReceiver(this, pkgReceiver, pkg,
                ContextCompat.RECEIVER_NOT_EXPORTED);

        IntentFilter dl = new IntentFilter("android.intent.action.DOWNLOAD_COMPLETE");
        ContextCompat.registerReceiver(this, pkgReceiver, dl,
                ContextCompat.RECEIVER_NOT_EXPORTED);

        FixerLog.i("Dynamic package receiver registered");
    }

    @Override
    public void onDestroy() {
        if (pkgReceiver != null) {
            try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) {}
            pkgReceiver = null;
        }
        super.onDestroy();
    }

    private Notification buildNotif(String text) {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, App.CHANNEL_ID)
                .setContentTitle("StorageFixer")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void updateNotif(String text) {
        getSystemService(NotificationManager.class)
                .notify(NOTIF_ID, buildNotif(text));
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
