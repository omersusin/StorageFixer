package com.omersusin.storagefixer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView statusView, logView;
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusView = findViewById(R.id.statusView);
        logView = findViewById(R.id.logView);
        scrollView = findViewById(R.id.scrollView);
        Button btnFix = findViewById(R.id.btnFixAll);
        Button btnDiagnose = findViewById(R.id.btnDiagnose);
        Button btnRefresh = findViewById(R.id.btnRefreshLog);
        Button btnCopy = findViewById(R.id.btnCopyLog);
        Button btnClear = findViewById(R.id.btnClearLog);

        // Notification permission (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }

        btnFix.setOnClickListener(v -> {
            statusView.setText("⏳ Fixing all apps...");
            Intent svc = new Intent(this, FixerService.class);
            svc.setAction("MANUAL_SCAN");
            startForegroundService(svc);
            v.postDelayed(this::refreshLog, 5000);
        });

        btnDiagnose.setOnClickListener(v -> showDiagnoseDialog());

        btnRefresh.setOnClickListener(v -> refreshLog());

        btnCopy.setOnClickListener(v -> {
            if (FixerLog.copyToClipboard(this)) {
                Toast.makeText(this, "📋 Logs copied!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "❌ Copy failed", Toast.LENGTH_SHORT).show();
            }
        });

        btnClear.setOnClickListener(v -> {
            FixerLog.clear();
            logView.setText("Log cleared.");
        });

        checkStatus();
        refreshLog();
    }

    private void showDiagnoseDialog() {
        EditText input = new EditText(this);
        input.setHint("org.telegram.messenger");
        input.setSingleLine(true);
        input.setPadding(48, 32, 48, 32);

        new AlertDialog.Builder(this)
                .setTitle("🔍 Diagnose Package")
                .setMessage("Enter the package name to diagnose.\n\nThis will run a full diagnostic with detailed logs.")
                .setView(input)
                .setPositiveButton("Diagnose", (d, w) -> {
                    String pkg = input.getText().toString().trim();
                    if (pkg.isEmpty()) return;
                    statusView.setText("🔍 Diagnosing " + pkg + "...");
                    new Thread(() -> {
                        StorageFixer.diagnosePackage(this, pkg);
                        runOnUiThread(() -> {
                            statusView.setText("✅ Diagnosis complete for " + pkg);
                            refreshLog();
                        });
                    }).start();
                })
                .setNeutralButton("Telegram", (d, w) -> {
                    statusView.setText("🔍 Diagnosing Telegram...");
                    new Thread(() -> {
                        StorageFixer.diagnosePackage(this,
                                "org.telegram.messenger");
                        runOnUiThread(() -> {
                            statusView.setText("✅ Telegram diagnosis complete");
                            refreshLog();
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLog();
    }

    private void checkStatus() {
        new Thread(() -> {
            boolean root = StorageFixer.isRootAvailable();
            boolean fuse = StorageFixer.isFuseReady();
            String s = "Root: " + (root ? "✅" : "❌")
                    + "   Storage: " + (fuse ? "✅" : "❌")
                    + "\nAPI: " + Build.VERSION.SDK_INT
                    + "   Android: " + Build.VERSION.RELEASE;
            runOnUiThread(() -> statusView.setText(s));
        }).start();
    }

    private void refreshLog() {
        List<String> logs = FixerLog.readAll();
        StringBuilder sb = new StringBuilder();
        if (logs.isEmpty()) {
            sb.append("No logs yet.\n\n");
            sb.append("Auto-fix runs on:\n");
            sb.append("• Boot completed\n");
            sb.append("• App install / update\n\n");
            sb.append("Tap 'Fix All' for manual scan.\n");
            sb.append("Tap 'Diagnose' for detailed analysis.");
        } else {
            int start = Math.max(0, logs.size() - 500);
            for (int i = start; i < logs.size(); i++)
                sb.append(logs.get(i)).append("\n");
        }
        logView.setText(sb.toString());
        scrollView.post(() ->
                scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }
}
