package com.omersusin.storagefixer;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FixerLog {

    private static final String TAG = "StorageFixer";
    private static final String LOG_FILE = "storagefixer.log";
    private static final long MAX_SIZE = 1024 * 1024; // 1 MB
    private static File logFile;
    private static Context appContext;

    public static void init(Context context) {
        appContext = context.getApplicationContext();
        logFile = new File(context.getFilesDir(), LOG_FILE);
    }

    public static void i(String msg) {
        Log.i(TAG, msg);
        write("INFO", msg);
    }

    public static void e(String msg) {
        Log.e(TAG, msg);
        write("ERROR", msg);
    }

    public static void w(String msg) {
        Log.w(TAG, msg);
        write("WARN", msg);
    }

    public static void d(String msg) {
        Log.d(TAG, msg);
        write("DEBUG", msg);
    }

    public static void divider() {
        write("----", "════════════════════════════════════════");
    }

    private static synchronized void write(String level, String msg) {
        if (logFile == null) return;
        try {
            if (logFile.exists() && logFile.length() > MAX_SIZE) {
                File old = new File(logFile.getParentFile(), LOG_FILE + ".old");
                if (old.exists()) old.delete();
                logFile.renameTo(old);
                logFile = new File(old.getParentFile(), LOG_FILE);
            }
            String ts = new SimpleDateFormat("MM-dd HH:mm:ss.SSS",
                    Locale.US).format(new Date());
            try (PrintWriter pw = new PrintWriter(
                    new FileWriter(logFile, true))) {
                pw.printf("[%s] %s: %s%n", ts, level, msg);
            }
        } catch (IOException e) {
            Log.e(TAG, "Log write failed", e);
        }
    }

    public static List<String> readAll() {
        List<String> lines = new ArrayList<>();
        if (logFile == null || !logFile.exists()) return lines;
        try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = br.readLine()) != null) lines.add(line);
        } catch (IOException e) {
            Log.e(TAG, "Log read failed", e);
        }
        return lines;
    }

    public static String readAllAsString() {
        List<String> lines = readAll();
        StringBuilder sb = new StringBuilder();
        for (String line : lines) sb.append(line).append("\n");
        return sb.toString();
    }

    public static boolean copyToClipboard(Context ctx) {
        try {
            String text = readAllAsString();
            if (text.isEmpty()) text = "No logs.";
            ClipboardManager cm = (ClipboardManager)
                    ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("StorageFixer Log", text));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Clipboard copy failed", e);
            return false;
        }
    }

    public static void clear() {
        if (logFile != null && logFile.exists()) logFile.delete();
    }
}
