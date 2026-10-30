# Hail Enhanced — v1.11.0 test build

Unofficial parallel-install test build based on upstream Hail v1.11.0.

## What changed

- Adds a tag manager to the home-screen overflow menu.
- Long-pressing a tab also opens the tag manager.
- Reorders custom tags using drag-and-drop or up/down controls.
- Keeps the built-in Default tag pinned first.
- Renaming keeps the existing tag ID, preserving app assignments.
- Deleting a tag removes only that tag ID from app assignments. Apps with other tags remain on the home list.
- Uses the separate application ID `com.aistra.hail.enhanced`; the original Hail app can remain installed.

## Build

Run:

```sh
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

The release variant uses the Gradle debug signing key when no private signing configuration is supplied. This is a test build, not an official signed release.

## Upstream and license

Based on aistra0528/Hail v1.11.0, commit `8bd6f4db2156e0a73bc995740abfa637ec2c2ee0`.
Licensed under GPL-3.0. See [LICENSE](LICENSE).


## Tag backup and migration

The Tag backup menu exports a ZIP containing the original Hail-compatible `tags.json` and `apps.json` files plus a versioned manifest. Import accepts this format and legacy Hail archives containing those two files at the archive root or under `v1/`.

To migrate from the original Hail installation without modifying it, use a root-capable file manager to copy:

- `/data/user/0/com.aistra.hail/files/v1/tags.json`
- `/data/user/0/com.aistra.hail/files/v1/apps.json`

Place both files in a ZIP archive (at the root or inside a `v1/` folder), then choose **Home → Tag backup → Import tag configuration** in Hail Enhanced.

Only tag definitions and app-to-tag assignments are imported. Existing pin and whitelist flags are preserved; frozen states, permissions, working mode, and other preferences are not imported or changed. Apps already in Enhanced but absent from the archive are retained; assignments to tags that no longer exist are moved to Default.
