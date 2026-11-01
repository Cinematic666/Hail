# Hail Enhanced — v1.11.0-enhanced.10 development

Unofficial Enhanced build based on upstream Hail v1.11.0. This branch prepares permanent APK signing and full app-data backup/restore. The application ID remains `com.aistra.hail.enhanced`.

## What changed

- Shows home-screen actions directly in the three-dot overflow menu; only backup export/import stay grouped in a Backup and restore submenu.
- Adds per-category bulk app management: select apps from a source category, then add them to the current category or move them out of the source category.
- Supports app search and select-all for the currently filtered source-category list.

- Opens the tag manager by long-pressing a category tab; the Default tab remains visible for access when it is the only category.
- Removes the redundant settings gear next to the category tabs.
- Reorders custom tags using drag-and-drop or up/down controls.
- Keeps the built-in Default tag pinned first.
- Renaming keeps the existing tag ID, preserving app assignments.
- Deleting a tag removes only that tag ID from app assignments. Apps with other tags remain on the home list.
- Uses the separate application ID `com.aistra.hail.enhanced`; the original Hail app can remain installed.

## Build

The separate debug package is `com.aistra.hail.enhanced.debug`, labelled **Hail Enhanced Test**. It can be installed beside an existing Enhanced release. Build it with `./gradlew :app:testDebugUnitTest :app:assembleDebug`. This development build does not imply that all modernization features have passed real-device validation. See [Enhanced.10 testing notes](docs/ENHANCED_10_TESTING.md).

Run:

```sh
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

For local builds, Gradle uses a debug key unless a local `signing.properties` or `HAIL_SIGNING_*` environment variables are supplied. GitHub Actions only builds/uploads the release APK when all permanent signing secrets are configured; otherwise it runs unit tests and skips release packaging. See [Backup and signing](docs/BACKUP_AND_SIGNING.md).

## Upstream and license

Based on aistra0528/Hail v1.11.0, commit `8bd6f4db2156e0a73bc995740abfa637ec2c2ee0`.
Licensed under GPL-3.0. See [LICENSE](LICENSE).


## Tag backup and migration

The Tag backup menu exports a ZIP containing the original Hail-compatible `tags.json` and `apps.json` files plus a versioned manifest. Import accepts this format and legacy Hail archives containing those two files at the archive root or under `v1/`.

To migrate from the original Hail installation without modifying it, use a root-capable file manager to copy:

- `/data/user/0/com.aistra.hail/files/v1/tags.json`
- `/data/user/0/com.aistra.hail/files/v1/apps.json`

Place both files in a ZIP archive (at the root or inside a `v1/` folder), then choose **Home → three-dot menu → Backup and restore → Import tag configuration** in Hail Enhanced.

Only tag definitions and app-to-tag assignments are imported. For apps already tracked in Enhanced, existing pin and whitelist flags are preserved; newly imported app entries restore those flags from the backup. Frozen states, permissions, working mode, and other preferences are not imported or changed. Apps already in Enhanced but absent from the archive are retained; assignments to tags that no longer exist are moved to Default.

## Enhanced.5 — upstream baseline sync

This release integrates the upstream `main` snapshot `67a43c8ea7d43eddf51d840f3615b97ce6079c9b` into a separate development branch without changing `main` or Enhanced.4. It retains the upstream Jailbreak Disable mode, Settings updates, and translations together with Enhanced tag editing, assignments, backup and parallel-install package name. A new typed operation report is a standalone model only; it is **not yet wired to Root/Shizuku or used for freeze actions**. The real-device safety gate still applies.

## Enhanced.6 — modern category manager

- Tag editor opened by long-pressing any category tab; the Default tab remains visible even when it is the only category.
- Material bottom sheets with readable cards, counts and one contextual action menu.
- Drag handle for reorder; fixed Default category remains pinned.
- Tap a category to choose and bulk-add/move apps from another category.
- **All changes are staged together**: Save commits tag definitions and assignments; Back/Cancel/swipe discards the entire session.
- No working-mode changes or freeze commands are executed from the redesigned category editor.
- Home-screen overflow actions are visible immediately; only backup import/export are nested.
- Preserves existing tag IDs, legacy JSON format and backup import/export.

## Enhanced.9 — permanent signing and full backup

- Adds full app backup/restore for app-private files, tracked apps, tags, pin/whitelist flags and supported preferences.
- Adds archive schema validation, size limits, ZIP path traversal protection, duplicate-entry rejection and best-effort rollback.
- Excludes working mode, biometric login and auto-freeze activation from portable preferences; Android permissions and system freeze states are never replayed.
- Adds a root-assisted one-time migration exporter for existing debug-signed installs.
- Adds permanent release signing through GitHub Actions secrets. The release workflow refuses to fall back to a debug certificate.
- Keeps the application ID unchanged for future stable updates. The first transition from Enhanced.7/.8 requires an external migration archive and one uninstall/reinstall because the earlier builds used different temporary debug certificates.

See [docs/BACKUP_AND_SIGNING.md](docs/BACKUP_AND_SIGNING.md) for the migration procedure, secret names and backup scope.

## Enhanced.8 — compact navigation

- Removes the gear button next to category tabs.
- Long-pressing any category tab opens the category editor; the Default tab stays visible when it is the only category.
- Shows freeze, import and export actions directly after opening the three-dot menu.
- Keeps only tag-configuration backup export/import inside the Backup and restore submenu.
- Preserves the existing action handlers and staged tag-edit save/cancel behavior.

Testing: :app:testDebugUnitTest :app:assembleRelease.

## Enhanced.7 — save hardening

- Preserve the selected category by its stable tag ID when edited categories are reordered.
- Preserve apps added by another operation while a tag-editing session was open; do not silently strip their assignments.
- Remove from the home list only apps newly left without tags by this edit, not unrelated pre-existing untagged entries.
- Save behavior remains staged; this is an isolated development build, not a stable release.
