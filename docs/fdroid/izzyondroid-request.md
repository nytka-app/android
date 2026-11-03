# IzzyOnDroid inclusion request: text for each field

Not filed. Read "Read this first" in [README.md](README.md) before filing: the AI answers below are
true, and IzzyOnDroid's policy strongly opposes AI-written code. File at
https://codeberg.org/IzzyOnDroid/repodata/issues/new, template "App Inclusion Request" (fields as of
2026-11-03: copy the template's current fields if they changed).

**Title:** `[AppRequest] Nytka`

**Guidelines (checkboxes)**
- I am the developer of the app: yes.
- Complies with the App Inclusion Policy: do not tick this without reading the AI policy and the size note under "Further Notices".
- Not already listed in the repo or issue tracker: search both first, then tick.
- The Fastlane folder is available in the app's repo: yes, `fastlane/metadata/android/` (English and Ukrainian). It has no screenshots yet; add them before filing.

**Link to the source code:** https://github.com/nytka-app/android

**Link to app in another app store:** none yet.

**License used:** Apache-2.0

**Categories:** Connectivity

**Summary:** Self-hosted companion for the Omi pendant: your audio goes only to your server.

**Description:**
Nytka is the phone half of a self-hosted replacement for Omi's cloud. It takes the audio of an Omi AI pendant over Bluetooth, keeps it in a queue on the phone and uploads it to a Nytka server that the user runs. The server is Apache-2.0 too (https://github.com/nytka-app/server). The app has no analytics, no ads, no account and no Google services in the `oss` flavor. It talks to the server address the user enters and to the pendant; the one other call is a firmware-release check against the GitHub API (at most once a day, no token or identifier, switchable off in the app; it downloads and flashes nothing). The phone's microphone is used only when the user switches Phone microphone on.

**Build instructions:**
```
git clone https://github.com/nytka-app/android.git && cd android
git checkout v0.26.3
./gradlew assembleOssRelease    # unsigned without the NYTKA_KEYSTORE_* variables
```
JDK 21 and the Android SDK with platform 37. The released APK is the `oss` flavor, signed in GitHub Actions
(`.github/workflows/release.yml`) and attached to the release as `nytka-<version>.apk`.

**AI tools usage**
- Assistance Level: the owner chooses. History shows 149 of 168 commits on `main` co-authored by Claude Code, which fits "Dominant" or "Substantial" more than "Moderate"; do not pick a lower level than the history supports.
- Tool(s): Claude Code (Anthropic).
- What did the tools help with: write the owner's value here (design, code, tests, documentation) and how much the owner reviewed.
- Accountability checkboxes: tick only what is true.

**Free software only:** the `oss` flavor has no Google Play services, Firebase, ML Kit, billing or other closed-source library, and no prebuilt native code. `./gradlew check` fails if its release runtime classpath holds any (`checkOssNoGoogleServices`), and `scripts/check-oss-apk.py` scans the built APK for them; CI runs both. The `play` flavor, which adds Play services and a prebuilt on-device speech recogniser, is not what releases publish.

**Permissions:** `INTERNET`, `BLUETOOTH_CONNECT`, `POST_NOTIFICATIONS`, `CAMERA` (scanning the server's QR code), `FOREGROUND_SERVICE` with the connected-device and microphone types, `RECORD_AUDIO` (only for the optional Phone microphone mode, off by default and never started by the app itself), `ACCESS_COARSE_LOCATION` (only for the optional location tags, off by default), `ACCESS_LOCAL_NETWORK`, `RECEIVE_BOOT_COMPLETED` and the companion-device permissions for reconnecting to the pendant.

**Needs:** Android 12 (API 31) or later, an Omi pendant on consumer firmware 3.0.x and a Nytka server. Without a server the app can be opened and set up but has nothing to show.

**Further Notices:**
- The release APK is 42.5 MB (`nytka-0.26.3.apk`), over the 30 MB rule of thumb; a per-ABI build is possible if wanted.
- `android:usesCleartextTraffic` is permitted in the network security config because Android cannot allow cleartext per server; `ServerUrl` refuses `http://` unless the user switches on a private-network option, and the app talks only to the server address the user enters.
- The server the app connects to summarises conversations with a language model the server's owner chooses; the app itself calls no LLM service.
- The Fastlane screenshots are made with the fake pendant, not real recordings. The repository has none yet.
- Re-check right before sending: the latest release number, the APK size (`gh release view -R nytka-app/android --json assets --jq '.assets[].size'`) and the signing certificate (`apksigner verify --print-certs nytka-<version>.apk`).
