package com.omersusin.storagefixer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class PackageReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getData() == null) return;

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
                if (StorageFixer.needsFix(pkg)) {
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
