# Android READ_MEDIA_IMAGES / READ_MEDIA_VIDEO: why they are still declared, and a migration proposal

Status: proposal only. No code or manifest change is made by this file.

## Why the ops release harness refuses this app today

The Android release harness refuses any build whose merged manifest declares `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO` (owner rule, after the ScribeAI Play rejection).
`android/app/src/main/AndroidManifest.xml` declares both (lines 6-7), plus `READ_EXTERNAL_STORAGE` with `maxSdkVersion="32"` (allowed).

## The permission is genuinely used (so it was NOT removed)

- `ui/onboarding/OnboardingScreen.kt` (~line 47) requests `READ_MEDIA_IMAGES` + `READ_MEDIA_VIDEO` on API 33+.
- `domain/service/PermissionHelper.kt` (`mediaPermissions`, `hasMediaPermission()`); `ui/home/HomeViewModel.kt` branches on it.
- `domain/service/MediaAccessHelper.kt` (`loadAllPhotosFromMediaStore`, `loadAllVideosFromMediaStore`, `loadAllVideoAssetsFromMediaStore`) queries `MediaStore.Images/Video.Media.EXTERNAL_CONTENT_URI` for the whole library.
- Duplicate/similar-photo and large-video detection, compression, vault and archive all work on that library-wide result; deletes go through `MediaStore.createDeleteRequest` (`PhotoAnalysisService.kt`).

Removing the lines alone would make the main scan return nothing on API 33+, so this is a product change, not a manifest tweak.

## What already exists

`MediaAccessHelper` also implements a folder-based fallback with the Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`, persisted URI grant, `loadPhotosFromFolder`), and `HomeViewModel` already uses it when the media permission is absent (`hasMedia || hasSaf`). Its KDoc even says it replaces "the removed READ_MEDIA_* permissions", so the migration was started and the manifest lines were never dropped.

## Options

1. **SAF-only (recommended, rule-compliant).** Delete `READ_MEDIA_IMAGES/VIDEO` from the manifest, delete the permission request in onboarding and `PermissionHelper.mediaPermissions`, delete `scanAllMediaStore()` and the `loadAll*FromMediaStore` functions, and make "choose your Camera/Pictures folder" the one onboarding step. Cost: one extra tap for users, and the folder picker cannot select the storage root or Download on Android 11+ (DCIM and Pictures are selectable). Deletion of SAF documents uses `DocumentsContract.deleteDocument` (write grant is already taken in `saveFolderUri`) instead of `MediaStore.createDeleteRequest`, which needs MediaStore URIs; that path must be re-tested.
2. **Photo Picker (`PickMultipleVisualMedia`) only.** Works for "choose photos to review", but cannot enumerate the whole library, so it cannot power duplicate/similar detection. Useful only as an add-on for one-off imports. Not sufficient alone.
3. **Keep the permissions** and file Google Play's Photo and Video permissions declaration (core-functionality justification). That conflicts with the owner rule (no READ_MEDIA_*), so it needs an explicit owner exception and a harness policy change.

## Release impact

Until option 1 (or an owner exception) lands, the harness cannot release this app. Signing already reads `~/.gradle/gradle.properties` on main (commit 1819d36). The app also has no `FailureReporter`.
