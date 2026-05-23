package com.omersusin.storagefixer;

import android.content.Context;
import java.io.File;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XposedModule implements IXposedHookLoadPackage {

    private static final String TAG = "StorageFixer-Xposed";

    // Lower FS paths that bind-mounts resolve to
    private static final String[][] PATH_FIXES = {
        {"/data/media/0/", "/storage/emulated/0/"},
        {"/mnt/pass_through/0/emulated/0/", "/storage/emulated/0/"},
        {"/mnt/pass_through/0/", "/storage/emulated/0/"},
        {"/mnt/androidwritable/0/emulated/0/", "/storage/emulated/0/"},
        {"/mnt/installer/0/emulated/0/", "/storage/emulated/0/"},
        {"/mnt/user/0/emulated/0/", "/storage/emulated/0/"},
    };

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        // Skip system framework and our own app
        if (lpparam.packageName.equals("android") ||
            lpparam.packageName.equals("com.omersusin.storagefixer")) {
            return;
        }

        if (lpparam.packageName.equals("idm.internet.download.manager.plus")) {
            XposedBridge.log(TAG + ": Loaded into IDM+ package: " + lpparam.packageName);
        }

        // Try to hook FileProvider in every app (scoped via LSPosed)
        hookFileProvider(lpparam);
        hookFileProviderCompat(lpparam);
    }

    private void hookFileProvider(final LoadPackageParam lpparam) {
        // Hook AndroidX FileProvider (compiled into most apps)
        try {
            XposedHelpers.findAndHookMethod(
                "androidx.core.content.FileProvider",
                lpparam.classLoader,
                "getUriForFile",
                Context.class,
                String.class,
                File.class,
                new FileProviderHook(lpparam.packageName)
            );
        } catch (Throwable ignored) {
            // App doesn't use AndroidX FileProvider
        }

        // Hook the 4-param overload too
        try {
            XposedHelpers.findAndHookMethod(
                "androidx.core.content.FileProvider",
                lpparam.classLoader,
                "getUriForFile",
                Context.class,
                String.class,
                File.class,
                String.class,
                new FileProviderHook(lpparam.packageName)
            );
        } catch (Throwable ignored) {
        }
    }

    private void hookFileProviderCompat(final LoadPackageParam lpparam) {
        // Some apps use the old support library FileProvider
        try {
            XposedHelpers.findAndHookMethod(
                "android.support.v4.content.FileProvider",
                lpparam.classLoader,
                "getUriForFile",
                Context.class,
                String.class,
                File.class,
                new FileProviderHook(lpparam.packageName)
            );
        } catch (Throwable ignored) {
        }
    }

    private static class FileProviderHook extends XC_MethodHook {
        private final String packageName;

        FileProviderHook(String packageName) {
            this.packageName = packageName;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            // File is always the 3rd parameter (index 2)
            File originalFile = (File) param.args[2];
            if (originalFile == null) return;

            String path = originalFile.getAbsolutePath();
            String fixedPath = fixPath(path);

            if (fixedPath != null) {
                param.args[2] = new File(fixedPath);
                XposedBridge.log(TAG + ": [" + packageName + "] "
                    + path + " -> " + fixedPath);
            }
        }

        private String fixPath(String path) {
            for (String[] fix : PATH_FIXES) {
                if (path.startsWith(fix[0])) {
                    return fix[1] + path.substring(fix[0].length());
                }
            }
            return null;
        }
    }
}
