package com.omersusin.storagefixer;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages the list of apps that should be skipped during fix operations.
 * Apps in this list will not have their FUSE directories or AppOps modified.
 *
 * Persisted via SharedPreferences as a Set of package names.
 */
public class IgnoredAppsManager {

    private static final String PREFS_NAME = "ignored_apps_prefs";
    private static final String KEY_IGNORED = "ignored_packages";

    private IgnoredAppsManager() {}

    /**
     * Returns an unmodifiable set of currently ignored package names.
     */
    public static Set<String> getIgnoredApps(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> stored = prefs.getStringSet(KEY_IGNORED, null);
        if (stored == null) return Collections.emptySet();
        // Return a copy -- getStringSet may return a reference that shouldn't be modified
        return Collections.unmodifiableSet(new HashSet<>(stored));
    }

    /**
     * Checks whether a specific package is in the ignored list.
     */
    public static boolean isIgnored(Context context, String packageName) {
        return getIgnoredApps(context).contains(packageName);
    }

    /**
     * Adds a package to the ignored list.
     */
    public static void addIgnoredApp(Context context, String packageName) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> current = new HashSet<>(getIgnoredApps(context));
        current.add(packageName);
        prefs.edit().putStringSet(KEY_IGNORED, current).apply();
    }

    /**
     * Removes a package from the ignored list.
     */
    public static void removeIgnoredApp(Context context, String packageName) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> current = new HashSet<>(getIgnoredApps(context));
        current.remove(packageName);
        prefs.edit().putStringSet(KEY_IGNORED, current).apply();
    }

    /**
     * Sets the ignored state for a package.
     */
    public static void setIgnored(Context context, String packageName, boolean ignored) {
        if (ignored) {
            addIgnoredApp(context, packageName);
        } else {
            removeIgnoredApp(context, packageName);
        }
    }

    /**
     * Replaces the entire ignored set at once (used for bulk updates).
     */
    public static void setIgnoredApps(Context context, Set<String> packages) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putStringSet(KEY_IGNORED, new HashSet<>(packages)).apply();
    }
}
