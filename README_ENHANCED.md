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
./gradlew testReleaseUnitTest assembleRelease
```

The release variant uses the Gradle debug signing key when no private signing configuration is supplied. This is a test build, not an official signed release.

## Upstream and license

Based on aistra0528/Hail v1.11.0, commit `8bd6f4db2156e0a73bc995740abfa637ec2c2ee0`.
Licensed under GPL-3.0. See [LICENSE](LICENSE).
