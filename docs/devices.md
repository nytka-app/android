# Recorders

Which wearable recorders Nytka for Android works with, what each can do, and what you do to use it. Back to the
[README](../README.md). The plan behind this page is [the multi-device spec](specs/multi-device.md).

Today one recorder is supported: the Omi pendant. A second, the Limitless Pendant, has a driver that has **not
been verified on real hardware**: it ran only against a stand-in (`FakeLimitless`). The pairing screen offers it as a
choice, so a first tester can try it. Nothing here claims it works.

## At a glance

| Recorder | Status | Stored audio while the phone is away | Buttons | Buzz | Audio on/off | LED and gain | Firmware notice |
|---|---|---|---|---|---|---|---|
| Omi pendant (consumer, "Omi CV 1") | **Supported** | Yes, from firmware 3.0.20 | Single tap, double tap, release | Yes | Yes | LED brightness and mic gain | Yes |
| Limitless Pendant | **Pairable, still not verified on real hardware.** Driver written; a choice on the pairing screen | Yes, from its flash; freed only past what the server accepted | Double press only | Not known | Not known | LED only | No |
| Plaud NotePin, Fieldy, Friend, Bee | Not supported, no driver | | | | | | |

The Omi row is the `Capabilities` of `OmiDriver` (`pendant/.../OmiDriver.kt`). The Limitless row is the
`Capabilities` of `LimitlessDriver`, read from Omi's reverse-engineered connector ([spec](specs/multi-device.md#the-recorders))
and never run on a pendant. It will change when the driver is tested.

## Omi pendant

**Status:** supported. This is the recorder the app was built for.

**What it does with Nytka**

- **Live audio** over Bluetooth, Opus, kept in the phone's queue until the server has it.
- **Stored audio:** it records to its own memory while the phone is away, and Nytka copies that when the phone
  is back, then frees the pendant's copy only past what the server accepted
  ([Offline sync](explanation/offline-sync.md)). This needs firmware 3.0.20 or later; on older firmware the card
  says so and nothing is read or written.
- **Buttons:** a double tap mutes or unmutes, a single tap bookmarks. Release is reported and ignored.
- **Buzz:** the pendant confirms a mute and a bookmark with a buzz.
- **Audio on/off:** Nytka switches the pendant's audio off while muted; the pendant still records to its own
  memory, and the mute filter removes the muted stretches when that memory is read.
- **Settings:** LED brightness and microphone gain, once the pendant reports each
  ([Pendant settings](reference/device.md#pendant-settings)).
- **Firmware notice:** Nytka tells you when Omi has published a newer firmware and never installs one
  ([Pendant firmware notice](reference/device.md#pendant-firmware-notice)).

**What you do**

1. Uninstall the official Omi app, or at least force-stop it: two apps cannot hold the pendant at once.
2. Pair it in Nytka's first run, or on **Device → Pendant**. The chooser lists only devices that advertise the
   Omi service `19b10000-e8f2-537e-4f6c-d104768a1214`.
3. For stored audio, make sure the firmware is 3.0.20 or later. Nytka cannot update firmware; Omi's own app does
   that (install it, update, uninstall it again).

## Limitless Pendant

**Status:** the driver is written and **not verified on real hardware**. It has not been run on a Limitless Pendant;
it is tested only against `FakeLimitless`, a stand-in that answers the way its source reads. It is in the code (from
release 0.26.4) and the pairing screen lets you choose it, so it can be paired, but pairing it proves nothing about
recording. Do not buy a Limitless Pendant for Nytka. If you own one and want to be its first tester, open an issue
and say what happened.

The Limitless Pendant is no longer sold (since 5 Dec 2025, per the spec's survey), and its maker promised support only through 2026.
Nytka's driver would follow the open connector Omi wrote for it; the protocol is not published by the maker.

What the driver is built to do, to be confirmed on a real pendant:

- Live audio and stored audio (flash pages, freed only past what the server accepted).
- A double press for mute; no single press, so no bookmark from the pendant.
- LED brightness; no microphone gain; no firmware notice.
- The frame length (10 ms or 20 ms) is not known. If it is 10 ms, the driver joins pairs into 20 ms packets so
  the server needs no change.

What you do to pair it: on **Pair pendant** (or the Pairing step of first run), pick **Limitless** above the button.
Nytka then says "Not verified on real hardware"; Omi stays selected until you change it. Turn the pendant on and pick
it in the list that opens. The steps to retire the Limitless app go here when the driver has run for a day on a
pendant. Known gaps are listed in the [README](../README.md#recorders).

## Recorders without a driver

Plaud NotePin, Fieldy, Friend and Bee are in the [spec's survey](specs/multi-device.md#the-recorders) only.
None has a driver. The ones that would be live-only (Plaud, Fieldy, Friend) cannot hand over audio the phone missed, so
with them **audio said while the phone is away is lost**. Bee is dropped while its audio is encrypted.

## How the app tells recorders apart

The app reads what a recorder can do from its `Capabilities`; outside its driver it never asks which model it is.

| Part | What it is |
|---|---|
| `RecorderModel` (`:pendant`) | The kinds of recorder Nytka can pair with: id, name, service UUID, the source its protocol was read from, and its `Capabilities`. `Omi` and `Limitless` exist. A missing or unknown id reads as Omi, so installs from before models keep working. |
| `Settings.pendantModel` | The paired recorder's `RecorderModel.id`, stored next to the address. Pairing filters the system chooser on the model's service UUID only; the app never guesses the model from an advertisement. |
| `Pendant` | One driver per model. It exposes `model` and `capabilities` besides the connection, battery, link statistics, `frames`, `buttons`, `storage`, `settings`, `connect`, `setAudio` and `buzz`. |
| `Capabilities` | `storage` (`None`, or `Custody`: read, then free up to a position), `buttons` (events it can send), `haptic`, `audioSwitch`, `settings` (LED, gain), `firmware` (release stream, or null for no notice) and `codec` (the chunk codec its frames are in). The app reads `haptic`, `audioSwitch`, `firmware` and `codec` today; `storage`, `buttons` and `settings` are declared and not wired yet. |
| `RecorderStorage` | A recorder's stored audio, read and freed by position: `status()` (the first and next position, capacity, dropped), `read(from, count)` (blocks, then one `Done`), `advance(through)` (frees positions below `through`). A driver writes nothing to storage until its `support` is `Supported` and never sends a command that erases audio the server has not accepted. |
| Codec gate | Before a connected recorder records, `CaptureController` checks its `capabilities.codec` against `codecs` in the server's `/api/v1/info`. A server without the field reads Opus FS320 (21) only. A recorder whose codec the server does not list is `Refused` with "This server cannot read this recorder's audio yet. Update the server." and nothing is recorded, because a live chunk the server refuses is deleted. |
| `ring_position.model` | The model that wrote the stored-audio position (default `omi`). A position of another model is not followed. The Room database is at version 10. |

A recorder without an audio switch keeps streaming while muted: its driver drops frames while audio is off, and the
Device screen says the recorder still hears. A recorder without haptic gets no buzz.

## Adding a recorder

A recorder is one `RecorderModel` entry, one driver and its fakes, and a section on this page. Take its facts
(UUIDs, packet layout, button codes, storage commands) from a pinned commit of the source it was read from and name
the file; a port of MIT code keeps its notice. Document it here as **in progress** until its driver has run on a
real recorder.

Nytka is an independent project, not affiliated with or endorsed by any recorder maker. "Omi" is a trademark of
Based Hardware; "Limitless", "Plaud", "Fieldy", "Friend" and "Bee" belong to their owners.
