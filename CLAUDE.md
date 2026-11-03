# Nytka for Android

Android app for wearable recorders, today the Omi pendant (and, only while you switch it on, the phone's own microphone): reassembles its Opus frames from Bluetooth notifications, queues
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
| `:pendant` | One driver per `RecorderModel` behind `Pendant` (only Omi so far: `OmiPendant`, `OmiDriver`; a Limitless driver is in progress and untested on hardware, so no doc says it works). `Pendant` has `model` and `Capabilities` (storage, buttons, haptic, audio switch, settings, firmware stream, codec; the app reads haptic, audio switch, firmware stream and codec so far, the rest are declared but not wired yet), `RecorderModel` (id, service UUID, source, capabilities; `of(null)` is Omi), `FrameAssembler`, `OmiPendant` (Bluetooth) and `OmiDriver` (Omi's capabilities, button codes, haptic codes), `RecorderStorage` (stored audio read and freed by position; `StoredRange`, `StoredBlock`), the storage service behind it for Omi (`OmiStorage`, `RingProtocol`, `RingRecords`), the settings service (`OmiSettings`: LED brightness, mic gain), `FirmwareVersion`, `FirmwareStream`, `FakePendant` with `FakeRing`; no UI |
| `:core` | chunk format, `FrameQueue` (Room), capture times and the mute filter (`ring/`), settings, `NytkaApi` and its clients, `Uploader`, `diagnostics/` (event log, samples, upload), `location/` (saved places, location tags), `context/` (context ranges), `transcribe/` (on-device draft text: the `OnDeviceTranscriber` seam, `DraftAudioTap`, `SpeechSegments`, `DraftTranscription`); tested on the JVM |
| `:app` | Hilt wiring, `CaptureController`, `StorageSyncController`, `PendantSettingsController`, `firmware/` (release check) and the services; `capture/` also holds the home-screen widget, the companion-device service, the phone-context recorder, `MuteLogRecorder` and `DiagnosticsRecorder`; `briefs/` (morning brief notifications), `phonemic/` (Phone microphone: `PhoneMicService`, `PhoneMicRecorder`, `OpusEncoder`), `quiet/` (quiet-day notice), `transcribe/` (model import, Opus decoder, worker, benchmark), alerts, Compose screens; `src/play` holds the sherpa-onnx engine, `src/oss` a stand-in without it |

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
   `FrameQueue.add` as it arrives; frames read from the recorder's storage enter through
   `FrameQueue.commit`, in one transaction with the recorder's position (`ring_position`, with its `model`). Sealing, the cap and uploads work
   on what Room holds. With "Transcribe on this phone" on, a SQLite trigger (`DraftAudioTap`) copies each chunk
   into `draft_audio` as it is inserted; nothing in the queue or the uploader knows about it. The recorder's storage
   is a second copy: the app frees it only through `RecorderStorage.advance` (the Omi ring's `ADVANCE`), and only past
   audio the server accepted, the mute filter dropped or you chose to discard.
2. **`CaptureController` alone turns the pendant's audio on or off.** It calls `setAudio(!muted)`
   whenever the connection becomes `Connected` or the muted setting changes, so a reconnect never
   resubscribes while muted. The pendant records to its ring while the phone is away whatever the
   setting says: `MuteLogRecorder` and `MuteFilter` keep the promise when the ring is read. A recorder
   without an audio switch (`Capabilities.audioSwitch`) keeps streaming while muted: the controller
   drops its frames while muted, its driver must read `setAudio(false)` as "drop every frame" (the contract on
   `Capabilities.audioSwitch`), and the Device screen says the recorder still hears. A recorder without `haptic`
   gets no buzz (`buzz` is a no-op).
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
7. **The phone's microphone records only while you switched Phone microphone on.** Off by default; the app never
   turns it on by itself, never at boot, never from a schedule or the companion service, and it is off after a
   restart (`START_NOT_STICKY`, no stored setting). While it records, `PhoneMicService`, a foreground service of type
   `microphone`, shows an ongoing notification with Stop, and Android's privacy dot is on. Its audio is its own
   capture source (`CaptureSource.PHONE_MIC`): it never enters a pendant session, and it goes to the server with
   `X-Nytka-Source: phone-mic`, only to a server that lists `phone-mic`. Both builds work without the permission.
8. **Real voices never enter this repository.** The fake pendant replays text-to-speech;
   developer-mode fixtures stay on the phone or go to the private research repository.
9. **A stored chunk is never deleted until the server answers.** Only `200` or `202` removes one
   from the queue. On a refusal (`400`, `409`, `413`, or any other `4xx` except `401`, `403`, `404`, `405`,
   `408`, `425` and `429`) it is parked, and its range still counts in `ackedThrough`, so `advance`
   never passes it. A driver never sends a command that erases audio the server has not accepted (Omi's `CLEAR`,
   any "delete all"), and writes nothing to the recorder's storage until its `RecorderStorage.support` is
   `Supported` (Omi: `OmiStorage`, firmware 3.0.20 or later, feature bit 6). A sync starts only when `/api/v1/info` lists
   `offline-sync`. **This covers stored chunks only.** A live chunk has no second copy: the same refusals
   delete it, and the 1 GiB cap deletes the oldest live chunks first (parked chunks count toward the cap
   but are kept).
10. **A recorder whose codec the server does not list is not recorded.** `CaptureController` checks
   `Capabilities.codec` against `ServerInfo.codecs` (`/api/v1/info`; a server without the field reads Opus FS320,
   21, only) when the recorder connects, and an unreadable one is `Refused` with a fixed sentence: a live chunk the
   server refuses with `400` is deleted (invariant 9), so the gate is the only way a new codec cannot lose audio.

## Pendant settings (Omi)

Only Omi has settings so far (`Capabilities.settings`). LED brightness (`19b10011`, 0 to 100) and mic gain (`19b10012`, level 0 to 8) come from the settings
service `19b10010` in `BasedHardware/omi` at `2e34261` (`omi/firmware/omi/src/lib/core/transport.c`, `omi/firmware/omi/src/mic.c` `mic_set_gain`,
`omi/firmware/omi/src/settings.c`).
The firmware saves each write to flash, so `PendantSettingsController` writes each control at most once
per 2 s, after the slider is released. Gain 0 is mute and levels 1 to 8 are -20, -10, 0, +6, +10, +20,
+30, +40 dB. `OmiSettings` writes only after `19b10021` reports bit 7 (LED) or bit 8 (gain). The gain level the
pendant reports goes to the server as `gain` context ranges (`GainRecorder`, `GainRanges`), so it can correct levels and
voice scores; the LED does not. The app never changes the gain by itself.

## Firmware notice (Omi)

Only Omi has a firmware stream so far (`Capabilities.firmware`; null means no notice). `Pendant` info gives model (`0x2A24`) and firmware (`0x2A26`); `FirmwareStream` (`:pendant`) maps
"Omi CV 1" + major 3 to the tag prefix `Omi_CV1_v` of `BasedHardware/omi` releases, and nothing else
(DK2, DK1, EVT and Glass have other tags; unmapped pendants get no check). `GithubFirmwareReleases`
lists tags (`git/matching-refs`, since the release list is mostly desktop builds), takes the highest
by number, and needs a published non-pre-release. `FirmwareUpdateChecker` runs it at most once per 24 h,
only if `Settings.firmwareCheck` (default on), when the Device tab shows a connected pendant. It sends
no token or identifier; this is the only call to a host that is not the user's server. Never download
or flash firmware.

## Conventions

- AGP's built-in Kotlin: never apply `org.jetbrains.kotlin.android`; KSP, never kapt.
- Hand-written fakes, no mocking library (`FakePendant`, `FakeRing`, `FakeSettings`,
  `FakeDeviceActions`, `FakeSyncControls`, `FakePhoneMicControls`). View models take interfaces (`SettingsSource`,
  `InfoClient`, `ConversationsClient`, `TasksClient`, `MemoriesClient`, `SearchClient`,
  `ServerSettingsClient`, `TokensClient`, `WebhooksClient`, `PeopleClient`, `PersonPageClient`,
  `ReviewClient`, `CardsClient`, `BriefsClient`, `VoiceprintsClient`, `DeviceActions`, `SyncControls`, `PhoneMicControls`),
  so their tests need no Android.
- A server call is a client interface in `:core` (`core/api`) with its Hilt module in
  `app/di/<Area>Module.kt`; `AppModule.kt` stays as it is. Screens turn a `404` or `405` on a list or
  create call into "This server needs an update", a `404` on one item into "This item no longer
  exists" and a `403` into "The app needs an admin token." (`ui/ServerNotices.kt`).
- A screen the server may lack checks `/api/v1/info` `features` first (`ServerInfo.FEATURE_*`: `people`,
  `voice-groups`, `review`, `offline-sync`, `voice`) and hides itself on an older server. A flag marks
  a feature, not a release, so a missing route still answers `404`: show "This server needs an update",
  never "This item no longer exists".
- Room schemas under `core/schemas/` are committed. A schema change bumps the database version (now
  10) and adds a migration, never a destructive fallback: the queue holds audio nobody can record
  again.
- Recorder facts (UUIDs, packet header, button codes, the storage service) come from the source the
  driver was ported from (`RecorderModel.source`), at a pinned commit; name the file when adding one. A port of
  MIT code keeps its notice. A recorder is documented as in progress in `docs/devices.md` until its driver has run
  on the real recorder.
- A change a user can see gets its README section changed in the same PR.
- Conventional commits; release-please turns them into releases and bumps `appVersion` in
  `app/build.gradle.kts`. Every commit uses a GitHub noreply address; the `.githooks` hooks and
  `.private-terms` stay on.
