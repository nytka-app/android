# F-Droid and IzzyOnDroid

Nothing here is submitted. Store text and changelogs live in `fastlane/metadata/android/` (`en-US`
and `uk`); both stores read them from the repository. Back to the [README](../../README.md).

| File | What |
|---|---|
| [`io.github.nytka_app.yml`](io.github.nytka_app.yml) | Draft of `metadata/io.github.nytka_app.yml` for F-Droid's `fdroiddata` |
| [`izzyondroid-request.md`](izzyondroid-request.md) | Text of the IzzyOnDroid inclusion request |

## What keeps the `oss` flavor free

`oss` has no Google Play services, Firebase, ML Kit or prebuilt native library (those are `play`
only). Two checks keep it so:

- `./gradlew :app:checkOssNoGoogleServices` fails if the oss release runtime classpath holds one. It is
  part of `./gradlew check`, so CI runs it.
- `scripts/check-oss-apk.py <apk>` scans an APK's dex files and manifest for the same libraries. CI runs
  it on the oss debug APK in `ci.yml` and on the signed release APK in `release.yml`, before the upload.

## Before sending, once

1. Pick the latest tag. Update `versionName`, `versionCode`, `commit`, `CurrentVersion` and
   `CurrentVersionCode` in the draft; `versionCode` is `major*10000 + minor*100 + patch`, so 0.26.3 is
   2603.
2. **F-Droid.** Fork https://gitlab.com/fdroid/fdroiddata, copy the draft to
   `metadata/io.github.nytka_app.yml`, run `fdroid lint io.github.nytka_app` and
   `fdroid build -v -l io.github.nytka_app` (needs the F-Droid build server), then open a merge request.
3. **Reproducible build (optional, makes F-Droid ship our signature).** When the local `fdroid build`
   matches the release APK, fill in `AllowedAPKSigningKeys` with the certificate's SHA-256
   (`apksigner verify --print-certs nytka-<version>.apk`) and uncomment `Binaries`. Not tried yet: the
   draft builds from source and F-Droid signs with its own key, which makes the app a different install
   from the GitHub one.
4. **IzzyOnDroid.** It reads the signed APK from GitHub Releases (`nytka-<version>.apk`) and the fastlane
   folder. [`izzyondroid-request.md`](izzyondroid-request.md) says where to open the issue: the GitLab
   tracker is archived, so check the Codeberg one and its Inclusion Policy first.

## Known risks

- `versionCode` is computed in `app/build.gradle.kts` (`versionCodeOf(appVersion)`), which `fdroid`'s
  parser cannot read, so the draft keeps `AutoUpdateMode: None` and each release needs a `Builds`
  entry by hand. Making it a literal that release-please also bumps would let `AutoUpdateMode: Version`
  work; that is a build change of its own.
- `settings.gradle.kts` lists JitPack (only for sherpa-onnx, which `oss` does not use). F-Droid's
  scanner may still ask about the repository; the `oss` build never contacts it.
- Both stores want an APK that works with no Google services. `oss` does; Transcribe on this phone and
  activity transitions need `play`.
- There are no screenshots in the repository. Add `fastlane/metadata/android/en-US/images/phoneScreenshots/`
  when there are honest ones (no real voices or transcripts, see the architecture rules).

## Changelogs

A store changelog is `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, at most 500 bytes.
release-please writes `CHANGELOG.md` from commit titles, which are too technical and too long for a store
page, so these files stay hand-written and are added in the PR that a release should show text for.
Without one, the store shows no changelog for that version.
