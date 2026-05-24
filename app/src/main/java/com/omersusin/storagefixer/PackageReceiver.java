package com.omersusin.storagefixer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class PackageReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        if ("android.intent.action.DOWNLOAD_COMPLETE".equals(action)) {
            long downloadId = intent.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            FixerLog.i("Download complete event received for ID: " + downloadId);
            if (downloadId == -1) return;

            PendingResult pending = goAsync();
            new Thread(() -> {
                try {
                    // Wait a moment for file to be flushed and finalized
                    Thread.sleep(1000);

                    // Query the database via root content command to find the initiating package
                    com.topjohnwu.superuser.Shell.Result res = com.topjohnwu.superuser.Shell.cmd(
                        "content query --uri content://downloads/all_downloads --projection notificationpackage --where \"_id=" + downloadId + "\""
                    ).exec();

                    String pkg = null;
                    if (res.isSuccess() && !res.getOut().isEmpty()) {
                        for (String line : res.getOut()) {
                            if (line.contains("notificationpackage=")) {
                                int start = line.indexOf("notificationpackage=") + "notificationpackage=".length();
                                int end = line.indexOf(",", start);
                                if (end == -1) end = line.length();
                                pkg = line.substring(start, end).trim();
                                break;
                            }
                        }
                    }

                    if (pkg != null && !pkg.isEmpty()) {
                        FixerLog.i("Found download initiator package: " + pkg);
                        if (!IgnoredAppsManager.isIgnored(context, pkg)) {
                            StorageFixer.fixPackage(context, pkg);
                            FixerLog.i("Auto-fix complete after download completion for: " + pkg);
                        } else {
                            FixerLog.i("Skipping ignored download initiator: " + pkg);
                        }
                    } else {
                        // Fallback to fixAll just in case we couldn't resolve the initiator
                        FixerLog.w("Could not resolve initiator package, falling back to fixAll...");
                        StorageFixer.fixAll(context);
                    }
                } catch (Exception e) {
                    FixerLog.e("Download complete handling failed: " + e.getMessage());
                } finally {
                    pending.finish();
                }
            }).start();
            return;
        }

        if (intent.getData() == null) return;

        String pkg = intent.getData().getSchemeSpecificPart();
        if (pkg == null) return;
        if (pkg.equals(context.getPackageName())) return;

        if (IgnoredAppsManager.isIgnored(context, pkg)) {
            FixerLog.i("Skipping ignored app: " + pkg);
            return;
        }

        FixerLog.i("Package event: " + intent.getAction() + " -> " + pkg);

        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                // Wait 10s for vold to try and fail
                FixerLog.i("Waiting 10s for vold...");
                Thread.sleep(10000);

                // Fix directories if needed
                if (StorageFixer.needsFix(context, pkg)) {
                    FixerLog.i("Fixing dirs for " + pkg);
                    StorageFixer.fixPackage(context, pkg);
                }

                // Always fix appops for new installs
                StorageFixer.fixAppops(pkg);

                // Force stop so app picks up new permissions
                StorageFixer.forceStopPackage(pkg);

                // Rescan
                StorageFixer.triggerMediaRescan();

                FixerLog.i("Auto-fix complete for " + pkg);

            } catch (InterruptedException ignored) {
            } finally {
                pending.finish();
            }
        }).start();
    }
}
