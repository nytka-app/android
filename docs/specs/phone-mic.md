# Phone microphone mode: design

Status: design for approval (2026-10-24). Nothing below is built until the owner approves it.

The owner asked for the phone's microphone as an option ("let's add phone micro as an option"), against the device
research's "not now" (nytka-app/research `audio/2026-10-device-and-emotion.md`, Q3 row d). This spec says how, and what
changes in the app's promises.

## What it is for

- **Days without the pendant**: the phone records in its place.
- **A second, farther microphone** next to the pendant: the wearer is loud on the pendant and quiet on the phone, a
  bystander or a loudspeaker about equal on both (the own-voice cue of hearing aids). That could attack both the wearer
  misses (12 of 55 lines in the research) and device audio. Using it that way is a later server decision; this mode
  only records and uploads.

## The rule that replaces invariant 7

Today: "7. **Never `RECORD_AUDIO`.** The app hears only the pendant." Proposed:

> 7. **The phone's microphone records only while you switched Phone microphone on.** Off by default; the app never
> turns it on by itself, never at boot, never from a schedule or the companion service. While it records, a
> foreground service of type `microphone` shows an ongoing notification with Stop, and Android's green privacy dot
> is on. Its audio is its own capture source: it never enters a pendant session, and it goes to the server with
> `X-Nytka-Source: phone-mic`. The Play and oss builds both work without the permission.

README's "Not here: … the phone's microphone: the app never asks for `RECORD_AUDIO`" becomes a "What it does" line,
and the consent warning gains a sentence (below).

## Permissions and service

| Item | Value | Why |
|---|---|---|
| `RECORD_AUDIO` | runtime, asked when the switch first goes on | a refusal keeps the switch off; nothing else asks for it |
| `FOREGROUND_SERVICE_MICROPHONE` | normal | required for the service type from Android 14 |
| Service | new `PhoneMicService`, `foregroundServiceType="microphone"`, not exported | separate from `CaptureService` (`connectedDevice`), so the pendant path is untouched and stopping one never stops the other |
| Start | only from a visible screen: the Device screen switch, or the notification's own action | Android forbids starting a microphone service from the background ([restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)); this also enforces "never by itself" |
| After a reboot or a kill | does not restart; the switch reads off and the notification is gone | `START_NOT_STICKY`; `BootReceiver` and `NytkaCompanionService` never touch it |
| Muted | the pendant's mute setting and mute windows also pause the phone mic (the service stops reading; the ongoing notification says "Paused") | one "don't record now" control for both |

Play build: no new dependency. oss build: same code, nothing proprietary. Without the permission both builds behave as
today.

## Audio path, into the existing chunk format

- `AudioRecord`, `MediaRecorder.AudioSource.VOICE_RECOGNITION` (no AGC/noise suppression, unlike `MIC` on many phones),
  16 kHz, mono, PCM 16-bit, read in 20 ms buffers of 320 samples.
- Encode each 20 ms buffer with the platform Opus encoder (`MediaCodec`, `audio/opus`, available from API 29; minSdk is
  31), 32 kbit/s, one packet per 20 ms: exactly codec 21 ("Opus, one packet per frame, 20 ms, 16 kHz"), so the chunk
  format, `ChunkFormatTest`'s golden bytes and the server's decoder stay unchanged. If a phone has no Opus encoder, the
  switch says so and stays off (no fallback codec).
- Frames go to the same Room queue through `FrameQueue.add`, under their own session UUID (one per run of the
  service), sequence numbers from 0 without holes, capture time = the phone's clock when the buffer was read. So
  invariant 1 ("a frame is safe once it is in Room") holds as is.
- The queue row gets a `source` column (the next Room version after #135's 8, auto-migration, default `pendant`); the uploader sends
  `X-Nytka-Source: phone-mic` for those chunks, only to a server that lists `phone-mic` in `/api/v1/info`. Until the
  server does, the switch is hidden (no audio is recorded that could not be sent).
- The 1 GiB cap and the refusal rules of invariant 9 apply as to live pendant chunks: there is no second copy.
- Never mixed: the phone mic does not feed `CaptureController`, `DraftAudioTap` copies its chunks only if "Transcribe on
  this phone" is on (same as pendant), and the mute log covers it.

## How the server tells the sources apart

Agreed with the server session (its "PR C", design only, needs the owner's approval too):

- Header `X-Nytka-Source: pendant | phone-mic` on `POST /api/v1/chunks`; missing means `pendant`. Chunk bytes unchanged.
- A capture session holds one source; a chunk whose source differs from its session's earlier chunks gets
  `400 "A capture session uses one source."`. The app never mixes, so this only guards bugs.
- Conversations form per source, so a phone-mic session never joins a pendant conversation.
- Feature flag `phone-mic` in `/api/v1/info`.

Using both at once for wearer identity (level difference, clock alignment by cross-correlation) is out of scope here.

## Battery

UNKNOWN until measured on the owner's phone. Estimate: microphone capture at 16 kHz plus Opus encoding of 50 packets a
second costs little CPU (Opus encodes far faster than real time), but the service holds a partial wake lock for as long
as it records, which keeps the CPU out of deep sleep. Plausible range 1 to 3 % of battery per hour, so 15 to 40 % over a
14-hour day, on top of the pendant's Bluetooth capture. Step 3 measures it with `dumpsys batterystats` over 2 hours and
writes the figure into the README before release.

## Consent and legal note (README, next to the existing warning)

> [!WARNING]
> Phone microphone mode records everyone near the phone, not only near the pendant, and the phone often lies on a table
> between people. In Ukraine, recording someone's private conversation without consent can fall under Criminal Code
> art. 182 and Civil Code art. 306; keeping recordings for yourself is outside the data-protection law (art. 25), and
> the GDPR's household exemption is read narrowly. Turn it on only where the people around you know. You answer for
> following the law where you use it.

Sources: research report Q3 row d ([codes](https://zakon.rada.gov.ua/laws/show/2341-14),
[art. 25](https://zakon.rada.gov.ua/laws/show/2297-17), [Ryneš](https://www.hoganlovells.com/en/publications/cctv-cjeu-narrows-the-scope-of-the-household-exemption)). Not legal advice.

## Alternative the research proposed

A separate app or build flavour holding the microphone, so the main app keeps invariant 7 literally. Not chosen here:
it doubles the release and pairing work, and the owner asked for an option in this app. If preferred, the same design
moves into a `mic` flavour with the permission in its own manifest.

## Steps (each its own PR, after approval)

1. Rewrite invariant 7, README and privacy page; `RECORD_AUDIO` + `FOREGROUND_SERVICE_MICROPHONE`; `PhoneMicService`
   with the switch and notification; `AudioRecord` → Opus → `FrameQueue`; Room 9 `source`; uploader header; hidden
   until the server lists `phone-mic`. Tests: encoder framing, session and sequence rules, header, switch never on by
   itself (boot, companion, schedule).
2. Server PR C (server session).
3. Battery measurement on the owner's phone; figure into the README.

## Open questions for the owner

1. Approve the rewritten invariant 7 as worded above?
2. Same app (this design) or a separate `mic` flavour (the research's suggestion)?
3. Should the pendant's mute also pause the phone mic (proposed: yes)?
