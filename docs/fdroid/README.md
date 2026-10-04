# F-Droid and IzzyOnDroid

`io.github.nytka_app.yml` is a draft of the file F-Droid's `fdroiddata` repository needs. Nothing
here is submitted. Store text and changelogs live in `fastlane/metadata/android/en-US/`.

**F-Droid.** Fork https://gitlab.com/fdroid/fdroiddata, copy the draft to
`metadata/io.github.nytka_app.yml`, run `fdroid lint io.github.nytka_app` and
`fdroid build -v -l io.github.nytka_app` (needs the F-Droid build server), then open a merge request.

**IzzyOnDroid.** It reads the signed APK from GitHub Releases (`nytka-<version>.apk`) and the same
fastlane folder. Request inclusion with an issue at https://gitlab.com/IzzyOnDroid/repo.

Per release: add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (at most 500 bytes;
versionCode is `major*10000 + minor*100 + patch`, so 0.11.0 is 1100) before release-please merges, or
the store shows no changelog for that version.
