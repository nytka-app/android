# Everyday use

Muting, bookmarks, the widget, alerts and the other things you do day to day. Back to the [README](../../README.md).

- **Mute** with a double tap on the pendant: one long buzz means muted, two short buzzes mean
  recording again. **Mute** in the notification, on the status card and on the home-screen widget
  does the same. The notification's title is the capture state, and its status-bar icon is the same
  glyph as in the app, so the shape tells the state even in the status bar; **Open** opens the app. While muted, Nytka hears nothing and the pendant discards its audio, and Nytka
  stays muted when the pendant reconnects. What the pendant records while the phone is away is
  handled by [Offline sync](../explanation/offline-sync.md#offline-sync).
- A **home-screen widget** shows the capture state with its glyph (Recording, Muted, Waiting for
  pendant or Recording off; only Recording is green), pendant battery and the upload queue length. It
  follows your launcher's light or dark theme and your wallpaper's colours, and keeps the recording green;
  tap it to open the app, or tap **Mute**/**Unmute** to toggle recording without opening it.
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
- **Consent chime** (Device screen, off by default): while the pendant records, the phone plays a short
  chime every 5 to 60 minutes (15 by default), at once when recording starts and not on each
  reconnect, so people nearby know. It is silent while muted, disconnected or during voice
  enrollment. It uses no microphone and no permission, and follows the notification volume: on
  silent, or when Do Not Disturb blocks notifications, you will not hear it.
- **Language of summaries** (Device screen): English, Ukrainian or Follow the conversation, editing the
  server's `llm.outputLanguage`. Shown locked, "Set by the server's environment", when the server's
  environment sets it.
- **Names as you type.** Where you name a voice (**Who is this?** in a conversation, an unnamed
  voice in People, a voice card), up to three names sit under the field as chips: the server's own
  suggestions for that voice first, then the people whose name starts with what you typed, then
  those with a later word or a part that matches, the most recently heard first. An empty field
  offers the people heard last. Accents do not matter ("zoe" finds Zoë), and a person known only by a
  role is never offered. Picking a known name joins the voice to that person. In free text (a task,
  a memory, a fact, your note on a person, a bookmark note, Ask) a capitalised word of two letters or
  more gets the names with a word that starts with it ("Call Ol" offers Oleh and Olena Petrenko); a
  tap writes the full name in. Search does the same without the capital. The names come from your
  server once per screen and are not stored on the phone; a server without people offers none.
- **Save a line as a memory.** Press and hold a line in a transcript and choose **Save as a memory**. The text
  opens with the speaker's name in front ("Olena: I will send the contract"; your own lines carry no
  name), up to 300 characters, and you can edit it before **Save**. Known names are offered as you type.
  A fact the server already holds counts as saved. Needs only the Memories API, so any server with the
  Memories tab has it.
- **Ask ideas.** The empty Ask tab offers questions as chips: for the two people heard last "What did
  Olena and I talk about last?" and "What did I promise Olena?", then "What happened yesterday?" and
  "What is still open on my plate?". A tap asks at once. Names come from the server, as for
  [names as you type](#everyday-use); nothing is stored on the phone.
- **Delete** a conversation from its ⋮ menu: its transcript, audio, tasks and memories go from your
  server.
- **After a reboot**, recording starts once the phone has been unlocked and the pendant is in
  range; Nytka need not be opened.
- The **Device** tab shows the pendant and the server, and holds the settings: server address,
  token and the private-network switch.
- **Diagnostics** (Device → Developer mode) record link quality, queue and upload health every
  10 seconds while Nytka records, and keep the last 7 days. A sample also holds the pendant's signal
  strength (`rssi`, dBm), the status of the last link loss (`lastDisconnectStatus`; 147 is a link that went
  silent), and the phone's own battery level and charging state. The app's log names that same status (and the
  other GATT and link statuses Android reports) next to the number, for example `onConnectionStateChange
  status=147 (LINK_WENT_SILENT) newState=0`. **Export diagnostics** shares them as a
  CSV file through Android's share sheet. **Send diagnostics to my server** is off by default; on, it
  uploads the samples to your server every minute (needs server 0.2.0 or later).
  The app's own log events (tags such as `OmiPendant`, `CaptureController`) are kept and uploaded with
  them while Nytka records; they hold no audio, transcripts, server address or token. Read them on the
  server with `select at, payload->>'level', payload->>'tag', payload->>'message' from diagnostics
  where payload->>'kind' = 'log' order by at`. The export and the sample count leave them out.
