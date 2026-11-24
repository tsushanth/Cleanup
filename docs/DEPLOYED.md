# SmartSpace (com.kreativekoala.smartspace) Play releases

## 1.1.2 (11), 2026-11-23, production at 20%
- Built from origin/main 090112b0b47f29f6646b87860851cd1105ee70ee (tag play/1.1.2-11), `android/`, `:app:bundleRelease`, unminified, jarsigner verified.
- AAB sha256 f6da817fdba7794e6a128e05df7d596647d867bf9a9289dea7a617f6dabea8d7
- Unit tests: 19 pass (StringResourcesTest, NoHardcodedUiStringsTest, ByteFormatterTest, ArchiveErrorMapperTest).
- Device test (Pixel 9 Pro, debug build of the same tree with a side-by-side applicationIdSuffix, per-app language zh-TW, zh-HK, zh-CN, es, en): onboarding, home, settings and paywall render in the right script and wording (zh-TW Traditional/Taiwan terms, zh-HK Hong Kong terms such as 設置/視頻/聯繫人, zh-CN still Simplified), storage sizes show correct units ("105.4 GB"), no FATAL/ANR. No clean/delete/archive action was run; permission prompts were skipped.
- Track after commit: production 1.1.2 [11] inProgress 0.2; 1.1.1 [10] completed.
- Content: Traditional Chinese for Taiwan (zh-TW, script fallback b+zh+Hant) and Hong Kong (zh-HK), per-app language entries (PRs #15, #16). No server changes.
- Rollback: halt the rollout, or re-upload versionCode 10 as the completed release via the Play API.

## 1.1.1 (10), 2026-11-23, production at 20%
- Built from origin/main 82b788093f560c522b7d9219c6479582ed9c4853 (tag play/1.1.1-10), `android/`, `:app:bundleRelease`, unminified.
- AAB sha256 9d15cb127ddf8d04e751f15d2bf0e3646eb8e683f8f14940f45e730353774bc2
- Unit tests: 17 pass (StringResourcesTest, NoHardcodedUiStringsTest, ByteFormatterTest, ArchiveErrorMapperTest); backend `npm test` 2 pass.
- Device test (Pixel 9 Pro, debug build, per-app language es, de, ru): home storage sizes localized (es/de "105,7 GB", ru "105,7 ГБ"), Pro paywall translated, no FATAL/ANR. The archive screens need a Google sign-in and were not opened.
- Track after commit: production 1.1.1 [10] inProgress 0.2; 1.1.0 [9] completed.
- Content: archive tier/status/file-type text and plan sizes from string resources, locale-aware byte sizes (follows the per-app language), localized archive error messages mapped from backend error codes (PRs #11, #13).
- Rollback: halt the rollout, or re-upload versionCode 9 as the completed release via the Play API.

## Backend Lambdas (stack cleanup-archive, us-east-1, account 451115460668), 2026-11-23
- Code from origin/main d1d7368 (PR #11): every archive-*.js error response now carries a stable `code`; English `error` text unchanged.
- All 7 functions share one package. CodeSha256 before: ttrwY/e29BVHGUUkDMMCs2sjgHAh9sH7EKOBAvH1nC0= (LastModified 2026-02-16T20:49:45Z, S3 key 2e3c5a5c308c0577c6eb7a37c0717457 in the SAM managed bucket). After: vgc9iVYFvQDG4y/mxtJBA93yazmCnLuWkWukYVmG5h8= (s3://aws-sam-cli-managed-default-samclisourcebucket-i4ifkabmqxl1/cleanup-archive/cl-d1d7368-be073d895605.zip).
- Package = repo lambdas/ + package*.json + template.yml + the node_modules of the previous live package (dependencies unchanged).
- Deployed with `aws lambda update-function-code` per function, not CloudFormation: `sam` is broken locally and a CloudFormation changeset showed Api (swagger body) and IdentityPoolRoleAttachment modifications beyond the code change, so the stack was not updated. The stack still references the old S3 key; the next stack deploy must be reviewed (changeset first) and will point at its own package.
- Verified by invoking each function with an identity-less event: 400 with `{"error":"Missing user identity","code":"MISSING_IDENTITY"}`. The API itself needs Apple-sign-in Cognito credentials, so it was not called end to end.
- Rollback: `aws lambda update-function-code --function-name <name> --s3-bucket aws-sam-cli-managed-default-samclisourcebucket-i4ifkabmqxl1 --s3-key 2e3c5a5c308c0577c6eb7a37c0717457 --region us-east-1` for each of the 7 functions.

## 1.1.0 (9), 2026-11-16, production at 20%
- Built from origin/main 627d5e5ac44d410b7e3ea2f41137c7c9595407a4 (tag play/1.1.0-9), `android/`, `:app:bundleRelease`, unminified.
- AAB sha256 2a13bb122db656904cc08f940acbc240f9cdfca2d4fc9fdc0d6493f86d7bf0a9
- Unit tests: 6 pass. Lint: 19 errors, all pre-existing (MediaStore.Downloads API 29 in DownloadCleanupService, media3 opt-in in VideoCompressionService); not touched by this release.
- Track after commit: production 1.1.0 [9] inProgress 0.2; 1.0.6 [8] completed.
- Content: 26 translations (cs el fi hu ro sv uk new), per-app language list, paywall percent fix, plurals, Play Billing Library 8.0.0 (Play requires it).
- Rollback: halt the rollout, or re-upload versionCode 8 as the completed release via the Play API.
