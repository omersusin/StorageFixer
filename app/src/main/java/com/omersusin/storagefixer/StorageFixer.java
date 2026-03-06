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

    private static final String[] SUBDIRS = {
        "cache", "files", "no_backup", "shared_prefs",
        "databases", "code_cache"
    };

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
        return fixPackage(pkg, false);
    }

    public static FixResult fixPackage(String pkg, boolean diagnose) {
        FixResult r = new FixResult(pkg);

        if (diagnose) {
            FixerLog.divider();
            FixerLog.i("🔍 DIAGNOSING: " + pkg);
            logPackageInfo(pkg);
        }

        // Fix Android/data/<pkg>
        String dataPath = BASE + "/data/" + pkg;
        if (diagnose) logDirState("BEFORE", dataPath);
        r.dataOk = fixDir(dataPath, diagnose);
        fixSubDirs(dataPath, diagnose);
        if (diagnose) logDirState("AFTER", dataPath);

        // Fix Android/obb/<pkg>
        String obbPath = BASE + "/obb/" + pkg;
        if (diagnose) logDirState("BEFORE", obbPath);
        r.obbOk = fixDir(obbPath, diagnose);
        if (diagnose) logDirState("AFTER", obbPath);

        // Fix Android/media/<pkg>
        String mediaPath = BASE + "/media/" + pkg;
        if (diagnose) logDirState("BEFORE", mediaPath);
        r.mediaOk = fixDir(mediaPath, diagnose);
        fixSubDirs(mediaPath, diagnose);
        if (diagnose) logDirState("AFTER", mediaPath);

        // Verify write test
        if (diagnose) {
            r.writeTestOk = testWrite(dataPath);
            FixerLog.i("✏️ Write test " + dataPath + ": "
                    + (r.writeTestOk ? "PASS" : "FAIL"));

            boolean mediaWrite = testWrite(mediaPath);
            FixerLog.i("✏️ Write test " + mediaPath + ": "
                    + (mediaWrite ? "PASS" : "FAIL"));
        }

        r.success = r.dataOk && r.obbOk && r.mediaOk;

        FixerLog.i((r.success ? "✓" : "✗") + " " + pkg
                + " [data:" + (r.dataOk ? "ok" : "fail")
                + " obb:" + (r.obbOk ? "ok" : "fail")
                + " media:" + (r.mediaOk ? "ok" : "fail") + "]");

        if (diagnose) FixerLog.divider();
        return r;
    }

    private static void logPackageInfo(String pkg) {
        Shell.Result uidRes = Shell.cmd(
                "dumpsys package " + pkg + " | grep userId= | head -1").exec();
        for (String line : uidRes.getOut()) {
            FixerLog.d("  UID: " + line.trim());
        }

        Shell.Result pathRes = Shell.cmd("pm path " + pkg).exec();
        for (String line : pathRes.getOut()) {
            FixerLog.d("  Path: " + line.trim());
        }

        Shell.Result permRes = Shell.cmd(
                "dumpsys package " + pkg
                + " | grep -A 50 'granted=true' | head -30").exec();
        if (!permRes.getOut().isEmpty()) {
            FixerLog.d("  Granted permissions:");
            for (String line : permRes.getOut()) {
                String t = line.trim();
                if (t.contains("storage") || t.contains("STORAGE")
                        || t.contains("READ_MEDIA") || t.contains("MANAGE")) {
                    FixerLog.d("    " + t);
                }
            }
        }

        Shell.Result mountRes = Shell.cmd(
                "cat /proc/mounts | grep 'emulated' | head -5").exec();
        FixerLog.d("  Relevant mounts:");
        for (String line : mountRes.getOut()) {
            FixerLog.d("    " + line.trim());
        }
    }

    private static void logDirState(String label, String path) {
        FixerLog.d("  [" + label + "] " + path);

        Shell.Result exists = Shell.cmd(
                "[ -d '" + path + "' ] && echo EXISTS || echo MISSING").exec();
        String status = exists.getOut().isEmpty() ? "UNKNOWN"
                : exists.getOut().get(0);
        FixerLog.d("    Status: " + status);

        if ("MISSING".equals(status)) return;

        Shell.Result lsRes = Shell.cmd("ls -laZd '" + path + "'").exec();
        for (String line : lsRes.getOut()) {
            FixerLog.d("    Perms: " + line.trim());
        }

        Shell.Result statRes = Shell.cmd(
                "stat -c 'mode=%a owner=%U:%G' '" + path + "' 2>/dev/null"
                + " || stat '" + path + "' 2>/dev/null | head -4").exec();
        for (String line : statRes.getOut()) {
            FixerLog.d("    Stat: " + line.trim());
        }

        Shell.Result conRes = Shell.cmd(
                "ls -Zd '" + path + "' | awk '{print $1}'").exec();
        for (String line : conRes.getOut()) {
            FixerLog.d("    SEctx: " + line.trim());
        }

        Shell.Result subRes = Shell.cmd(
                "ls -laZ '" + path + "/' 2>/dev/null | head -20").exec();
        if (!subRes.getOut().isEmpty()) {
            FixerLog.d("    Contents:");
            for (String line : subRes.getOut()) {
                FixerLog.d("      " + line.trim());
            }
        }
    }

    private static boolean fixDir(String path, boolean diagnose) {
        String createCmd = String.join(" && ",
                "mkdir -p '" + path + "'",
                "chmod " + DIR_PERM + " '" + path + "'",
                "chown " + OWNER + " '" + path + "'",
                "chcon " + SECTX + " '" + path + "'");

        if (diagnose) FixerLog.d("  CMD: " + createCmd);

        Shell.Result res = Shell.cmd(createCmd).exec();
        if (!res.isSuccess()) {
            for (String e : res.getErr()) FixerLog.e("  ERR: " + e);
            return false;
        }

        String recursiveCmd = String.join("; ",
                "find '" + path + "' -type d -exec chmod " + DIR_PERM + " {} + 2>/dev/null",
                "find '" + path + "' -type f -exec chmod " + FILE_PERM + " {} + 2>/dev/null",
                "chown -R " + OWNER + " '" + path + "' 2>/dev/null",
                "chcon -R " + SECTX + " '" + path + "' 2>/dev/null");

        Shell.cmd(recursiveCmd).exec();

        if (diagnose) FixerLog.d("  Recursive fix applied: " + path);
        return true;
    }

    private static boolean fixDir(String path) {
        return fixDir(path, false);
    }

    private static void fixSubDirs(String parentPath, boolean diagnose) {
        for (String sub : SUBDIRS) {
            String subPath = parentPath + "/" + sub;
            String cmd = String.join(" && ",
                    "mkdir -p '" + subPath + "'",
                    "chmod " + DIR_PERM + " '" + subPath + "'",
                    "chown " + OWNER + " '" + subPath + "'",
                    "chcon " + SECTX + " '" + subPath + "'");
            Shell.cmd(cmd).exec();
            if (diagnose) FixerLog.d("  Subdir: " + sub + " created");
        }
    }

    private static boolean testWrite(String path) {
        String testFile = path + "/.storagefixer_test";
        Shell.Result w = Shell.cmd(
                "echo test > '" + testFile + "' && rm '" + testFile + "'").exec();
        return w.isSuccess();
    }

    public static void forceStopPackage(String pkg) {
        Shell.cmd("am force-stop '" + pkg + "'").exec();
        FixerLog.i("⏹ Force stopped: " + pkg);
    }

    public static void diagnosePackage(Context ctx, String pkg) {
        FixerLog.divider();
        FixerLog.i("🏥 FULL DIAGNOSIS FOR: " + pkg);

        Shell.Result sdkRes = Shell.cmd("getprop ro.build.version.sdk").exec();
        Shell.Result romRes = Shell.cmd("getprop ro.build.display.id").exec();
        Shell.Result kernRes = Shell.cmd("uname -r").exec();
        FixerLog.i("  SDK: " + join(sdkRes.getOut()));
        FixerLog.i("  ROM: " + join(romRes.getOut()));
        FixerLog.i("  Kernel: " + join(kernRes.getOut()));

        Shell.Result fsRes = Shell.cmd(
                "mount | grep emulated | head -3").exec();
        FixerLog.i("  Storage filesystem:");
        for (String line : fsRes.getOut()) {
            FixerLog.i("    " + line.trim());
        }

        Shell.Result magiskRes = Shell.cmd("magisk -v 2>/dev/null").exec();
        Shell.Result ksuRes = Shell.cmd("ksud -v 2>/dev/null || ksu -v 2>/dev/null").exec();
        FixerLog.i("  Magisk: " + join(magiskRes.getOut()));
        FixerLog.i("  KernelSU: " + join(ksuRes.getOut()));

        fixPackage(pkg, true);

        Shell.Result verifyRes = Shell.cmd(
                "run-as " + pkg + " ls -la /storage/emulated/0/Android/data/"
                + pkg + "/ 2>&1 || echo 'run-as failed (normal for release apps)'"
        ).exec();
        FixerLog.i("  App-perspective check:");
        for (String line : verifyRes.getOut()) {
            FixerLog.d("    " + line.trim());
        }

        FixerLog.divider();
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

    private static String join(List<String> list) {
        if (list == null || list.isEmpty()) return "(empty)";
        StringBuilder sb = new StringBuilder();
        for (String s : list) sb.append(s).append(" ");
        return sb.toString().trim();
    }

    public static class FixResult {
        public String packageName;
        public boolean dataOk, obbOk, mediaOk, writeTestOk, success;

        FixResult(String pkg) { this.packageName = pkg; }

        @Override
        public String toString() {
            return (success ? "✓" : "✗") + " " + packageName
                    + " [data:" + (dataOk ? "ok" : "fail")
                    + " obb:" + (obbOk ? "ok" : "fail")
                    + " media:" + (mediaOk ? "ok" : "fail")
                    + " write:" + (writeTestOk ? "ok" : "fail") + "]";
        }
    }
}
