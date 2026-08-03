# StorageFixer

**Fixes Android 16 QPR1+ storage permission bugs on AOSP-based ROMs.**

[![Build](https://github.com/omersusin/StorageFixer/actions/workflows/build.yml/badge.svg)](https://github.com/omersusin/StorageFixer/actions/workflows/build.yml)

![GitHub Downloads](https://img.shields.io/github/downloads/omersusin/StorageFixer/total.svg)

---

## The Problem

On AOSP-based ROMs built on **Android 16 QPR1+**, the system fails to properly create or set permissions on scoped storage directories during app installation:

- `/storage/emulated/0/Android/data/<package>/`
- `/storage/emulated/0/Android/obb/<package>/`
- `/storage/emulated/0/Android/media/<package>/`

This causes apps like **PUBG**, **Telegram**, **WhatsApp**, and many others to fail with storage access errors such as:

- "read/write permission error"
- "Unable to create directory"
- "You don't have applications that can handle the file type"

### Root Cause

Android 16 QPR1+ uses **f2fs bind-mounts** for `Android/data` and `Android/obb` directories instead of routing them through FUSE.

This causes two issues:

1. **Storage Directory Bug**

`vold` (Volume Daemon) fails to create app directories with correct permissions due to missing SELinux policies or broken FUSE passthrough patches.

2. **FileProvider Bug**

`FileProvider.getUriForFile()` internally calls `File.getCanonicalPath()`, which resolves the f2fs bind-mount to the lower filesystem path:

`/data/media/0/...`

instead of the expected FUSE path:

`/storage/emulated/0/...`

This causes a silent `IllegalArgumentException`, preventing apps from sharing files via `content://` URIs.

---

## How It Works

StorageFixer is a **hybrid solution** — a root Android app combined with an LSPosed/Xposed module.

### Root Service (Storage Directory Fix)

| Feature | Description |
|--------|-------------|
| Boot-time scan | Scans all 3rd-party apps on boot, fixes missing directories |
| Persistent install watcher | `FixerService` stays running in the foreground after the boot scan and dynamically registers a receiver for `PACKAGE_ADDED`, `PACKAGE_REPLACED`, and `DOWNLOAD_COMPLETE` — more reliable than a static manifest receiver, which could get killed before it ever fired |
| Whitelist mode | Optionally restrict fixes to a chosen list of apps instead of every app on the device — useful if you only want a handful of apps fixed |
| Smart detection | Only fixes apps with missing directories |
| 10-second delay | Waits for `vold` to fail before applying fix |
| Guarded root creation | `safeMkdir()` refuses to create new top-level folders directly under a storage root (e.g. the sdcard root) unless they already exist; existing and nested directories are unaffected |
| App UID ownership | Sets directory ownership to the app's actual UID |
| Force stop | Force stops fixed apps so they pick up new permissions |
| MediaProvider rescan | Triggers volume rescan after fixes |

### Xposed Module (FileProvider Fix)

| Feature | Description |
|--------|-------------|
| FileProvider hook | Intercepts `FileProvider.getUriForFile()` |
| Path rewriting | Rewrites bind-mount paths back to FUSE paths |
| Zero permission changes | Does not touch runtime permissions or `appops` |
| Scoped | Only hooks apps you select in LSPosed |

### Fix Flow

Device boots → `FixerService` starts → Waits for FUSE + vold delay → Scans all apps → Fixes missing directories → Force stops fixed apps → Registers a package receiver and keeps running in the foreground, watching for new installs/updates

New app installed or updated → Package receiver (running inside `FixerService`) fires → Fixes directories if missing, respecting whitelist mode if enabled → Force stops the app

App shares a file → Xposed hook intercepts FileProvider → Rewrite path → Valid `content://` URI generated → File opens normally

---

## Installation

### Requirements

- Android 14–16 (API 34–36)
- Root access (Magisk or KernelSU)
- LSPosed (optional)

### Steps

1. Download the latest APK from **Releases**
2. Install the APK
3. Open StorageFixer and grant **root access**
4. Disable **battery optimization**
5. Grant **notification permission** (the persistent foreground notification is what lets the app catch new installs instantly)
6. (Optional) Enable **whitelist mode** if you only want specific apps fixed
7. (Optional) Enable the **Xposed module in LSPosed**

---

## Supported Apps

| App | Storage Fix | FileProvider Fix |
|-----|-------------|------------------|
| PUBG Mobile | ✅ | — |
| Telegram | ✅ | ✅ |
| WhatsApp | ✅ | ✅ |
| Instagram | ✅ | — |
| MT Manager | ✅ | — |
| YouTube | ✅ | — |
| Reddit | ✅ | — |
| Chrome | ✅ | — |
| 95+ other apps | ✅ | — |

Fixing *every* app isn't always desirable — some users have seen it interfere with apps like Google Photos backups. Use **whitelist mode** to scope fixes to just the apps you need.

---

## App Features

| Feature | Description |
|--------|-------------|
| Fix All | Manually scan and fix broken apps |
| Whitelist Mode | Restrict automatic and manual fixes to a chosen set of apps |
| Diagnose | Run detailed diagnostics |
| Copy Logs | Copy logs to clipboard |
| Clear Logs | Clear log history |
| Auto-fix | Runs on boot and continuously in the background for new installs/updates |

---

## Technical Details

### Why Other Solutions Fail

| Solution | Problem |
|---------|--------|
| Magisk shell modules | `inotifyd` unreliable |
| Boot scripts | Race condition with `vold` |
| `appops` hacks | Breaks Android 14+ Photo Picker |
| Manual fixes | Not automated |

### Why StorageFixer Works

1. Correct timing — waits for `vold` failure
2. Lower filesystem — operates on `/data/media/0`
3. Correct ownership — sets app UID
4. Persistent, broadcast-based detection — `FixerService` keeps a dynamically registered receiver alive instead of relying on a manifest receiver that the system can kill before `PACKAGE_ADDED` fires
5. Guarded directory creation — `safeMkdir()` won't create new folders at the root of a storage volume, only within apps' own directories or paths that already exist
6. Xposed hook — fixes FileProvider without permission hacks
7. Whitelist mode — lets you scope fixes to a handful of apps instead of applying them device-wide

### Mount Configuration (Android 16 QPR1+)

/dev/fuse on /mnt/installer/0/emulated type fuse /dev/block/dm-XX on .../Android/data type f2fs (bind-mount) /dev/block/dm-XX on .../Android/obb type f2fs (bind-mount)

---

## Building

### Prerequisites

- GitHub account
- GitHub Actions enabled

### Steps

1. Fork the repository
2. Push a commit
3. Go to **Actions**
4. Download the built APK artifact

Release builds are signed in CI with a dedicated release keystore (credentials pulled from environment variables/secrets), so APKs published under **Releases** aren't debug-signed.

---

## FAQ

**Do I need LSPosed?**
Only if you experience the FileProvider bug.

**Will this break apps?**
Fixing a specific app's directories shouldn't break it. Fixing *every* app has, in some cases, interfered with apps like Google Photos backups — if you hit that, switch on whitelist mode and only fix the apps you actually need.

**Why does StorageFixer show a persistent notification?**
`FixerService` stays running in the foreground so it can reliably catch new installs and updates the moment they happen. An earlier approach relied on a manifest-registered receiver that Android could kill before it ever fired, which meant the fix only applied after a reboot.

**Does it survive reboot?**
Yes. It runs again on boot.

**Which ROMs are affected?**
AOSP-based ROMs built on Android 16 QPR1 or newer.

---

## Credits

- libsu — topjohnwu
- XposedBridge — rovo89

### Contributors

- Whitelist mode & status bar padding fix — [@Kamjue](https://github.com/Kamjue)
- Persistent install watcher & guarded storage-root creation — [@Gronkdalonka](https://github.com/Gronkdalonka)

---

## License

MIT License
