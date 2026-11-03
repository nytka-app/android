# F-Droid, IzzyOnDroid and Obtainium

Where Nytka can be installed from besides a downloaded APK, and what each channel needs. Nothing here is
submitted. Checked against each channel's own documentation on 2026-11-03; their rules change, so
re-read the linked page before submitting. Store text and changelogs live in
`fastlane/metadata/android/` (`en-US` and `uk`). Back to the [README](../../README.md).

| Channel | Source of the APK | Status | Blocker |
|---|---|---|---|
| Obtainium | GitHub Releases, `nytka-<version>.apk` | works today, nothing to submit | none |
| F-Droid | built from this repository | draft in [`io.github.nytka_app.yml`](io.github.nytka_app.yml), never built with fdroidserver | unknown build, screenshots (below) |
| IzzyOnDroid | GitHub Releases, `nytka-<version>.apk` | request text in [`izzyondroid-request.md`](izzyondroid-request.md) | its AI policy (below); the 42.5 MB APK is over its 30 MB rule of thumb |

## Read this first: IzzyOnDroid's AI policy

IzzyOnDroid's [inclusion policy](https://izzyondroid.org/docs/general/AppInclusionPolicy/) says it is
"strongly opposed to apps which are fully or in part created by generative AI tools": vibe-coded apps are
rejected, and so are apps that integrate with LLM services. Using an LLM for research or debugging is fine
when its output is not in the app's code. The request form asks how much AI tooling was used, and the
policy says "any lack of transparency discovered by us can lead to the project being degraded to rejected state".

Nytka does not fit: 149 of the 168 commits on `main` carry a `Co-Authored-By: Claude` trailer, and
the server it talks to writes summaries with a language model. Answer the form truthfully; expect a rejection.
**F-Droid has no such rule today**: its [inclusion policy](https://f-droid.org/docs/Inclusion_Policy/) does not
mention generative AI, and a proposed interim policy ([fdroid/admin#699](https://gitlab.com/fdroid/admin/-/work_items/699))
is a draft that neither endorses nor bans it and leaves apps to a separate issue. Submit to F-Droid first;
do not spend the IzzyOnDroid request until you accept a likely "no".

## Obtainium

Works with the release workflow as it is, because each GitHub release carries exactly one APK. The user steps are
in the README ("Install") and in [Get started](../tutorial.md#install-with-obtainium). What makes them work:

- Obtainium's GitHub source reads the release list of `https://github.com/nytka-app/android` and downloads the
  attached APK; with one APK per release it needs no APK filter.
- It compares Android's version (`0.26.3`) with the release tag (`v0.26.3`). It strips a leading `v`, so
  updates are detected. Keep the tag format `v<major>.<minor>.<patch>` that release-please writes.
- Silent background updates need Obtainium to have done the install itself, Android 12 or later (Nytka needs it
  anyway) and a recent target SDK (Nytka's is 37).
- GitHub's anonymous API limit can throw a rate-limit error for someone tracking many GitHub apps; Obtainium
  takes a personal access token in its settings.
- A one-tap link for the README or the server's setup page: `obtainium://add/https://github.com/nytka-app/android`.
- The first install from Obtainium and every update must be signed by the same key, or Android refuses the update.
  The release workflow signs with the one key in the `release` environment; never publish an APK from a
  different key.

Sources: [Obtainium wiki, App Sources](https://wiki.obtainium.imranr.dev/sources),
[How Apps are Tracked](https://wiki.obtainium.imranr.dev/app_tracking),
[Deep Links](https://wiki.obtainium.imranr.dev/deep_links).

## F-Droid

F-Droid builds from source in its own build server. Reference:
[Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/),
[Submitting to F-Droid Quick Start](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/).

What the repository already has:

- Apache-2.0 licence, public source, no analytics or ads.
- The `oss` flavor, which has no Google services; `play` adds Play services location and a prebuilt native
  library and is not what F-Droid builds (`gradle: - oss`). Two checks keep `oss` free: `./gradlew
  :app:checkOssNoGoogleServices` (part of `./gradlew check`) fails if its release runtime classpath holds Play
  services, Firebase, ML Kit or billing, and `scripts/check-oss-apk.py <apk>` scans an APK's dex files and manifest
  for the same libraries; CI runs it on the debug APK (`ci.yml`) and on the signed release APK (`release.yml`).
- Fastlane text: title, short and full description, per-version changelogs, in English and Ukrainian.
- `dependenciesInfo` off, so the APK has no signing-block blob (`app/build.gradle.kts`).
- Release tags `v<version>` created by release-please.

Steps, in order:

1. Fix the open items below.
2. Fork https://gitlab.com/fdroid/fdroiddata, create a branch named `io.github.nytka_app`, copy
   `io.github.nytka_app.yml` to `metadata/io.github.nytka_app.yml`.
3. Run, ideally inside the F-Droid build container, in order: `fdroid readmeta`,
   `fdroid rewritemeta io.github.nytka_app`, `fdroid lint io.github.nytka_app`,
   `fdroid build -v io.github.nytka_app`. Fix what they report.
4. Commit as `New App: io.github.nytka_app`, push to the fork, open a merge request against fdroiddata and
   answer the packagers.
5. After the merge the app appears in about 24 to 48 hours if the build succeeds.

Open items before step 2:

- **Never built.** Whether F-Droid's build server accepts JDK 21, compileSdk 37 and AGP 9.5 is unknown. The
  `sudo` lines in the draft are a guess at installing JDK 21; step 3 is the test.
- **No screenshots.** The guide asks for phone screenshots in `fastlane/metadata/android/en-US/images/phoneScreenshots/`
  and an icon; the repository has neither as fastlane files. Use the fake pendant, never real voices
  (CLAUDE.md invariant 8).
- **Changelogs stop at 0.17.0** (`changelogs/1700.txt`); versions after it show no changelog in a store.
- **versionCode is computed**, so `fdroid checkupdates` cannot read it; each release needs a hand-written
  `Builds` entry. Making `versionCode` a literal that release-please also updates would allow `AutoUpdateMode: Version`.
- **JitPack.** `settings.gradle.kts` lists JitPack (only for sherpa-onnx, which `oss` does not use). The F-Droid
  scanner may still ask about the repository; the `oss` build never contacts it.
- **Anti-features.** F-Droid's policy asks that anti-features be disclosed. The app depends on a server the user
  runs; that server is Apache-2.0 too, so `NonFreeNet` should not apply, but the reviewers decide.
  `fdroid scanner` on the build tells whether any dependency needs one.

### Reproducible build (ship our signature)

With `Binaries` and `AllowedAPKSigningKeys` set, F-Droid builds the unsigned `oss` release, checks that it is
byte-identical to the APK on GitHub Releases apart from the signature, and then publishes our signed
APK. Phones then update between F-Droid and Obtainium without a signature clash. Without them, F-Droid
signs with its own key and the two channels cannot update each other.

Status: **not tested.** Nothing shows yet that the build is byte-identical. What helps: `dependenciesInfo` is
off and AGP zeroes ZIP timestamps ([F-Droid, Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds/)).
What can still differ: the JDK build and Gradle version on the build server compared with the release
workflow's Temurin 21, and dependency resolution; F-Droid asks for a build from a clean checkout of the tag.

To test (needs JDK 21 and the Android SDK, or the F-Droid build container):

```bash
git checkout v0.26.3
./gradlew assembleOssRelease          # no keystore variables set: the APK is unsigned
gh release download v0.26.3 -R nytka-app/android -p 'nytka-0.26.3.apk'
# compare, ignoring the signature block (diffoscope shows what differs)
diffoscope app/build/outputs/apk/oss/release/app-oss-release-unsigned.apk nytka-0.26.3.apk
```

If only the signature differs, uncomment the two lines in the draft and fill in the certificate fingerprint
(`apksigner verify --print-certs nytka-0.26.3.apk`, lower case, no colons). `fdroid update` leaves out APKs
signed by any other key, and APKs that need several signers are not supported.

## IzzyOnDroid

Needs no build: it reads the signed APK from GitHub Releases (`nytka-<version>.apk`, one per tagged release)
and the same fastlane folder. The release workflow already meets its APK rules: signed with a release key, not
debuggable. One rule it does not meet: the policy reserves about 30 MB per app as a rule of thumb ("exceptions
are being made for some larger apps"), and `nytka-0.26.3.apk` is 42.5 MB (42,503,923 bytes). Say so in the
request and offer a per-ABI split if they ask; the policy names that as the remedy. Request inclusion with an issue in
https://codeberg.org/IzzyOnDroid/repodata (the older GitLab tracker is archived), using the template
"App Inclusion Request". The text for each field is in [`izzyondroid-request.md`](izzyondroid-request.md).

The policy also says to avoid `android:usesCleartextTraffic` unless essential. The network security config
permits cleartext because Android cannot allow it per server, and `ServerUrl` refuses `http://` unless the
private-network switch is on (CLAUDE.md invariant 5); say so in the request.

## Per release

Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (at most 500 bytes; versionCode is
`major*10000 + minor*100 + patch`, so 0.26.3 is 2603) in the PR a release should show text for, before
release-please merges, or the store shows no changelog for that version. These stay hand-written:
release-please's `CHANGELOG.md` is built from commit titles, too technical and too long for a store page.
For F-Droid also add a `Builds` entry to its metadata file (`versionName`, `versionCode`, `commit`,
`CurrentVersion`, `CurrentVersionCode`).

## What only the owner can do

1. Decide whether to file the IzzyOnDroid request, given its AI policy.
2. Create the accounts: GitLab (for the fdroiddata fork and merge request), Codeberg (for the IzzyOnDroid issue).
3. Take the screenshots and, if wanted, a feature graphic.
4. Run step 3 of F-Droid, and the reproducible-build test, on a machine with the SDK or the build container.
5. Submit: the fdroiddata merge request, and the IzzyOnDroid issue if step 1 says yes.
