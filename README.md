# Nytka for Android

The phone half of [Nytka](https://github.com/nytka-app/server/blob/main/docs/vision.md), a companion
app and self-hosted server for the Omi AI necklace. The app takes the pendant's audio over
Bluetooth, keeps it in a queue on the phone and uploads it to your own
[Nytka server](https://github.com/nytka-app/server), which transcribes it and serves the
conversations back. Omi's cloud sees none of it.

Status: v0.1, capture and transcription. What v0.1 does and does not do:
[the v0.1 spec](https://github.com/nytka-app/server/blob/main/docs/specs/v0.1.md).

## What you need

- A phone on Android 12 or later, with Bluetooth on.
- An Omi pendant on consumer firmware 3.0.x. Nytka checks the pendant's audio codec when it
  connects and tells you if the firmware needs an update.
- A Nytka server the phone can reach, with its address and its token (`Nytka__AdminToken` in the
  server's `.env`). The
  [server README](https://github.com/nytka-app/server#first-run-about-5-minutes) sets one up with
  Docker Compose. Keep the address and the token where you can paste them on the phone: the token
  is a long random string.

## Install

1. Uninstall the official Omi app, or at least force-stop it: two apps cannot hold the pendant at
   once. To update the pendant's firmware later, install it again, update, and uninstall it.
2. On the phone, download `nytka-<version>.apk` from
   [Releases](https://github.com/nytka-app/android/releases/latest) and open it. Android asks once
   whether your browser may install apps; allow it, then tap **Install**. From a computer:
   `adb install nytka-<version>.apk`.

A newer APK installs over the old one the same way and keeps the queue and the settings.

## First run (about 5 minutes)

Charge the pendant and keep it next to the phone. Open Nytka: its *Set up Nytka* screen has four
steps.

1. **Server.** Enter the server's address (`https://…`) and the token, and tap **Test connection**.
   The next step opens once the server answers and accepts the token.
2. **Permissions.** Tap **Allow**. Android asks for two permissions, one prompt each, in this order:
   *Nearby devices*, so Nytka can reach the pendant, and on Android 13 and later *Notifications*,
   for the recording notification and the alerts. There are no others, and Nytka never uses the
   phone's microphone or location.
3. **Consent.** Read the note, tick **I understand** and tap **Continue**.
4. **Pairing.** Turn the pendant on, tap **Pair pendant** and pick it in the list that opens.
   **Set up later** skips this; the **Device** tab has the same **Pair pendant** button.

Nytka now opens on **Conversations**. The chip at the top reads **Waiting** while the phone connects
to the pendant, then **Recording**, with a red dot and the pendant's battery: the first run is done.
Say something: within a minute the status card shows "Server: Last upload …", and the speech shows
up under **Conversations** a few minutes after it is said.

A server on a VPN such as Tailscale or WireGuard, without HTTPS, needs the **Private network (allow
plain HTTP)** switch on the server step and an address like `http://<address on the VPN>:8080`. It
allows plain `http://`, which carries your audio unencrypted: use it only on a network you trust.

## Everyday use

- **Mute** with a double tap on the pendant: one long buzz means muted, two short buzzes mean
  recording again. **Mute** in the notification and on the status card does the same. Nothing is
  recorded while muted, and Nytka stays muted when the pendant reconnects.
- **Offline**, audio waits on the phone and uploads once the server answers again, with the times
  it was said. The queue holds up to 1 GiB, about 70 hours. Past 80% Nytka warns you; when it is
  full, the oldest audio goes first.
- **Alerts** come when the pendant has been away for 5 minutes, the server has been unreachable
  for 15 minutes, or the pendant battery reaches 20%.
- **Delete** a conversation from its ⋮ menu: its transcript and audio go from your server.
- **After a reboot**, recording starts once the phone has been unlocked and the pendant is in
  range; Nytka need not be opened.
- The **Device** tab shows the pendant and the server, and holds the settings: server address,
  token and the private-network switch.
- **Diagnostics** (Device → Developer mode) record link quality, queue and upload health every
  10 seconds while Nytka records, and keep the last 7 days. **Export diagnostics** shares them as a
  CSV file through Android's share sheet. **Send diagnostics to my server** is off by default; on, it
  uploads the samples to your server every minute (needs server 0.2.0 or later).
  The app's own log events (tags such as `OmiPendant`, `CaptureController`) are kept and uploaded with
  them while Nytka records; they hold no audio, transcripts, server address or token. Read them on the
  server with `select at, payload->>'level', payload->>'tag', payload->>'message' from diagnostics
  where payload->>'kind' = 'log' order by at`. The export and the sample count leave them out.

## When something is off

| What you see | What to do |
|---|---|
| "The server refused the token." on **Test connection** | The token differs from `Nytka__AdminToken` in the server's `.env`. Paste it again, whole. |
| Another error on **Test connection**, such as "Failed to connect" or "Unable to resolve host" | The phone cannot reach that address; an unreachable one takes 15 seconds to fail. Open the address plus `/healthz` in the phone's browser: `{"status":"healthy"}` means the server is up, so recheck the address and the private-network switch in Nytka. Anything else is the network: the VPN is off, a firewall is in the way, or `NYTKA_BIND` on the server points elsewhere. |
| "Without nearby devices Nytka cannot reach the pendant." | Android stops asking after two refusals. Open Nytka's app info (long-press its icon), allow *Nearby devices* under Permissions and tap **Allow** again. |
| "Pendant not supported", or a message about the audio codec | Update the pendant's firmware with the official Omi app, then uninstall that app again. |
| The chip stays on "Waiting", or the pairing list is empty | The official Omi app (or another phone) still holds the pendant, Bluetooth is off, or the pendant is out of range or flat. |
| "Paused: The server refused the token." | The server's token changed. Enter the new one under Device → Settings; nothing queued is lost. |
| "Unreachable since …" | The phone cannot reach the server. Audio waits in the queue and uploads by itself. |
| Recording, but no conversation after several minutes | The server could not transcribe the speech. Turn on developer mode (below) and tap **Check status**: it shows the server's last error, usually the transcription URL, key or model in the server's `.env`. |
| The recording notification disappears on its own | Some phones stop background apps. Set Nytka's battery use to *Unrestricted* in its system settings; [dontkillmyapp.com](https://dontkillmyapp.com) has the steps for each maker. |

## What stays on the phone

Audio waiting to upload, in the app's private storage, until the server has it; the settings; and
the token, encrypted with a key that never leaves the Android Keystore. Android backups and
phone-to-phone transfers skip all of it, so a new phone starts with first run. The app talks to
your server and nothing else: no analytics, no crash reporting, no Omi cloud.

Diagnostics samples hold counters and short status words: no audio, no words, no server address, no
token. They stay on the phone for 7 days and leave it only when you export them or switch on *Send
diagnostics to my server*, which sends them to your own server and nowhere else.

## Developer mode

Tap the version under Device → About seven times. Developer mode shows the Bluetooth packet rate
and loss, the queue, the last upload and the server's status. It has a fake pendant that replays a
bundled text-to-speech recording, a button that saves the next 60 seconds of frames as a test
fixture, and **Copy debug report** for bug reports, which never includes the token. A saved fixture
holds the voices of whoever spoke: keep it private.

## Build from source

JDK 21 and the Android SDK with platform 37.0; see [CONTRIBUTING.md](CONTRIBUTING.md).

```bash
./gradlew assembleOssDebug
adb install app/build/outputs/apk/oss/debug/app-oss-debug.apk
```

`oss` is the flavor Releases publishes; `play` is kept for a later store listing and only has to
compile.

## License

Apache-2.0, see [LICENSE](LICENSE) and [NOTICE](NOTICE).

Nytka is an independent project, not affiliated with or endorsed by Based Hardware; "Omi" is a
trademark of its owner. Nytka records the people around the wearer: you are responsible for
following the recording and privacy laws where you use it.
