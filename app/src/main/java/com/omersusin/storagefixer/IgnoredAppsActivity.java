package com.omersusin.storagefixer;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class IgnoredAppsActivity extends AppCompatActivity {

    private AppListAdapter adapter;
    private List<AppEntry> allApps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ignored_apps);

        ListView listView = findViewById(R.id.appListView);
        SearchView searchView = findViewById(R.id.searchView);
        searchView.setQueryHint("Search apps...");

        allApps = loadInstalledApps();
        adapter = new AppListAdapter(allApps);
        listView.setAdapter(adapter);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.filter(newText);
                return true;
            }
        });
    }

    private List<AppEntry> loadInstalledApps() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(0));
        Set<String> ignoredSet = IgnoredAppsManager.getIgnoredApps(this);

        List<AppEntry> entries = new ArrayList<>();
        String selfPkg = getPackageName();

        for (ApplicationInfo app : apps) {
            // Skip system apps and self
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            if (app.packageName.equals(selfPkg)) continue;

            String label = pm.getApplicationLabel(app).toString();
            Drawable icon = pm.getApplicationIcon(app);
            boolean ignored = ignoredSet.contains(app.packageName);

            entries.add(new AppEntry(app.packageName, label, icon, ignored));
        }

        // Sort: ignored apps first, then alphabetically by label
        Collections.sort(entries, (a, b) -> {
            if (a.ignored != b.ignored) return a.ignored ? -1 : 1;
            return a.label.compareToIgnoreCase(b.label);
        });

        return entries;
    }

    private static class AppEntry {
        final String packageName;
        final String label;
        final Drawable icon;
        boolean ignored;

        AppEntry(String packageName, String label, Drawable icon, boolean ignored) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
            this.ignored = ignored;
        }
    }

    private class AppListAdapter extends BaseAdapter {
        private List<AppEntry> displayList;
        private String currentFilter = "";

        AppListAdapter(List<AppEntry> apps) {
            this.displayList = new ArrayList<>(apps);
        }

        void filter(String query) {
            currentFilter = query == null ? "" : query.toLowerCase().trim();
            displayList.clear();
            for (AppEntry app : allApps) {
                if (currentFilter.isEmpty()
                        || app.label.toLowerCase().contains(currentFilter)
                        || app.packageName.toLowerCase().contains(currentFilter)) {
                    displayList.add(app);
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return displayList.size();
        }

        @Override
        public AppEntry getItem(int position) {
            return displayList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(IgnoredAppsActivity.this)
                        .inflate(R.layout.item_ignored_app, parent, false);
            }

            AppEntry entry = getItem(position);

            ImageView icon = convertView.findViewById(R.id.appIcon);
            TextView name = convertView.findViewById(R.id.appName);
            TextView pkg = convertView.findViewById(R.id.appPackage);
            CheckBox checkBox = convertView.findViewById(R.id.appCheckBox);

            icon.setImageDrawable(entry.icon);
            name.setText(entry.label);
            pkg.setText(entry.packageName);

            // Prevent listener from firing during recycling
            checkBox.setOnCheckedChangeListener(null);
            checkBox.setChecked(entry.ignored);

            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                entry.ignored = isChecked;
                IgnoredAppsManager.setIgnored(
                        IgnoredAppsActivity.this, entry.packageName, isChecked);
            });

            // Make the whole row clickable to toggle
            convertView.setOnClickListener(v -> checkBox.toggle());

            return convertView;
        }
    }
}
