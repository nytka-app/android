# Nytka for Android, more than one kind of recorder

Nytka speaks only to the Omi pendant today. This spec says how the app learns to speak to other wearable
recorders without weakening its one promise: a frame is safe once it is in Room, and a recorder frees stored
audio only after the server accepted it. The server side, a codec registry for the chunk format, is
nytka-app/server [`docs/specs/multi-device.md`](https://github.com/nytka-app/server/blob/main/docs/specs/multi-device.md);
the full device survey with every citation is
[`docs/research/2026-10-20-wearables.md`](https://github.com/nytka-app/server/blob/main/docs/research/2026-10-20-wearables.md)
there. Roadmap goals: "Limitless owners can switch" (stage 2) and "More pendants" (stage 3).

Verified against app `main` at `d28a8a5`, server `main` at `b6d9fd1` and `BasedHardware/omi` at `afad3b7`
(MIT), all on 2026-10-20. Nothing here was run on a recorder: protocol facts come from reading Omi's
connectors, not from hardware.

## Done when

1. A user picks the kind of recorder (Omi, then Limitless) before pairing; the companion chooser shows only
   devices that advertise that model's service.
2. One driver per model implements one contract; `CaptureController`, `StorageSyncController`, the Device
   screen and the widget read what a recorder can do from its capabilities, and nothing outside the
   driver names Omi.
3. Every driver writes each live frame to `FrameQueue.add` as it arrives, as the Omi driver does now.
4. A driver with stored audio frees it only through `advance` and only up to `ackedThrough`. A driver
   never sends a command that erases audio the server has not accepted (Omi's `CLEAR`, any "delete all").
5. The app captures only from a recorder whose codec the server lists; otherwise the connection is
   `Refused` with a fixed sentence, and no chunk is sent that the server would refuse with `400`.
6. Existing installs keep working with no step from the user: no saved model means Omi.
7. A Limitless pendant streams, syncs what it stored and frees it, for a day, with no Limitless app.

## The recorders

From the survey (Omi connectors at `afad3b7`, vendor pages and press, accessed 2026-10-20). "Live" means
audio only while the phone is connected. UNKNOWN means no source said.

| Recorder | Status | Protocol source | Discovery | Codec | Stored audio | Other | To test |
|---|---|---|---|---|---|---|---|
| Omi pendant (CV1) | Sold | MIT firmware in `BasedHardware/omi` | Service `19b10000-…` | Opus FS320 (21), 16 kHz, 20 ms | Ring: `READ`, `ADVANCE` frees | Buttons, haptic, battery, LED, gain, firmware notice | Owned |
| Limitless Pendant | Not sold since 5 Dec 2025; supported "throughout 2026" | Closed firmware; Omi's reverse-engineered connector, MIT, with a Kotlin port | Service `632de001-604c-446b-a80f-7963e950f3fb`, or a name with "limitless"/"pendant" | Opus, 16 kHz output; 10 ms or 20 ms frames (see below) | Flash pages: batch download, message 7 frees "up to index" | Double press only, battery, LED brightness; no gain, no firmware update | Buy used; price UNKNOWN |
| Plaud NotePin / NotePin S | Sold, $159 / $179 | Closed; Omi reverse-engineered it; an official Plaud SDK needs a partner key | Service `0x1910`, or name "plaud"/"notepin" | Opus FS320 per Omi (rechunked to 80 B) | Live in Omi's connector; reading older recordings and freeing: UNKNOWN | Battery; no buttons or settings | Buy new |
| Fieldy ("Compass") | Sold; price UNKNOWN (third parties quote $129-179) | Closed; reverse-engineered | Service `4fafc201-1fb5-459e-8fcc-c5c9c331914b` (the Espressif example UUID: DIY boards collide) | Opus, 40 B frames, TOC `0xb8` (16 kHz, 20 ms) | None; the maker says it stores no audio | Battery only | Buy new |
| Friend (original) | Friend 2.0 reported at $249; protocol match UNKNOWN | Closed; reverse-engineered | Service `1a3fd0e7-b1f3-ac9e-2e49-b647b2c4f8da`, or name "friend_" | LC3, 16 kHz, 10 ms | None | Battery is a fixed 90 in Omi | Buy new, if 2.0 matches |
| Bee | Amazon; Bee Frontier $74.99 since 15 Oct 2026 | Closed; firmware 0.6.1+ encrypts audio and Omi cannot read it | Service `03d5d5c4-a86c-11ee-9d89-8f2089a49e7e` | AAC | None | Mute command, battery | Not worth buying |
| OmiGlass | Dev kit, $499 | MIT | Omi service | Opus | None | Camera | Not a recorder Nytka needs |
| Apple Watch, Ray-Ban Meta | | Not Bluetooth GATT (WatchConnectivity; Meta's toolkit over HFP) | | | | | Out of scope |

What follows from the table:

- Only Omi and Limitless can hand over stored audio and free it after. Every other recorder is live-only,
  so with them a phone left at home loses audio: the README must say so per model.
- Omi, Limitless, Plaud and Fieldy all send Opus at 16 kHz, so the server path holds for them if every
  frame is 20 ms. Friend (LC3) and Bee (AAC) need a server decoder first.
- Bee is not a candidate: the current firmware encrypts audio and a new Bee is unlikely to speak the
  protocol Omi wrote. The roadmap's "Plaud and Bee" becomes "Plaud and Fieldy"
  (server PR alongside this spec).

**Limitless frame length is UNKNOWN.** Omi's parser accepts Opus TOC bytes `0xb8`, `0x78`, `0xf8` (20 ms
frames) and `0xb0`, `0x70`, `0xf0` (10 ms frames) (`LimitlessProtocol.kt`, `isValidOpusToc`, at `afad3b7`).
The server decodes every frame as exactly 320 samples and turns anything else into 20 ms of silence
(server `src/Nytka.Audio/Decoding/OpusFrameDecoder.cs:17-31`). If the pendant sends 10 ms frames, the driver
joins each pair into one 20 ms Opus packet (RFC 6716 §3.2, code 1 or 3: a byte operation, no re-encoding),
so the chunk format and the server stay as they are. Step 5 checks this on the first real pendant.

## What is Omi-shaped today

Verified at app `d28a8a5`:

| Where | What assumes Omi |
|---|---|
| `app/.../capture/PendantFactory.kt:24-32` | Builds `OmiPendant` or `FakePendant`, nothing else |
| `app/.../capture/CompanionPairing.kt:35-40` | One `BluetoothLeDeviceFilter` on `OmiUuids.AUDIO_SERVICE` |
| `core/.../settings/Settings.kt:13` | `pendantAddress` with no model next to it |
| `pendant/.../OmiUuids.kt:9-32` | Every UUID, and `OPUS_FS320 = 21` |
| `pendant/.../OmiPendant.kt:304-321` | Reads Omi's codec characteristic and refuses anything but 21 |
| `pendant/.../Pendant.kt:28-54` | `ButtonEvent.fromCode` maps Omi firmware codes (`button.c`); `Haptic` holds Omi's codes and durations |
| `pendant/.../Pendant.kt:91-123` | `PendantStorage` is the Omi ring: `setTime`, `info` (stop, then `INFO`), `read(fromSeq, count)` of 444-byte records, `advance(seq)` |
| `pendant/.../PendantSettings.kt:8-58` | `SettingsSupport` is feature bits 7 and 8 of `19b10021`; `MicGain` is Omi's 0-8 level table |
| `pendant/.../FirmwareStream.kt:9-38` | One stream, `Omi_CV1_v`; others already get no check (`FirmwareUpdateChecker.kt:43`) |
| `pendant/.../RingRecords.kt`, `RingProtocol.kt` | Omi's 444-byte records with a 4-byte stamp |
| `app/.../capture/StorageSyncController.kt:352-356` | Refuses a ring whose record size is not `RingProtocol.RECORD_BYTES` |
| `app/.../capture/CaptureController.kt:228-261` | Mute is a double tap, bookmark a single tap; `buzz` confirms (`:298`, `:330-332`) |
| `core/.../queue/RingPositionRow.kt:9-24` | One row (`id` 1) for "the paired pendant", keyed by address |
| `core/.../chunks/ChunkWriter.kt:33`, `ChunkReader.kt:26` | Always writes and accepts codec 21 |
| `CLAUDE.md` invariants 1, 2, 9; "Pendant settings"; "Firmware notice" | Written in Omi terms (ring, `ADVANCE`, `CLEAR`, feature bit 6, firmware 3.0.20) |

The `Pendant` interface (`Pendant.kt:130-156`) is already close to neutral: connection, battery, link
stats, frames, buttons, storage, settings, `connect`, `setAudio`, `buzz`. The work is in what sits behind
`storage`, `settings`, `buttons` and `buzz`, and in the factory and pairing around it.

On the server (`b6d9fd1`), the chunk header already has a codec byte at offset 5
(`src/Nytka.Audio/Frames/ChunkFormat.cs:47-50`, spec v0.1 "Wire format"), and `Chunk` carries it
(`Frames/Chunk.cs:4`). The reader accepts only 21, and 20 ms at 16 kHz is fixed in `Frame.DurationMs`
(`Frames/Frame.cs:6`), `Timeline` (`Decoding/Timeline.cs:13-14`), `OpusFrameDecoder`, the playback map and
the Ogg writer, which copies Opus packets straight through (`Api/AudioEndpoints.cs:42`,
`People/CardClip.cs:22`). The server stores chunk bodies as received and parses them when it processes a
session (`Pipeline/ProcessSessionHandler.cs:78`), so old chunks stay readable as long as codec 21 keeps its
meaning.

## Design

### Models and pairing

A `RecorderModel` enum in `:pendant` lists what Nytka supports, each with its service UUID, a display
name and the source it was ported from. `Settings` gains `pendantModel`, stored next to `pendantAddress`;
a missing value reads as `Omi`, so existing installs need no migration and DataStore needs none either.

Pairing asks for the model first, then opens the companion chooser filtered by that model's service UUID
only. The app does not guess the model from the advertisement: Fieldy's UUID is a common example UUID and
Limitless matches by a name as loose as "pendant", so a guess would bind the wrong driver. Changing model
is "Forget this recorder" then pair again; the queue and the server do not care which model sent a frame.

`PendantFactory.create` takes the model and builds its driver. `FakePendant` stays a developer switch and
takes a model too, so the fake can stand in for any driver's capabilities.

### The driver contract

`Pendant` stays the interface; it gains `model` and `capabilities`:

```kotlin
data class Capabilities(
    val storage: StorageKind,          // None, or Custody (read, then free up to a position)
    val buttons: Set<ButtonEvent>,     // what the recorder can send; empty for none
    val haptic: Boolean,
    val audioSwitch: Boolean,          // the recorder can stop sending audio on request
    val settings: SettingsSupport,     // LED and gain, each reported by the driver
    val firmware: FirmwareStream?,     // null: no update notice
    val codec: Byte,                   // the chunk codec its frames are in
)
```

- **Frames.** `frames` emits whole frames in the declared codec with the phone's clock at the last
  fragment, exactly as `FrameAssembler` does for Omi. Re-framing (Limitless 10 ms pairs, Plaud's 80-byte
  rechunking) happens inside the driver. Invariant 1 does not change: `CaptureController` writes each frame
  to `FrameQueue.add`.
- **Audio on and off.** Invariant 2 stays: only `CaptureController` calls `setAudio`. A recorder without
  `audioSwitch` (Fieldy streams whenever connected) gets `setAudio(false)` as "drop every frame before it
  reaches the controller", done in the driver, and the Device screen says that the recorder still hears
  while muted.
- **Buttons.** `ButtonEvent` keeps its meanings (single tap, double tap, release) and each driver maps its
  own codes; `fromCode` moves into the Omi driver. Limitless reports only a double press, so it can mute
  but not bookmark; the Device screen hides the bookmark hint when `SingleTap` is missing.
- **Haptic.** Without `haptic`, `buzz` is a no-op and the mute confirmation moves to the phone: the
  capture notification changes and the widget updates, as they already do on every mute. Whether to add a
  phone sound is left to step 2.
- **Settings.** Each driver reports what it supports. Omi keeps LED and the 0-8 gain table. Limitless
  offers LED brightness (message 26 in Omi's connector) and no gain. The Device screen shows only supported
  controls, as it does now for an Omi without the feature bits.
- **Firmware.** Only `FirmwareStream.OmiCv1` exists; every other model has `firmware = null` and shows no
  notice. Nytka never flashes firmware, for any model.

### Stored audio: one custody contract

`PendantStorage` becomes a contract over positions rather than Omi's ring records:

```kotlin
interface RecorderStorage {
    val support: StateFlow<StorageSupport>
    suspend fun status(): StoredRange?            // first and end position, epoch, device clock
    fun read(from: Long, count: Int): Flow<StoredEvent>  // StoredBlock(position, stampMs?, frames) ... Done(status)
    suspend fun advance(through: Long): Int       // frees positions below `through`
}
```

- Omi: position = ring sequence, epoch = ring epoch, block = one 444-byte record, `advance` = `ADVANCE`.
  `OmiStorage`, `RingProtocol` and `RingRecords` become the Omi driver's private parts.
- Limitless: position = flash page index, block = one flash page (its pendant-clock timestamp, field 1, and
  its Opus frames), `status` = `GetDeviceStatus` (message 21, oldest and newest page), `read` = mode
  `{batch, not realtime}` (message 8), `advance` = acknowledge up to index (message 7). Omi's own Android
  drain acknowledges only after the data is on disk (`LimitlessFlashDrainEngine.kt:222-238` at
  `afad3b7`); Nytka acknowledges later still, only past `ackedThrough`.
- A page that yields no frames but holds audio fields is a parse failure: the driver ends the window
  there and never acknowledges past it (Omi's parser makes the same choice, `parseStorageBuffer`).

`StorageSyncController` keeps its loop and its rule: commit blocks and the position in one transaction
(`FrameQueue.commit`), upload, and advance only to `ackedThrough`. The record-size check
(`StorageSyncController.kt:352`) moves into the Omi driver's `status`. `CaptureTimes` already rebuilds times
from a stamp and the measured clock skew; Limitless gives a millisecond stamp per page, Omi a second stamp
per record, so the stamp unit becomes part of `StoredBlock`.

**Room.** `ring_position` gains a `model` column (default `omi`); the database goes from version 6 to 7 with
a migration, never a destructive fallback. A position whose model differs from the paired one is
discarded the same way a position for another address is today. `frames` and `chunks` need no change: a
frame's `ringSeq` and `epoch` already mean "position in the recorder's storage".

### Codec and the server

The chunk header's codec byte already exists, so the format stays `application/vnd.nytka.frames.v1`. The
server spec registers codec values and adds `codecs` to `/api/v1/info`; a missing field means `[21]`.

The app sets the codec from the driver (`ChunkWriter` takes it from the session instead of the
constant). Before capturing, the app checks the paired recorder's codec against the server's list. A
recorder the server cannot decode is `Refused` with "This server cannot read this recorder's audio yet.
Update the server." This matters because a live chunk the server refuses with `400` is deleted (invariant
9); a gate before capture is the only way a new codec cannot lose audio. A capture session never mixes
codecs: one session is one run of one driver.

Limitless, Plaud and Fieldy need no new codec value if their frames reach the queue as 20 ms Opus at
16 kHz. Codec values for LC3 and AAC are reserved in the server spec but not accepted until a decoder ships.

### Fakes and tests

- `FakePendant` takes a `RecorderModel` and capabilities, so `CaptureController` and view-model tests run
  against "a recorder without haptic", "one that cannot switch audio off", "one with no storage".
- `FakeRing` stays the Omi storage fake. A `FakeFlash` answers Limitless messages 7, 8 and 21 with pages
  built in the test, the way `FakeRing` builds records.
- One abstract `RecorderStorageContractTest` runs against both fakes: no advance past what was
  committed, a cancelled read frees nothing, a parse failure stops the window, a lost link ends with
  `Done(LINK_LOST)`.
- Omi's Limitless golden fixtures (`app/test/fixtures/limitless_golden/`, 12 JSON files of 0.2-4.6 KB) can
  be ported with the MIT notice after reading each one: invariant 8 forbids real voices, so a fixture whose
  Opus decodes to speech is replaced by a text-to-speech one.

### What the docs must say

- **README.** "An Android app for wearable recorders": a table of supported models with what each can do
  (live only or stored audio, mute, bookmark, settings), how to pair each, and "uninstall the maker's app
  first" per model. For live-only models: "audio said while the phone is away is lost". The trademark
  line names every maker.
- **CLAUDE.md.** The `:pendant` row lists drivers. Invariant 1 unchanged. Invariant 2 adds the drop-in-driver
  rule for recorders without an audio switch. Invariant 9 says "the recorder's storage" and "a command that
  erases unaccepted audio" instead of `CLEAR`, and that a driver writes nothing to storage until its own
  support check passes (Omi: firmware 3.0.20, feature bit 6). "Pendant settings" and "Firmware notice"
  become per-model sections. A new rule: recorder facts come from a pinned commit of the source they were
  ported from, with the file named, and a port of MIT code keeps its notice.
- **docs/reference/device.md** shows which controls appear for which model.

## Steps

One PR each. App and server PRs are separate; none needs the others merged first unless noted.

| # | Plane | Repo | Step | Hardware |
|---|---|---|---|---|
| 1 | NYTKA-117 | app | Models and pairing: `RecorderModel`, `Settings.pendantModel`, model picker, filtered chooser, factory by model (Omi only) | None |
| 2 | NYTKA-118 | app | Capabilities: `Capabilities` on `Pendant`; buttons, haptic, settings, firmware and the Device screen read them; `FakePendant` takes a model | None |
| 3 | NYTKA-119 | app | Storage custody contract: `RecorderStorage`, Omi behind it, `ring_position.model`, Room 7, contract tests with `FakeRing` | None |
| 4 | NYTKA-120 | server | Codec registry and `codecs` in `/api/v1/info` (accepts 21 only) | None |
| 5 | NYTKA-121 | app | Codec gate: codec from the driver, `Refused` when the server does not list it | None |
| 6 | NYTKA-123 | app | Limitless live: port `LimitlessProtocol`, driver with streaming, battery, double press, LED; `FakeFlash`; check frame length on a real pendant | Limitless Pendant |
| 7 | NYTKA-124 | app | Limitless stored audio: flash-page sync through the custody contract, acknowledge only to `ackedThrough` | Limitless Pendant |
| 8 | NYTKA-125 | app | README, CLAUDE.md and device reference for more than one model | None |
| 9 | NYTKA-126 | you | Wear the Limitless pendant for a day with no Limitless app; report link loss and sync | Limitless Pendant |
| 10 | NYTKA-127 | app | Plaud NotePin live driver | Plaud NotePin |
| 11 | NYTKA-128 | app | Fieldy live driver (audio switch emulated in the driver) | Fieldy |
| 12 | NYTKA-129 | server, app | LC3: server decoder and codec value, then the Friend driver; only if Friend 2.0 speaks the old protocol | Friend |

Steps 1 to 5 and 8 need no hardware and can start now. Steps 6, 7 and 9 need a Limitless Pendant, which is
no longer sold: Yehor has to buy one used or borrow one (NYTKA-122). Step 6 can be written against `FakeFlash` and the
golden fixtures first, but it is done only when frames from a real pendant decode on the server.

## Order and why

1. **Limitless first.** It is the only other recorder with stored audio Nytka can free safely, the one with a
   Kotlin reference under MIT, and its owners have a deadline: support is promised only through 2026.
   Its Opus keeps the server path if frames are 20 ms or are paired.
2. **Plaud next**, then **Fieldy**: both on sale, both Opus at 16 kHz, both live-only. Plaud has the larger
   user base and records on its own; Fieldy's example UUID makes pairing by model essential.
3. **Friend** only after a server LC3 decoder, and only if the current hardware speaks the protocol Omi wrote.
4. **Bee** is dropped while its audio is encrypted.

## Open questions

1. Limitless frame length and whether its live and stored frames share it (step 6, on hardware).
2. Whether the Limitless pendant keeps working with no Limitless account after 2026 (UNKNOWN; nothing
   published).
3. Plaud: can its sync command read recordings made while away, and can anything free them? If yes, Plaud
   moves from live-only to custody and gets its own step.
4. A used Limitless pendant's price and condition (battery wear).
