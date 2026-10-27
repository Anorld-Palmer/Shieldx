package com.admincontrol;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class FirewallService extends VpnService {
    public static final String ACTION_START = "com.admincontrol.FIREWALL_START";
    private static final String CHANNEL = "admin_control_firewall";
    private ParcelFileDescriptor tunnel;
    private Thread discardThread;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        createNotification();
        startForeground(7, notification());
        startTunnel();
        return START_STICKY;
    }

    private void createNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "ADMIN CONTROL Firewall",
                    NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification notification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        if (Build.VERSION.SDK_INT >= 26) {
            return new Notification.Builder(this, CHANNEL)
                    .setContentTitle("ADMIN CONTROL firewall")
                    .setContentText("Selected app traffic is being blocked")
                    .setSmallIcon(android.R.drawable.ic_lock_lock)
                    .setContentIntent(pi).setOngoing(true).build();
        }
        return new Notification.Builder(this)
                .setContentTitle("ADMIN CONTROL firewall")
                .setContentText("Selected app traffic is being blocked")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentIntent(pi).setOngoing(true).build();
    }

    private synchronized void startTunnel() {
        closeTunnel();
        Set<String> blocked = getSharedPreferences("firewall", MODE_PRIVATE)
                .getStringSet("blocked", new HashSet<>());
        if (blocked == null || blocked.isEmpty()) {
            stopSelf();
            return;
        }

        Builder builder = new Builder()
                .setSession("ADMIN CONTROL Firewall")
                .setMtu(1500)
                .addAddress("10.10.0.2", 32)
                .addRoute("0.0.0.0", 0);

        // IPv6 is included to reduce the chance of IPv6 traffic bypassing the block.
        try {
            builder.addAddress("fd00:1:fd00:1::2", 128).addRoute("::", 0);
        } catch (Exception ignored) { }

        int added = 0;
        PackageManager pm = getPackageManager();
        for (String pkg : blocked) {
            if (pkg.equals(getPackageName())) continue;
            try {
                pm.getApplicationInfo(pkg, 0);
                builder.addAllowedApplication(pkg);
                added++;
            } catch (Exception ignored) {
                // The package may have been removed or be unavailable to this Android user.
            }
        }
        if (added == 0) {
            stopSelf();
            return;
        }

        try {
            tunnel = builder.establish();
            if (tunnel == null) {
                stopSelf();
                return;
            }
            ParcelFileDescriptor active = tunnel;
            discardThread = new Thread(() -> {
                byte[] buffer = new byte[32767];
                try (FileInputStream in = new FileInputStream(active.getFileDescriptor())) {
                    while (!Thread.currentThread().isInterrupted()) {
                        int n = in.read(buffer);
                        if (n < 0) break;
                        // Intentionally discard packets for selected apps: this is the block.
                    }
                } catch (IOException ignored) { }
            }, "AdminControlPacketDropper");
            discardThread.start();
        } catch (Exception e) {
            stopSelf();
        }
    }

    private synchronized void closeTunnel() {
        if (discardThread != null) {
            discardThread.interrupt();
            discardThread = null;
        }
        if (tunnel != null) {
            try { tunnel.close(); } catch (IOException ignored) { }
            tunnel = null;
        }
    }

    @Override public void onRevoke() {
        closeTunnel();
        stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() {
        closeTunnel();
        stopForeground(true);
        super.onDestroy();
    }
}
