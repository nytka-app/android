# Nytka for Android

The phone half of [Nytka](https://github.com/nytka-app/server/blob/main/docs/vision.md), a companion
app and self-hosted server for the Omi AI necklace. The app takes the pendant's audio over
Bluetooth, keeps it in a queue on the phone and uploads it to your own
[Nytka server](https://github.com/nytka-app/server), which transcribes it and serves the
conversations back. It shows the titles, summaries and tasks your server's language model writes,
the memories it keeps and a search over all of it, and it copies the audio the pendant stored while
the phone was away. Omi's cloud sees none of it.

Status: v0.4 of the roadmap is shipped: capture and transcription
([v0.1](https://github.com/nytka-app/server/blob/main/docs/specs/v0.1.md)), the AI layer
([v0.2](https://github.com/nytka-app/server/blob/main/docs/specs/v0.2.md)), offline sync
([v0.3](https://github.com/nytka-app/server/blob/main/docs/specs/v0.3.md)) and memory and search
([v0.4](https://github.com/nytka-app/server/blob/main/docs/specs/v0.4.md)). Each spec says what its
version does and does not do. Roadmap milestones are not release numbers: the app and the server each
have their own.

## What you need

- A phone on Android 12 or later, with Bluetooth on.
- An Omi pendant on consumer firmware 3.0.x. Nytka checks the pendant's audio codec when it
  connects and tells you if the firmware needs an update. Offline sync needs firmware 3.0.20 or
  later; live capture works without it.
- A Nytka server the phone can reach, with its address and an `admin` token: `Nytka__AdminToken` in
  the server's `.env`, or an admin token made in the app. A `read` token is refused ("The app needs
  an admin token."). The
  [server README](https://github.com/nytka-app/server#install-in-15-minutes) sets one up with
  Docker Compose. Keep the address and the token where you can paste them on the phone: the token
  is a long random string. The tabs, the sync and the server settings below need server 0.4.0 or
  later. An older server still takes audio, and a screen it lacks says "This server needs an update".

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

1. **Server.** Enter the server's address (`https://…`) and an `admin` token, and tap **Test
   connection**. The next step opens once the server answers and accepts the token. A `read` token
   stops here with "The app needs an admin token."
2. **Permissions.** Tap **Allow**. Android shows a prompt for *Nearby devices*, so Nytka can reach
   the pendant, and on Android 13 and later a second one for *Notifications*, for the recording
   notification and the alerts. There are no others, and Nytka never uses the phone's microphone or
   location.
3. **Consent.** Read the note, which says that recording people without their consent is illegal in
   some places, that you answer for following the law, and that the pendant also records while the
   phone is away. Tick **I understand** and tap **Continue**.
4. **Pairing.** Turn the pendant on, tap **Pair pendant** and pick it in the list that opens.
   **Set up later** skips this; the **Device** tab has the same **Pair pendant** button.

Nytka now opens on **Conversations**. The chip at the top reads **Waiting** while the phone connects
to the pendant, then **Recording**, with a red dot and the pendant's battery: the first run is done.
Say something: within a minute the status card shows "Server: Last upload …", and the speech shows
up under **Conversations** a few minutes after it is said. Pull the list down to refresh if a new
conversation hasn't appeared.

A server on a VPN such as Tailscale or WireGuard, without HTTPS, needs the **Private network (allow
plain HTTP)** switch on the server step and an address like `http://<address on the VPN>:8080`. It
allows plain `http://`, which carries your audio unencrypted: use it only on a network you trust.

## The tabs

- **Conversations.** A status card, a search icon and your conversations by day. A row shows the
  title, two lines of summary, and the time range and length. A chip reads **Summarizing** while the
  server writes a summary and **Summary failed** when it could not. Open a conversation for its
  summary, its tasks and the transcript, with the speaker's name in color above each run of words
  when the transcription provider names speakers. Its ⋮ menu has **Rename** (an empty title restores
  the generated one), **Regenerate summary** (not while the conversation is open) and **Delete**.
  When the server kept the audio (14 days by default, and none for imported conversations), a play
  bar sits above the transcript: play or pause, the position and length, and a slider. Tap the time
  beside a paragraph to play from there. Pauses between speech are not stored, so they are skipped.
  Playback stops when you leave the screen or the app.
  Titles, summaries and tasks come from a language model on your server: without one, a row shows the
  start of the transcript instead.
- **Tasks.** What your conversations left you to do, newest first, with the conversation's title and
  day. Tick a task to complete it, tap it to open its conversation, and use ⋮ to edit or delete it
  (delete asks first). Done tasks sit below, collapsed. The model finds the tasks; there is no add
  button.
- **Memories.** Lasting facts about you that the server took from your conversations, each with the
  conversation it came from (a memory you add yourself has none). **+** adds one and ⋮ edits or
  deletes one. A memory you delete does not come back from later conversations.
- **Ask.** A placeholder that says "Arrives in a later version".
- **Device.** The pendant, its storage and settings, the server, the settings, People,
  [Your voice](#your-voice) and the version.

**Search** opens from the magnifier above the conversation list. It looks through transcripts,
titles, summaries and memories, in Ukrainian and English, while you type: from one letter or digit,
after a short pause. **All**, **Conversations** and **Memories** narrow it. A hit shows the title or
the memory and a snippet with the matches in bold, and opens its conversation when it has one. Every
word must match, exactly or as the start of a word. Endings of Ukrainian words need the server's optional dictionary:
see the [server README](https://github.com/nytka-app/server#ukrainian-search-optional).

## Offline sync

While the phone is out of range, the pendant stores what it hears on its own card. When the phone
comes back, Nytka reads that storage and uploads it with the times the audio was recorded, so it
lands in the right conversations. It needs pendant firmware 3.0.20 or later and server 0.4.0 or
later. On anything else the **Pendant storage** card on the **Device** tab says why, or is not
there, and live capture is not affected.

A sync starts by itself whenever the pendant connects. The card shows it, and the recording
notification adds "· syncing 42%". **Stop** holds it until the next connection or **Sync now**.

| The card says | What it means |
|---|---|
| "Nothing stored", or "About 12 min stored. It syncs when connected." | That much was waiting at the last check: the line shows "Nothing stored" until the phone has read the pendant. **Sync now** starts a sync when the pendant is connected. |
| "Checking the pendant…" | Nytka is asking the pendant what it holds. **Stop** cancels. |
| "Syncing 42%, 38 KB/s, about 3 min left" | Under way. The rate depends on the Bluetooth link. |
| "About 2 h stored. Waiting for your answer." | The first sync found a large backlog. Tap **Import or discard…** and answer the question below. |
| "Waiting for uploads: the queue is 61% full." | The phone's queue is over 50% full. The sync waits and goes on below 40%, so it never drops audio. |
| "Paused: the link dropped. It resumes when the pendant reconnects." | The pendant went out of range. The next connection carries on from where it stopped. |
| "Paused: live audio is losing packets. It resumes shortly." | Live audio lost over 5% of its packets in one read window (Nytka needs at least 50 live packets to judge). The sync pauses for 30 seconds; the third pause in a row ends it until the next connection or **Sync now**. |
| "The pendant did not answer (status 9). Trying again in 30 s." | The pendant's storage failed to answer. Status 9 means its card is not ready yet, and -1 is a timeout. Nytka tries again after 30 seconds, then after 2 minutes, then every 10 minutes. |
| "The server did not answer. Trying again in 30 s." | The phone could not reach the server. Same tries. |
| "Stopped. Sync now continues where it left off." | You pressed **Stop**. |
| "Offline sync needs firmware 3.0.20 or later; this pendant has 3.0.19. Update it with the official Omi app." | Update the firmware, then uninstall the official app again. |
| "This pendant does not offer offline storage." (or "…did not report its firmware", "…its features") | The pendant cannot store audio for Nytka to read. Live capture is not affected. |

Under the status line, the card shows **Synced** and **Lost** once they are above zero. They count
audio since Nytka's recording service last started. *Lost* is audio the pendant no longer held when
Nytka came to read it: its storage filled and overwrote the oldest audio, or the firmware freed it
early after an interrupted transfer. "Pendant clock was off by 6 min, times corrected" appears when
the pendant's clock had run wrong (after a crash, say) and Nytka fixed the times. The first sync of
a pendant that holds more than 2 hours asks "Import the stored audio?": **Import** reads it all,
which takes a while, and **Discard…** frees it on the pendant unread, after a confirmation.

- **Storage is freed after your server has the audio.** Nytka tells the pendant to free stored audio
  only after the server accepted it; the exceptions are audio dropped for a mute and a backlog you
  discard. If you kill Nytka or switch Bluetooth off in the middle of a sync, the next sync carries on
  without repeating text. Firmware 3.0.21 also frees a little on its own during a transfer, so an
  interruption can cost a few seconds, which the card counts as *Lost*. Stored audio your server
  refuses (`400`, `409` or `413`) stays parked on the phone, and the pendant keeps its copy.
- **Muted stretches are dropped.** The pendant keeps no mute state, so it records while the phone is
  away whatever you chose. Nytka logs every mute change and, before it uploads anything, drops each
  stored frame within 2 seconds of a muted stretch. A mute stays open until you unmute, so it covers
  a whole absence, and muted audio never reaches the server.
- **The pendant's clock.** At each connection to a pendant that supports offline sync, Nytka sets
  its clock from the phone. The pendant stamps stored audio with that clock.
- **What sync cannot recover.** Audio that a stalled Bluetooth link dropped while the phone was
  connected (the pendant never stores it), audio the pendant overwrote or freed early (shown as
  *Lost*), a backlog you discard, and muted stretches, which are dropped on purpose.

## Pendant settings

While the pendant is connected, the **Device** tab has a **Pendant settings** card with two sliders,
for pendants that report the feature (firmware 3.0.21 does). **LED brightness** runs from 0 to 100 %
and dims every status light; at 0 the link and charging lights stay dark too, and the card says so.
**Microphone gain** has levels 0 to 8: 0 is mute, then -20, -10, 0, +6, +10, +20 (the default,
level 6), +30 and +40 dB, as the firmware maps them. Level 0 records silence, and the card warns
about it. The value is written when you let go of the slider, at most once every 2 seconds for each
slider, because the pendant saves it to its flash memory and keeps it after a restart. Nytka reads
the current values on every connection and stores nothing on the server. A failed write shows a
message and puts the slider back.

## Pendant firmware notice

The **Pendant** card on the **Device** tab shows the firmware the connected pendant reports. Nytka
also tells you when Omi has published a newer one: the card then says "Firmware 3.0.21 is available
(this pendant has 3.0.20)" with a **How to update** link to Omi's own instructions. Nytka only tells
you. It never downloads or installs firmware; the official Omi app does that (install it, update,
uninstall it again).

- **When it asks.** At most once every 24 hours, and only while the Device tab shows a connected
  pendant, Nytka asks GitHub for the newest release of `BasedHardware/omi` in the pendant's firmware
  stream (three or four requests, no token). Offline, it tries again an hour later at the earliest.
- **Which pendants.** Only the consumer pendant, which reports the model "Omi CV 1" and a 3.x
  firmware, and whose releases are tagged `Omi_CV1_v<version>`. Omi's other hardware (DevKit 1 and 2,
  EVT, Glass) has its own tags and is not checked; a pendant that reports anything else gets no
  notice, because a wrong guess would send you to the wrong firmware. Pre-releases are ignored.
- **What GitHub learns.** Your IP address, like any website you open. No account, token, cookie,
  pendant data, audio, or server address leaves the phone for this. Switch it off with **Check for
  new pendant firmware** on the Device tab (on by default); then Nytka never contacts GitHub.

## Your voice

With server 0.12 or later and its speaker model (`/api/v1/info` lists `voice`), the **Device** tab
has a **Your voice** card. It teaches the server your voice once, so it marks the lines you said as
yours whatever the transcription service says. It needs an admin token.

Choose the languages you speak (Ukrainian, Russian, English) and tap **Enroll**. The screen shows
three short sentences per language: read them aloud with the pendant on, somewhere quiet, for at
least 30 seconds in all (with one language, read them twice). A meter shows the pendant's level and
a counter the seconds against the server's 120-second limit; recording stops by itself at the limit.
**Send** gives the reading to the server, which answers with how much speech it used. It refuses a
reading with less than 20 seconds of speech, one too short to make 3 samples, or one whose samples
do not sound like one voice (another speaker or noise), and the screen says which and what to do.

The reading comes from the pendant, never the phone's microphone, because the server matches pendant
audio later. While you read, the pendant's live audio goes only to the enrollment and is not queued
for upload, so the reading never becomes a conversation; capture goes back to normal as soon as you
send, cancel, reach the limit or leave the screen. The audio of the reading is not kept on the phone,
and the server keeps only the voiceprint.

Once enrolled, the card shows when, from how many samples, and how many of your segments the
voiceprint learned from since. **Re-enroll** starts over, **Add more** blends a new reading in (for
example another language), **Forget what it learned** goes back to the enrolled voiceprint, and
**Forget my voice** (asked first) deletes the voiceprint, every segment fingerprint and the labels
made from them; your own marks stay. See the
[server README](https://github.com/nytka-app/server#your-voice).

## Everyday use

- **Mute** with a double tap on the pendant: one long buzz means muted, two short buzzes mean
  recording again. **Mute** in the notification and on the status card does the same. While muted,
  Nytka hears nothing and the pendant discards its audio, and Nytka stays muted when the pendant
  reconnects. What the pendant records while the phone is away is handled by
  [Offline sync](#offline-sync).
- **Bookmark** with a single tap on the pendant: one short buzz after about a second means it is kept.
  A double tap (or two quick single taps) mutes instead and keeps no bookmark. **Bookmark** in the
  notification does the same from the phone. Bookmarks upload like audio, also while muted. A
  conversation shows a star beside the nearest paragraph, where you can tap to add a note. The tap
  works only while the pendant is connected.
- **Offline**, audio waits on the phone and uploads once the server answers again, with the times
  it was said. The queue holds up to 1 GiB, about 60 hours. Past 80% Nytka warns you; when it is
  full, the oldest live audio goes first.
- **Alerts** come when the pendant has been away for 5 minutes, the server has been unreachable
  for 15 minutes, the pendant battery reaches 20%, or the queue is 80% full.
- **Delete** a conversation from its ⋮ menu: its transcript, audio, tasks and memories go from your
  server.
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
| "Without nearby devices Nytka cannot reach the pendant." | Android stops asking after two refusals. Open Nytka's app info (long-press its icon), allow *Nearby devices* under Permissions, come back and tap **Allow** again. Nytka resumes first run at the step you reached. |
| "Pendant not supported", or a message about the audio codec | Update the pendant's firmware with the official Omi app, then uninstall that app again. |
| The chip stays on "Waiting", or the pairing list is empty | The official Omi app (or another phone) still holds the pendant, Bluetooth is off, or the pendant is out of range or flat. |
| "Paused: The server refused the token." | The server's token changed. Enter the new one under Device → Settings; nothing queued is lost. |
| "The app needs an admin token." | The token is a `read` token. Enter the server's `Nytka__AdminToken`, or an `admin` token made under Developer mode → Access tokens. |
| "This server needs an update" | The server has no endpoint for that screen. Update it to 0.4.0 or later; capture keeps working meanwhile. |
| "Unreachable since …" | The phone cannot reach the server. Audio waits in the queue and uploads by itself. |
| A conversation shows **Summary failed** | The server's language model failed: a wrong key, an endpoint that is down, a model name it does not know. The server's admin finds the reason at `GET /api/v1/status`, under `ai.lastError`. After the fix, use ⋮ → **Regenerate summary**. |
| No conversation gets a title or a summary | The server has no language model set up. Set its base URL and model in the server's `.env`, or under Developer mode → Server settings; see the [server README](https://github.com/nytka-app/server#the-language-model). |
| **Regenerate summary** says "No summary can be made now: the conversation is still open, or the server has no language model set up." | Wait until the conversation ends (by default after two minutes without speech), or set up the model. |
| The **Device** tab has no **Pendant storage** card | The card shows when a pendant is paired and the server lists `offline-sync` (server 0.4.0 or later). Its other states are in [Offline sync](#offline-sync). |
| Conversations appear but stay empty after you pull down to refresh | The server could not transcribe the speech. Turn on developer mode (below) and tap **Check status**: it shows the server's last error, usually the transcription URL, key or model in the server's `.env`. |
| The recording notification disappears on its own | Some phones stop background apps. Set Nytka's battery use to *Unrestricted* in its system settings; [dontkillmyapp.com](https://dontkillmyapp.com) has the steps for each maker. |

## What stays on the phone

Audio waiting to upload, in the app's private storage, until the server has it; the settings; the
token, encrypted with a key that never leaves the Android Keystore; how far the pendant's storage
has been read; the time and result of the last firmware check; and a log of when you muted and unmuted (times and on or off, nothing else). Android
backups and phone-to-phone transfers skip all of it, so a new phone starts with first run. The
pendant keeps the audio it stored until your server has it. The app talks to your server and nothing
else, apart from the once-a-day [firmware notice](#pendant-firmware-notice) (GitHub, switchable): no
analytics, no crash reporting, no Omi cloud.

Diagnostics samples hold counters and short status words: no audio, no words, no server address, no
token. They stay on the phone for 7 days and leave it only when you export them or switch on *Send
diagnostics to my server*, which sends them to your own server and nowhere else.

## Developer mode

Tap the version under Device → About seven times. Developer mode shows the Bluetooth packet rate
and loss, the queue, the last upload and the server's status. Under **Pendant storage**, when the pendant can
store and the server supports it, it shows the pendant's ring (read, write, capacity and dropped),
the last transfer status, the measured rate, the loss of live audio during a sync, the frames dropped
for a mute, the records dropped for a bad stamp, the pendant's clock skew and the number of clock
segments. It has a fake pendant that replays a bundled text-to-speech recording,
a button that saves the next 60 seconds of frames as a test fixture, and **Copy debug report** for
bug reports, which never includes the token. A saved fixture holds the voices of whoever spoke: keep
it private. Its **Server** section holds three screens.

**Server settings** edits the settings your server keeps in its database, grouped by prefix: `stt`,
`conversations`, `audio`, `llm`, `memories` and `search`. A setting that the server's `.env` sets is
locked: "Set by the server's environment". API keys show only **Set** or **Not set**, because they
live in the server's environment and the app can neither read nor change them. **Save** sends only
what you changed, all or nothing, and an emptied field restores the default. The server uses a new
value from its next job, with no restart. What each setting does is in the
[server README](https://github.com/nytka-app/server#configuration).

**Access tokens** lists the server's named tokens: name, scope, when it was made and last used, and
whether it is revoked. **Create token** asks for a name and a scope, **Read** (conversations, tasks,
memories and search over the API, and MCP for agents) or **Admin** (everything, this app included).
The token shows once, with **Copy**; Nytka cannot show it again. **Revoke** ends a token at once. Give
[MCP clients](https://github.com/nytka-app/server#mcp) a read token.

**Webhooks** lists the server's webhooks, each with an on and off switch and the status of its last
delivery. **+** adds one: a URL, the events (a conversation is summarized, a task is created, a task
is completed, a memory is created, or all of them) and a description. The signing secret shows once,
with **Copy**. A webhook's own screen has **Send test**, its last 30 deliveries and **Delete**. How a
receiver checks the signature is in the [server README](https://github.com/nytka-app/server#webhooks).

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
