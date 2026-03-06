package com.omersusin.storagefixer;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.topjohnwu.superuser.Shell;

import java.util.ArrayList;
import java.util.List;

public class StorageFixer {

    private static final String BASE = "/storage/emulated/0/Android";
    private static final String OWNER = "media_rw:media_rw";
    private static final String SECTX = "u:object_r:media_rw_data_file:s0";
    private static final String DIR_PERM = "777";
    private static final String FILE_PERM = "666";

    public static boolean isRootAvailable() {
        return Shell.getShell().isRoot();
    }

    public static boolean isFuseReady() {
        return Shell.cmd("ls " + BASE + "/").exec().isSuccess();
    }

    public static boolean waitForFuse(int maxSeconds) {
        for (int i = 0; i < maxSeconds; i++) {
            if (isFuseReady()) {
                FixerLog.i("FUSE ready after " + i + "s");
                return true;
            }
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }
        FixerLog.e("FUSE not ready after " + maxSeconds + "s");
        return false;
    }

    public static FixResult fixPackage(String pkg) {
        FixResult r = new FixResult(pkg);
        r.dataOk = fixDir(BASE + "/data/" + pkg);
        r.obbOk = fixDir(BASE + "/obb/" + pkg);
        r.mediaOk = fixDir(BASE + "/media/" + pkg);
        r.success = r.dataOk && r.obbOk && r.mediaOk;
        FixerLog.i((r.success ? "✓" : "✗") + " " + pkg
                + " [data:" + (r.dataOk ? "ok" : "fail")
                + " obb:" + (r.obbOk ? "ok" : "fail")
                + " media:" + (r.mediaOk ? "ok" : "fail") + "]");
        return r;
    }

    private static boolean fixDir(String path) {
        String createCmd = String.join(" && ",
                "mkdir -p '" + path + "'",
                "chmod " + DIR_PERM + " '" + path + "'",
                "chown " + OWNER + " '" + path + "'",
                "chcon " + SECTX + " '" + path + "'");

        Shell.Result res = Shell.cmd(createCmd).exec();
        if (!res.isSuccess()) {
            for (String e : res.getErr()) FixerLog.e("  " + e);
            return false;
        }

        String recursiveCmd = String.join("; ",
                "find '" + path + "' -type d -exec chmod " + DIR_PERM + " {} + 2>/dev/null",
                "find '" + path + "' -type f -exec chmod " + FILE_PERM + " {} + 2>/dev/null",
                "chown -R " + OWNER + " '" + path + "' 2>/dev/null",
                "chcon -R " + SECTX + " '" + path + "' 2>/dev/null");

        Shell.cmd(recursiveCmd).exec();
        return true;
    }

    public static void forceStopPackage(String pkg) {
        Shell.cmd("am force-stop '" + pkg + "'").exec();
        FixerLog.i("⏹ Force stopped: " + pkg);
    }

    public static List<FixResult> fixAll(Context ctx) {
        List<FixResult> results = new ArrayList<>();
        PackageManager pm = ctx.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(0));

        int ok = 0;
        for (ApplicationInfo app : apps) {
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            FixResult r = fixPackage(app.packageName);
            results.add(r);
            if (r.success) ok++;
        }
        FixerLog.i("Scan done: " + ok + "/" + results.size() + " fixed");
        return results;
    }

    public static class FixResult {
        public String packageName;
        public boolean dataOk, obbOk, mediaOk, success;

        FixResult(String pkg) { this.packageName = pkg; }

        @Override
        public String toString() {
            return (success ? "✓" : "✗") + " " + packageName
                    + " [data:" + (dataOk ? "ok" : "fail")
                    + " obb:" + (obbOk ? "ok" : "fail")
                    + " media:" + (mediaOk ? "ok" : "fail") + "]";
        }
    }
}
