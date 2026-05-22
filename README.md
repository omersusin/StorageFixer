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
| Install listener | Listens for `PACKAGE_ADDED` and `PACKAGE_REPLACED` |
| Smart detection | Only fixes apps with missing directories |
| 10-second delay | Waits for `vold` to fail before applying fix |
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

Device boots → BootReceiver fires → Wait for FUSE + vold delay → Scan all apps → Fix missing directories → Force stop apps

New app installed → PackageReceiver fires → Wait 10 seconds → Fix directories if missing → Force stop app

App shares file → Xposed hook intercepts FileProvider → Rewrite path → Valid content:// URI generated → File opens normally

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
5. Grant **notification permission**
6. (Optional) Enable the **Xposed module in LSPosed**

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

---

## App Features

| Feature | Description |
|--------|-------------|
| Fix All | Manually scan and fix broken apps |
| Diagnose | Run detailed diagnostics |
| Copy Logs | Copy logs to clipboard |
| Clear Logs | Clear log history |
| Auto-fix | Runs automatically on boot and install |

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
4. Broadcast-based detection — uses `PACKAGE_ADDED`  
5. Xposed hook — fixes FileProvider without permission hacks

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

---

## FAQ

**Do I need LSPosed?**  
Only if you experience the FileProvider bug.

**Will this break apps?**  
No. StorageFixer only fixes missing directories.

**Does it survive reboot?**  
Yes. It runs again on boot.

**Which ROMs are affected?**  
AOSP-based ROMs built on Android 16 QPR1 or newer.

---

## Credits

- libsu — topjohnwu  
- XposedBridge — rovo89

---

## License

MIT License
