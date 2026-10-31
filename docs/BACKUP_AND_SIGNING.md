# Hail Enhanced — permanent signing and full backup

This branch prepares the first permanently signed Enhanced build, \`1.11.0-enhanced.9\`.

## Permanent APK signing

The release build keeps the package ID \`com.aistra.hail.enhanced\`. It reads signing material from these environment variables:

- \`HAIL_SIGNING_KEYSTORE_PATH\`
- \`HAIL_SIGNING_STORE_PASSWORD\`
- \`HAIL_SIGNING_KEY_ALIAS\`
- \`HAIL_SIGNING_KEY_PASSWORD\`

The GitHub Actions workflow decodes \`HAIL_SIGNING_KEYSTORE_BASE64\` into the runner's temporary directory and supplies the remaining values as environment variables. It refuses to build a release APK unless all signing secrets are present. Unit tests still run when secrets have not yet been configured; in that case the signed release step is skipped.

Add the following repository Actions secrets in **Settings → Secrets and variables → Actions**:

- \`HAIL_SIGNING_KEYSTORE_BASE64\`
- \`HAIL_SIGNING_STORE_PASSWORD\`
- \`HAIL_SIGNING_KEY_ALIAS\`
- \`HAIL_SIGNING_KEY_PASSWORD\`

After saving all four secrets, open **Actions → Hail Enhanced v1.11.0-enhanced.9 stable signed build → Run workflow** and select this branch. The workflow first runs tests, then builds and uploads the permanently signed APK.

The generated release certificate SHA-256 fingerprint is:

\`AE:3C:A1:22:8D:75:91:7A:56:1A:A9:8E:FA:4B:59:59:AF:77:09:5E:1D:08:9D:26:67:3B:55:67:AF:A6:48:CB\`

Keep the \`.jks\` file and the secret bundle offline in at least two encrypted backups. Never commit the keystore, passwords, base64 value, or \`signing.properties\`. If the key is lost, Android will not accept future updates signed with a different key.

## One-time migration from the old debug-signed build

Enhanced.7 and Enhanced.8 were signed with different temporary Android debug certificates. Android cannot update either installation in place with the new permanent certificate. The package ID is intentionally unchanged so all updates after the one-time migration can install normally.

**Do not uninstall the old app until the external migration ZIP has been created and verified.**

### Export the old app's private data

The helper \`tools/export_legacy_hail_backup.py\` uses root access to read the old app's private files and default SharedPreferences. It runs in Termux:

1. Install Python in Termux: \`pkg install python\`.
2. Download \`tools/export_legacy_hail_backup.py\` from this branch to Termux.
3. Grant Termux root access in KernelSU/ReSukiSU.
4. Run:

   \`\`\`sh
   python export_legacy_hail_backup.py --package com.aistra.hail.enhanced
   \`\`\`

   For the original upstream package, use \`--package com.aistra.hail\`.

5. Make the archive visible to Android's file picker:

   \`\`\`sh
   termux-setup-storage
   mv Hail-Enhanced-Migration-Backup.zip ~/storage/shared/Download/
   \`\`\`

6. Keep the ZIP in Download or another location outside the app's private storage. The script checks the ZIP and prints its SHA-256 hash.

The exporter includes the app's \`files/\` directory (including \`v1/tags.json\` and \`v1/apps.json\`) and supported values from the default SharedPreferences XML. It deliberately excludes working mode, biometric login, auto-freeze activation and preference keys that look like credentials.

### Install the permanently signed build

1. Confirm the migration ZIP is accessible outside the app.
2. If Hail is configured as Device Owner, stop before uninstalling and review that transition separately. Removing Device Owner can affect Android's app-hidden state; the portable backup does not preserve or replay those system states. Do not remove the owner role until the current freeze state has been checked.
3. Uninstall the old debug-signed \`com.aistra.hail.enhanced\` app.
4. Install the permanently signed Enhanced.9 APK.
5. Open Hail Enhanced and choose **Home → three-dot menu → Backup and restore → Restore full app backup**.
6. Select the migration ZIP and confirm the restore.
7. Re-select the working mode and grant the required Android, Root, Shizuku/Dhizuku, Usage Access or notification permissions again.

Do not delete the migration ZIP until the new installation has been checked.

## Full Backup & Restore

The in-app full backup is a regular ZIP archive and is **not encrypted**. It contains:

- Hail's private \`files/\` data, including tags and tracked-app data.
- Tracked app assignments, pin and whitelist flags.
- Supported default SharedPreferences values, including appearance, sorting, filters, shortcut action and auto-freeze preferences that are safe to restore.
- A versioned manifest with package/version metadata and entry counts.

Restore validates the complete archive before writing anything. It rejects unsupported schemas, duplicate entries, unsafe paths, oversized files, invalid tag references and protected preferences. App files are restored by merging archive paths; files absent from the archive are kept. Tags, tracked apps and saved preference values are restored from the archive. A failed restore makes a best-effort rollback of changed files, preferences, tags and app assignments.

The following are **not** restored:

- Working mode and device-bound Root/Shizuku/Dhizuku/Device Owner grants.
- Biometric login (the Android Keystore key cannot be exported).
- Auto-freeze activation (the service is left disabled until permissions are checked).
- Android permission grants, launcher-pinned shortcuts, WorkManager's queued delayed freeze/unfreeze jobs and system-level freeze states.

The restore does not freeze or unfreeze packages. After restore, configure the working mode and permissions manually. The system's current frozen states are left untouched. Pending delayed freeze/unfreeze jobs are not copied; schedule them again if needed. Keep backup ZIPs in a private location because they include the tracked package list and saved app preferences.

The existing tag-only ZIP export/import remains available for compatibility with original Hail archives.

## Local signing

For local builds, a root-level ignored \`signing.properties\` may contain:

\`\`\`properties
storeFile=/absolute/path/to/Hail-Enhanced-release.jks
storePassword=...
keyAlias=hail-enhanced-release
keyPassword=...
\`\`\`

Never commit this file. Set \`HAIL_REQUIRE_RELEASE_SIGNING=true\` to make Gradle fail rather than silently falling back to the debug key.
