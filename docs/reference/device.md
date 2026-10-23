# The Device screen

The pendant, its settings and firmware notice, your voice, phone context, meeting briefs and location tags. Back to the [README](../../README.md).

The Device screen groups settings by where they live, in four cards: **Pendant**, **On this phone**,
**Your server** and **Developer** (the last only while developer mode is on), then **About**.

- **Pendant** shows the pendant's state, its battery as a meter (a neutral colour; below 10 % it turns
  red and says to charge the pendant), the firmware, the pendant storage and the pendant settings.
  **Pendant details** opens a screen with its name and address, where **Forget** asks first and the
  dialog names the pendant that is removed. Forgetting a pendant is only possible there.
- **How people nearby know** (Device → Pendant, and the *People near you* step of the first run) says
  what the Omi pendant shows the people around you, on firmware 3.0.x. It never shows that the pendant is
  recording or muted: the light means connected (blue), not connected (red), clock not set (blinking
  red), charging (blinking green, alternating with blue or red), charged (steady green) or off. Under
  a red light the pendant still records to its own memory while the phone is away. The button: a
  double-tap mutes or unmutes with one long buzz, a single tap marks the moment with one short buzz,
  and holding it for 3 seconds turns the pendant off. It is read from the pendant's firmware (see the
  page's last line), and the colours are written in words, not drawn.
- **About → About recording** shows the four points of the first run's *People near you* step again,
  to read; there is nothing to tick.
- Server addresses, tokens and webhook URLs are set in a monospace font. A token shows as dots and its
  last four characters; **Revoke** is on the token's own screen (Developer mode → Server → Access
  tokens → tap a token) and asks first, naming the token. Deleting a webhook asks first and names
  its address.
- Developer mode sets its measurements in the same monospace font. Server settings groups each prefix
  in its own card, and Access tokens and Webhooks show a loading, empty or failed list the way the other
  lists do, with **Retry** on a failure.
- The recording notification, the alerts and the brief notification follow the phone's language
  (English or Ukrainian), and the alerts and briefs carry Nytka's indigo accent.

## Pendant settings

While the pendant is connected, the **Device** screen has a **Pendant settings** card with two sliders,
for pendants that report the feature (firmware 3.0.21 does). **LED brightness** runs from 0 to 100 %
and dims every status light; at 0 the link and charging lights stay dark too, and the card says so.
**Microphone gain** has levels 0 to 8: 0 is mute, then -20, -10, 0, +6, +10, +20 (the default,
level 6), +30 and +40 dB, as the firmware maps them. Level 0 records silence, and the card warns
about it. The value is written when you let go of the slider, at most once every 2 seconds for each
slider, because the pendant saves it to its flash memory and keeps it after a restart. Nytka reads
the current values on every connection and stores nothing on the server. A failed write shows a
message and puts the slider back.

## Pendant firmware notice

The **Pendant** card on the **Device** screen shows the firmware the connected pendant reports. Nytka
also tells you when Omi has published a newer one: the card then says "Firmware 3.0.21 is available
(this pendant has 3.0.20)" with a **How to update** link to Omi's own instructions. Nytka only tells
you. It never downloads or installs firmware; the official Omi app does that (install it, update,
uninstall it again).

- **When it asks.** At most once every 24 hours, and only while the Device screen shows a connected
  pendant, Nytka asks GitHub for the newest release of `BasedHardware/omi` in the pendant's firmware
  stream (three or four requests, no token). Offline, it tries again an hour later at the earliest.
- **Which pendants.** Only the consumer pendant, which reports the model "Omi CV 1" and a 3.x
  firmware, and whose releases are tagged `Omi_CV1_v<version>`. Omi's other hardware (DevKit 1 and 2,
  EVT, Glass) has its own tags and is not checked; a pendant that reports anything else gets no
  notice, because a wrong guess would send you to the wrong firmware. Pre-releases are ignored.
- **What GitHub learns.** Your IP address, like any website you open. No account, token, cookie,
  pendant data, audio, or server address leaves the phone for this. Switch it off with **Check for
  new pendant firmware** on the Device screen (on by default); then Nytka never contacts GitHub.

## Your voice

With server 0.12 or later and its speaker model (`/api/v1/info` lists `voice`), the **Device** screen
has a **Your voice** card. It teaches the server your voice once, so it marks the lines you said as
yours whatever the transcription service says. It needs an admin token.

Choose the languages you speak (Ukrainian, Russian, English) and tap **Enroll**. The screen shows
three short sentences per language: read them aloud with the pendant on, somewhere quiet, for at
least 30 seconds in all (with one language, read them twice). One sentence shows at a time in large type; **Next sentence** moves on. A timer and a bar count the
seconds against the server's 120-second limit, with a mark at 30 seconds; recording stops by itself at
the limit. **Send** stays off until 30 seconds, and the screen says how many more to read. **Send** gives the reading to the server, which answers with how much speech it used. It refuses a
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
[server docs](https://github.com/nytka-app/server/blob/main/docs/explanation/features.md#your-voice).

## Phone context

When the server lists `context-ranges` in `/api/v1/info`, the **Device** screen has a **Phone context** switch,
on by default: "Tell your server when this phone plays sound through its speaker, is in a call, has its screen on
or plays to headphones. Only times are sent, never what played." While capture runs, the app notes these kinds of
stretch: **media**, when the phone plays music or video through its built-in speaker at a volume above 0 (it ends 2
seconds after the sound stops, or at once when the sound moves to headphones), and **call**, when the phone is in a
call or a voice or video chat, with the route it held longest: speaker, earpiece, headset, Bluetooth or other. The
server uses them to tell a conversation in the room from a television, a video or a call.

On a server that also lists `context-device`, two more: **screen**, while the screen is on, and **headphones**,
while the phone's media output goes to wired or USB headphones (route headset) or to Bluetooth (route Bluetooth).
Android's list of audio outputs does not tell Bluetooth headphones from a Bluetooth speaker or a car, so all three
count as headphones. A server without `context-device` gets only media and call; the other
stretches wait on the phone, as below, for one that takes them.

What the app sends is the kind, the route and two times for each stretch, in batches of up to 500 once a minute.
Never an app, a title, a contact or a number, and nothing from the microphone: the app listens to Android's
playback, call-state and audio-device callbacks and the screen on and off broadcasts, which need no permission, and
does not poll. A stretch under 3 seconds is
dropped. Stretches wait on the phone until the server has them and are deleted after 7 days if they never go out.
With the switch off, or on a server without `context-ranges`, nothing is recorded or sent.

## Meeting briefs

With a server that has a calendar feed (`Nytka__Calendar__IcsUrl`, see the
[server docs](https://github.com/nytka-app/server/blob/main/docs/explanation/features.md#calendar-briefs)), the server writes a short
brief before a meeting with someone you named: who they are to you, what to remember, what is still
open. Switch on **Meeting briefs** in the Device screen (off by default) and Nytka posts it as a
notification. On Android 13 and later it asks to show notifications first; if you refuse twice, the
switch offers **Open settings**. Under the switch, "Your server has no calendar feed" means the feed
is not set, and "This server needs an update" means the server predates it.

- **When.** Nytka asks your server every 15 minutes, with a network, and posts each brief once, for
  meetings not over. The server makes a brief 30 minutes before the meeting by default
  (`Nytka__Calendar__BriefMinutes`), so it arrives 15 to 30 minutes before; with 5 it may arrive
  after the start.
- **The lock screen** shows only "A meeting brief is ready": no title, name or fact. Unlock to read
  it. The notification channel is called "Meeting briefs".
- **A failed check** (no network, an older server) posts nothing and waits for the next one.
- Nothing about a brief goes into the app's logs or the debug report.

## Location tags

Switch on **Location tags** in the Device tab (off by default) and Nytka reads a coarse location
when a capture session starts, asking Android for it first: on Android 13 and later this is a
system permission prompt; if you refuse twice, the switch offers **Open settings**. Only
`ACCESS_COARSE_LOCATION` is requested, and the exact reading never leaves the phone: it is turned
at once into either the name of a place you saved (the nearest one it falls within, see below) or a
city-level label such as `near_n40_7_w74_0` (about 11 km across, letters/digits/underscore only, the
tag format the server accepts). Once your server has made a
conversation from that session, Nytka adds the place as a tag through the tags API, the
same write **+ Tag** uses. A reading more than a day old, or one that matches no conversation, is
dropped rather than tagging whatever is newest.

**Saved places.** Under the switch, name the phone's current coarse location (for example "Home" or
"Office") and Nytka remembers it; a reading within about 150 m of a saved place uses its name
instead of the city-level label. Saved places stay on the phone and are never sent to the server by
themselves, only as the tag a session's place becomes.

## Transcribe on this phone

**Transcribe on this phone** (Device tab, under *On this phone*; off by default) recognises speech on the phone as
well as on your server: free, private and offline. The result is draft text that stays on the phone; the server's
transcript is still the record, and nothing the phone recognises is uploaded yet.

- **Which build.** Only the Google Play build (`play` flavor, `./gradlew installPlayDebug`) has the recogniser,
  [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 1.13.8 (Apache-2.0) over ONNX Runtime (MIT), for arm64
  phones. It is a prebuilt native library that F-Droid does not build from source, so the `oss` build, which is
  also the APK on GitHub Releases, shows the setting with a note and nothing else.
- **The model.** The app never downloads one: its only call to a host that is not your server is the firmware
  check. On a computer, download a sherpa-onnx model and unpack it, for example
  `sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8` (487 MB download, about 670 MB unpacked, CC-BY-4.0, the model the
  server's recogniser runs, 25 European languages including Ukrainian and Russian), or the smaller
  `sherpa-onnx-nemo-fast-conformer-transducer-be-de-en-es-fr-hr-it-pl-ru-uk-20k-int8` (about 110 MB). Copy the
  folder to the phone and tap **Choose model folder**. The app shows the model's size and the free space before it
  copies anything, refuses a copy that would leave less than 200 MB free, and keeps one model at a time. A folder
  needs `tokens.txt` and either `encoder`, `decoder` and `joiner` `.onnx` files (a transducer) or one `.onnx`
  (a NeMo CTC model); int8 files win over full-precision ones.
- **When it runs.** Every 15 minutes while the phone charges, for at most 8 minutes a time. **Also on battery**
  lets it run when the battery is at or above a level you pick (20 to 100 %, 80 by default).
- **What it reads.** While the switch is on, each chunk the queue seals is copied for the recogniser (the newest
  2,000, about 16 hours of speech); uploads are not delayed and nothing about how the queue stores or deletes audio
  changes. Only speech is decoded: the pendant's Opus frames are small when it is quiet, so silence is skipped
  without decoding. Stretches of speech up to 30 s are decoded to 16 kHz with Android's own Opus decoder and
  recognised; each copy is deleted once its text is saved. Switching off deletes the copies still waiting.
- **Drafts** show on the setting's screen, newest first, with their time, and stay on the phone for 7 days.
  **Delete drafts** removes them at once. Their text never goes into the app's log, the debug report or the
  diagnostics upload.
- **Benchmark** (developer mode): times the installed model on the fake pendant's synthetic recording and shows the
  real-time factor, load time, peak memory, battery per hour of recognising (when not charging) and the word error
  rate against the sentence the recording was made from.

