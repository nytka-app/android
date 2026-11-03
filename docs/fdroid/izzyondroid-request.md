# IzzyOnDroid inclusion request (draft, not sent)

Text for the issue that asks IzzyOnDroid to list Nytka. Copy everything under the line into one new
issue (one issue per app). Where to open it, read first: the old GitLab tracker
(`gitlab.com/IzzyOnDroid/repo`) is archived and read-only; the project says requests now go to its
Codeberg presence, `https://codeberg.org/IzzyOnDroid/repodata`, and the rules are in its
[Inclusion Policy](https://codeberg.org/IzzyOnDroid/repo/src/branch/master/Inclusion-Policy). Both
pages refused automated reading when this draft was written, so check the policy and the issue
template there and move these facts into the template's fields if it has any.

Facts to re-check right before sending: the latest release number, the APK size and the signing
certificate fingerprint (the commands are in [README.md](README.md)).

---

**Title:** Request: Nytka (io.github.nytka_app)

**App:** Nytka for Android, package `io.github.nytka_app`

**What it is:** The phone half of a self-hosted replacement for the Omi AI pendant's cloud. It takes
the pendant's audio over Bluetooth, keeps it in a queue on the phone and uploads it to a Nytka server
the user runs themselves (free software too, https://github.com/nytka-app/server). It shows the
conversations, summaries, tasks and memories the server makes. Nothing goes through Omi's cloud.

**Source and licence:** https://github.com/nytka-app/android, Apache-2.0.

**APK:** The signed release APK is attached to every GitHub release as `nytka-<version>.apk`:
https://github.com/nytka-app/android/releases/latest (v0.26.3: 42.5 MB, one APK for all ABIs). CI builds the `oss` flavor from the tagged
commit and signs it with the project's key (`.github/workflows/release.yml`); `apksigner verify` runs
before it is uploaded. Every release has its own tag, `v<version>`, and a GitHub release.

**Free software only:** The `oss` flavor has no Google Play services, Firebase, ML Kit, billing or
other closed-source library, and no prebuilt native code. `./gradlew check` fails if its release
runtime classpath holds any, and `scripts/check-oss-apk.py` scans the built APK for them (both run in
CI). The `play` flavor, which adds Play services and a prebuilt on-device speech recogniser, is not
what releases publish.

**Trackers, ads, network:** No analytics, no ads, no account. The app talks to the server address the
user enters and to the pendant. The one other call is a firmware-release check against the GitHub API
(at most once a day, sends no token or identifier, switchable off in the app). It downloads no
binaries and never installs or flashes firmware.

**Permissions:** `INTERNET`, `BLUETOOTH_CONNECT`, `POST_NOTIFICATIONS`, `CAMERA` (scanning the
server's QR code), `FOREGROUND_SERVICE` with the connected-device and microphone types,
`RECORD_AUDIO` (only for the optional Phone microphone mode, off by default and never started by the
app itself), `ACCESS_COARSE_LOCATION` (only for the optional location tags, off by default),
`ACCESS_LOCAL_NETWORK`, `RECEIVE_BOOT_COMPLETED` and the companion-device permissions for reconnecting
to the pendant.

**Needs:** Android 12 (API 31) or later, an Omi pendant on consumer firmware 3.0.x and a Nytka server.
Without a server the app can be opened and set up but has nothing to show.

**Metadata:** `fastlane/metadata/android/en-US` and `uk` in the repository hold the title, short and
full description and the changelog for some releases.

**Maintenance:** Active; the commit history and releases show it. Releases are cut by release-please
from conventional commits.

**Not yet:** Screenshots are not in the repository (a pendant and a server are needed for honest ones).
The app is not on F-Droid yet; a build recipe is drafted in `docs/fdroid/`.
