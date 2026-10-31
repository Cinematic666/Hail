# Hail Enhanced modernization — audited roadmap

## Baselines (2026-10-31)
- Source `main`: `67a43c8ea7d43eddf51d840f3615b97ce6079c9b` (read only).
- Enhanced.4 checkpoint: `f467ce36b305fbba42e4ff292ce2ffa1bf4a90ac` (read only).
- New branch: `enhanced/roadmap-v1.11.0`.
- First build: `1.11.0-enhanced.5`.
- Mandatory applicationId: `com.aistra.hail.enhanced`, parallel to original `com.aistra.hail`.

## Enhanced.5 scope
Integrate upstream changes made since 1.11.0 base: Jailbreak Disable mode, AppManager and HShell, Settings UI, additional strings and translations. Preserve Enhanced's tag editor, reorder, category bulk assignment and JSON migration. Add typed freeze-operation results and tests as an isolated future-facing foundation, with no privileged behavior changes to Enhanced's freeze path. Tests and build are required; release is not approved without CI and device acceptance.

## Verified technical debt
- `AppManager.isAppFrozen` uses current global mode; cannot reliably infer the historical freeze method.
- `AppManager.setListFrozen` returns string/null rather than per-application verified status.
- `HFiles.write` is not an atomic multi-file transaction.
- `AutoFreezeService` and the WorkManager flow need lifecycle/health diagnosis on device.
- `AppInfo` is keyed only by packageName; multi-user apps need (packageName,userId).

## Ordered implementation
1. FreezeController, backend adapters, bounded operation log, verification and recovery journal. No auto-migrations without user confirmation.
2. Discovery v1: current-user inventory, installed/hidden/disabled flags and invalidation.
3. Backup v2: versioned manifest, per-entry hashes and size limits, selective export/import, preview, merge/replace, crash recovery; keep v1 import.
4. Freeze Profiles: source backend tracking per app, tag membership conflict detection, protected packages and safe mode switching.
5. RuleEngine: opt-in triggers, cooldown, idempotency, bounded WorkManager jobs and audit.
6. Shortcut Manager: stable IDs, category/profile actions built upon existing HailApi/HShortcuts.
7. Discovery v2: user profiles, work profiles and supported OEM clones where access is permitted.
8. Instrumented tests and staged real-device acceptance.

## Quality gates
- `:app:testDebugUnitTest`, `:app:assembleRelease`, lint when possible.
- Validate installation beside original and no mutation to main or Enhanced.4.
- JVM tests for domain models, backup, assignments and rule conflicts.
- Android integration tests for Storage Access Framework, PackageManager, ShortcutManager and WorkManager.
- Physical tests by Android/backend variant before claiming root/system behavior works.
- Never automatically restore system permissions, Device Owner role or persistent freeze status from a backup.
- Ship APK only after successful CI, report its signer and signing-key continuity.
