# SmartSpace (com.kreativekoala.smartspace) Play releases

## 1.1.0 (9), 2026-11-16, production at 20%
- Built from origin/main 627d5e5ac44d410b7e3ea2f41137c7c9595407a4 (tag play/1.1.0-9), `android/`, `:app:bundleRelease`, unminified.
- AAB sha256 2a13bb122db656904cc08f940acbc240f9cdfca2d4fc9fdc0d6493f86d7bf0a9
- Unit tests: 6 pass. Lint: 19 errors, all pre-existing (MediaStore.Downloads API 29 in DownloadCleanupService, media3 opt-in in VideoCompressionService); not touched by this release.
- Track after commit: production 1.1.0 [9] inProgress 0.2; 1.0.6 [8] completed.
- Content: 26 translations (cs el fi hu ro sv uk new), per-app language list, paywall percent fix, plurals, Play Billing Library 8.0.0 (Play requires it).
- Rollback: halt the rollout, or re-upload versionCode 8 as the completed release via the Play API.
