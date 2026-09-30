# Nytka for Android

Android app for the Omi pendant: reassembles its Opus frames from Bluetooth notifications, queues
them in Room, uploads them as chunks to a Nytka server, copies the audio the pendant stored while the
phone was away, and shows what the server makes of it: conversations with titles, summaries and
tasks, memories and search. It also edits the server's settings, tokens and webhooks. What each
version must do: `docs/specs/v0.1.md` to `v0.4.md` in nytka-app/server; how v0.1 was built:
`docs/plans/`.

## Stack

Kotlin 2.4 (AGP 9 built-in Kotlin) · Jetpack Compose, Material 3 · Hilt with KSP · Room · DataStore
with a Keystore-encrypted token · WorkManager · OkHttp, kotlinx.serialization · JUnit 4,
Robolectric, MockWebServer, Turbine · ktlint, detekt.

| Module | Holds |
|---|---|
| `:pendant` | `Pendant`, `FrameAssembler`, `OmiPendant` (Bluetooth), the storage service (`OmiStorage`, `RingProtocol`, `RingRecords`), the settings service (`OmiSettings`: LED brightness, mic gain), `FakePendant` with `FakeRing`; no UI |
| `:core` | chunk format, `FrameQueue` (Room), capture times and the mute filter (`ring/`), settings, `NytkaApi` and its clients, `Uploader`; tested on the JVM |
| `:app` | Hilt wiring, `CaptureController`, `StorageSyncController`, `PendantSettingsController` and the services, alerts, Compose screens |

## Commands

```bash
./gradlew check assembleOssDebug assemblePlayDebug   # what CI runs
./gradlew :core:testDebugUnitTest
./gradlew :pendant:testDebugUnitTest
./gradlew :app:testOssDebugUnitTest --tests '*CaptureControllerTest'
./gradlew ktlintFormat                               # before every commit
./gradlew installOssDebug && adb logcat | grep -iE "nytka|AndroidRuntime"
```

## Architecture invariants

1. **A frame is safe once it is in Room.** `CaptureController` writes each reassembled live frame to
   `FrameQueue.add` as it arrives; frames read from the pendant's storage enter through
   `FrameQueue.commit`, in one transaction with the ring position. Sealing, the cap and uploads work
   on what Room holds. The pendant's ring is a second copy: the app frees it only by `ADVANCE`, and
   only past audio the server accepted, the mute filter dropped or you chose to discard.
2. **`CaptureController` alone turns the pendant's audio on or off.** It calls `setAudio(!muted)`
   whenever the connection becomes `Connected` or the muted setting changes, so a reconnect never
   resubscribes while muted. The pendant records to its ring while the phone is away whatever the
   setting says: `MuteLogRecorder` and `MuteFilter` keep the promise when the ring is read.
3. **A live capture session is one run of the capture service:** a new UUID, sequence numbers from 0
   without holes, and a capture time that is the phone's clock when a frame's last fragment arrived.
   Stored frames get one session per sync run and per pendant clock restart, times rebuilt from the
   record stamps by `CaptureTimes`, and sequence numbers without holes after the mute filter.
4. **The chunk format is shared with the server.** The golden bytes in `ChunkFormatTest` match the
   server's `ChunkFormatTests`; change both or neither.
5. **HTTPS is enforced in code.** The network security config permits cleartext because Android
   cannot allow it per server; `ServerUrl` refuses `http://` unless the private-network switch is on.
6. **Nothing sensitive in logs, errors or the debug report:** no audio, no transcript text, no
   token, and no server address in app log events. `Settings.toString`, `CreatedToken` and
   `CreatedWebhook` mask their secrets, clipboard entries that hold one are flagged sensitive, and
   OkHttp has no logging interceptor. A failure carries a fixed sentence or the system's own
   message, never the server's response text, except the field messages of a `400`.
7. **Never `RECORD_AUDIO`.** The app hears only the pendant.
8. **Real voices never enter this repository.** The fake pendant replays text-to-speech;
   developer-mode fixtures stay on the phone or go to the private research repository.
9. **A stored chunk is never deleted until the server answers.** Only `200` or `202` removes one
   from the queue. On `400`, `409` or `413` it is parked, and its ring range still counts in
   `ackedThrough`, so `ADVANCE` never passes it. The app never sends `CLEAR`, and writes nothing to
   the storage service until `OmiStorage` reports support (firmware 3.0.20 or later, feature bit 6).
   A sync starts only when `/api/v1/info` lists `offline-sync`.

## Pendant settings

LED brightness (`19b10011`, 0 to 100) and mic gain (`19b10012`, level 0 to 8) come from the settings
service `19b10010` in `BasedHardware/omi` at `2e34261` (`omi/firmware/omi/src/lib/core/transport.c`, `omi/firmware/omi/src/mic.c` `mic_set_gain`,
`omi/firmware/omi/src/settings.c`).
The firmware saves each write to flash, so `PendantSettingsController` writes each control at most once
per 2 s, after the slider is released. Gain 0 is mute and levels 1 to 8 are -20, -10, 0, +6, +10, +20,
+30, +40 dB. `OmiSettings` writes only after `19b10021` reports bit 7 (LED) or bit 8 (gain). Nothing
about them goes to the server.

## Conventions

- AGP's built-in Kotlin: never apply `org.jetbrains.kotlin.android`; KSP, never kapt.
- Hand-written fakes, no mocking library (`FakePendant`, `FakeRing`, `FakeSettings`,
  `FakeDeviceActions`, `FakeSyncControls`). View models take interfaces (`SettingsSource`,
  `InfoClient`, `ConversationsClient`, `TasksClient`, `MemoriesClient`, `SearchClient`,
  `ServerSettingsClient`, `TokensClient`, `WebhooksClient`, `DeviceActions`, `SyncControls`), so their
  tests need no Android.
- A server call is a client interface in `:core` (`core/api`) with its Hilt module in
  `app/di/<Area>Module.kt`; `AppModule.kt` stays as it is. Screens turn a `404` or `405` on a list or
  create call into "This server needs an update", a `404` on one item into "This item no longer
  exists" and a `403` into "The app needs an admin token." (`ui/ServerNotices.kt`).
- Room schemas under `core/schemas/` are committed. A schema change bumps the database version (now
  5) and adds a migration, never a destructive fallback: the queue holds audio nobody can record
  again.
- Pendant facts (UUIDs, packet header, button codes, the storage service) come from
  `BasedHardware/omi` at a pinned commit; name the file when adding one.
- A change a user can see gets its README section changed in the same PR.
- Conventional commits; release-please turns them into releases and bumps `appVersion` in
  `app/build.gradle.kts`. Every commit uses a GitHub noreply address; the `.githooks` hooks and
  `.private-terms` stay on.
