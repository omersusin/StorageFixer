package com.omersusin.storagefixer;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.topjohnwu.superuser.Shell;

import java.io.File;

public class StorageFixer {
    private static final String TAG = "StorageFixer";
    private static final String LOWER = "/data/media/0/Android";
    private static final String FUSE = "/storage/emulated/0/Android";
    private static final String[] DIR_TYPES = {"data", "obb", "media"};

    // SELinux contexts
    private static final String SECONTEXT_MEDIA_RW = "u:object_r:media_rw_data_file:s0";

    private static final int LEGACY_FLAG = ApplicationInfo.FLAG_SYSTEM;

    public static boolean isRootAvailable() {
        return Shell.getShell().isRoot();
    }

    public static boolean isFuseReady() {
        return new File(FUSE).exists();
    }

    public static void waitForFuse() {
        int attempts = 0;
        while (!isFuseReady() && attempts < 30) {
            try {
                Thread.sleep(1000);
                attempts++;
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    public static int getAppUid(Context ctx, String pkg) {
        try {
            ApplicationInfo info = ctx.getPackageManager()
                    .getApplicationInfo(pkg, 0);
            return info.uid;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Package not found: " + pkg);
            return -1;
        }
    }

    public static boolean isLegacyStorageApp(Context ctx, String pkg) {
        try {
            PackageInfo info = ctx.getPackageManager()
                    .getPackageInfo(pkg, PackageManager.GET_META_DATA);

            // Check targetSdkVersion < 30 (Android 11)
            if (info.applicationInfo.targetSdkVersion < 30) {
                return true;
            }

            // Check for legacy storage flag
            if ((info.applicationInfo.flags & LEGACY_FLAG) != 0) {
                return true;
            }

            return false;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean needsFix(Context ctx, String pkg) {
        int uid = getAppUid(ctx, pkg);
        if (uid == -1) return false;

        // Check standard Android directories
        for (String type : DIR_TYPES) {
            File dir = new File(LOWER + "/" + type + "/" + pkg);
            if (dir.exists()) {
                String[] contents = dir.list();
                if (contents != null && contents.length == 0) return true;
            }
        }
        return false;
    }

    public static void fixPackage(Context ctx, String pkg) {
        int uid = getAppUid(ctx, pkg);
        if (uid == -1) return;

        // Apply fixes for standard directories only (data, obb, media)
        for (String type : DIR_TYPES) {
            String path = LOWER + "/" + type + "/" + pkg;
            fixDir(path, uid);
        }
    }

    private static void fixDir(String path, int uid) {
        File file = new File(path);
        if (!file.exists()) return;

        String cmd = String.format(
                "mkdir -p %s && chown %d:%d %s && chmod 777 %s && chcon %s %s",
                path, uid, uid, path, path, SECONTEXT_MEDIA_RW, path
        );
        Shell.cmd(cmd).exec();
    }
}
