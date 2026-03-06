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

        FixerLog.i("📦 " + intent.getAction() + " → " + pkg);

        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                if (!StorageFixer.isFuseReady()) {
                    StorageFixer.waitForFuse(30);
                }

                // First pass: immediate fix
                FixerLog.i("Pass 1/2 for " + pkg);
                StorageFixer.fixPackage(pkg);

                // Wait for app to create its subdirectories
                Thread.sleep(3000);

                // Second pass: catch late-created subdirs
                FixerLog.i("Pass 2/2 for " + pkg);
                StorageFixer.FixResult r = StorageFixer.fixPackage(pkg);
                FixerLog.i("Auto-fix result: " + r);

            } catch (InterruptedException ignored) {
            } finally {
                pending.finish();
            }
        }).start();
    }
}
