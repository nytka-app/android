# Nytka for Android

Android app for the Omi pendant: reassembles its Opus frames from Bluetooth notifications, queues
them in Room, uploads them as chunks to a Nytka server and shows the conversations the server
transcribes. What v0.1 must do: `docs/specs/v0.1.md` in nytka-app/server; how it was built:
`docs/plans/`.

## Stack

Kotlin 2.4 (AGP 9 built-in Kotlin) · Jetpack Compose, Material 3 · Hilt with KSP · Room · DataStore
with a Keystore-encrypted token · WorkManager · OkHttp, kotlinx.serialization · JUnit 4,
Robolectric, MockWebServer, Turbine · ktlint, detekt.

| Module | Holds |
|---|---|
| `:pendant` | `Pendant`, `FrameAssembler`, `OmiPendant` (Bluetooth), `FakePendant`; no UI |
| `:core` | chunk format, `FrameQueue` (Room), settings, `NytkaApi`, `Uploader`; tested on the JVM |
| `:app` | Hilt wiring, `CaptureController` and the services, alerts, Compose screens |

## Commands

```bash
./gradlew check assembleOssDebug assemblePlayDebug   # what CI runs
./gradlew :core:testDebugUnitTest
./gradlew :app:testOssDebugUnitTest --tests '*CaptureControllerTest'
./gradlew ktlintFormat                               # before every commit
./gradlew installOssDebug && adb logcat | grep -iE "nytka|AndroidRuntime"
```

## Architecture invariants

1. **A frame is safe once it is in Room.** `CaptureController` writes each reassembled frame to
   `FrameQueue.add` as it arrives; sealing, the cap and uploads work on what Room holds.
2. **`CaptureController` alone turns the pendant's audio on or off.** It calls `setAudio(!muted)`
   whenever the connection becomes `Connected` or the muted setting changes, so a reconnect never
   resubscribes while muted.
3. **A capture session is one run of the capture service:** a new UUID, sequence numbers from 0
   without holes, and a capture time that is the phone's clock when a frame's last fragment arrived.
4. **The chunk format is shared with the server.** The golden bytes in `ChunkFormatTest` match the
   server's `ChunkFormatTests`; change both or neither.
5. **HTTPS is enforced in code.** The network security config permits cleartext because Android
   cannot allow it per server; `ServerUrl` refuses `http://` unless the private-network switch is on.
6. **Nothing sensitive in logs, errors or the debug report:** no audio, no transcript text, no
   token. `Settings.toString` masks the token, and OkHttp has no logging interceptor.
7. **Never `RECORD_AUDIO`.** The app hears only the pendant.
8. **Real voices never enter this repository.** The fake pendant replays text-to-speech;
   developer-mode fixtures stay on the phone or go to the private research repository.

## Conventions

- AGP's built-in Kotlin: never apply `org.jetbrains.kotlin.android`; KSP, never kapt.
- Hand-written fakes, no mocking library (`FakePendant`, `FakeSettings`, `FakeDeviceActions`). View
  models take interfaces (`SettingsSource`, `InfoClient`, `ConversationsClient`, `DeviceActions`),
  so their tests need no Android.
- Room schemas under `core/schemas/` are committed. A schema change bumps the database version and
  adds a migration, never a destructive fallback: the queue holds audio nobody can record again.
- Pendant facts (UUIDs, packet header, button codes) come from `BasedHardware/omi` at a pinned
  commit; name the file when adding one.
- Conventional commits; release-please turns them into releases and bumps `appVersion` in
  `app/build.gradle.kts`. Every commit uses a GitHub noreply address; the `.githooks` hooks and
  `.private-terms` stay on.
