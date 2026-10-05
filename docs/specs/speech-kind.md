# Nytka for Android, speech kind: the app side

The server spec is nytka-app/server [`docs/specs/speech-kind.md`](https://github.com/nytka-app/server/blob/main/docs/specs/speech-kind.md):
every transcript line gets a kind (`person`, `media`, `call`, `unsure`) with a score, the owner can mark any line or
every other voice of a conversation, and in `on` mode media stops feeding names, voice groups, facts, tasks and
memories. This spec says what the app shows, what it sends and what it calls. How it is built:
[`docs/plans/2026-10-05-speech-kind.md`](../plans/2026-10-05-speech-kind.md).

## Done when

1. A transcript line whose kind is `media` or `call` shows a chip ("Media", "Call") and muted text; in shadow mode
   a guess shows as an outlined chip with a question mark ("Media?").
2. Long-press on a line offers "Person here", "Media", "Call" and "Clear" beside "This is me" and "Not me"; the
   conversation menu offers "Other voices are media" and "Other voices are people".
3. Conversation rows show a "Media" chip when `mediaShare` is 0.8 or more, and the Conversations list has a
   "Hide media" filter.
4. The review inbox shows `speech` items ("Was this a TV or a video?") with Yes and No.
5. While capture runs, the app records when the phone itself plays media through its loudspeaker or is in a call,
   as time ranges, and sends them to the server; the Device tab has a "Phone context" switch, on by default.
6. No new permission. Every new string is in `values/strings.xml` and `values-uk/strings.xml`.
7. Against a server without `speech-kind` or `context-ranges`, the app shows no chip, menu entry or filter, sends no
   range, and nothing crashes.

## What exists today

Verified against app `main` at `53debfd`:

- `Segment` (`core/api/ApiModels.kt:82-100`) has `isUser`, `isUserSource`, `personId`, `personName` and no kind.
  JSON is decoded with `ignoreUnknownKeys` and `coerceInputValues` (`NytkaApi.kt:33-37`), so new optional fields
  are safe on an older server.
- The line menu: long-press the text (`ConversationScreen.kt:389-409`) offers This is me, Not me and Clear;
  `ConversationViewModel.markSegment` (`:514-531`) updates optimistically and rolls back on failure; it sends
  `PATCH api/v1/segments/{id}` with `isUser`.
- `ReviewViewModel` drops review kinds it does not know (`:54-77`, `:172`), so the server's new kind is invisible to
  today's app.
- Bookmarks are the precedent for small records the phone sends: a Room outbox (`BookmarkRow`), an uploader
  (`BookmarkUploader.run`, `:62-73`) and a client with a client-made id (`BookmarksClient`, `:28-42`): 200 deletes,
  400 drops, 401/403/404/405 pause.
- Room is at version 5 with automatic migrations only (`QueueDatabase.kt:26-34`); a schema change adds version 6
  and its test.
- The only `AudioManager` use is the consent chime's notification stream (`Chime.kt`). The capture service is a
  `connectedDevice` foreground service (`AndroidManifest.xml:47-50`). The app never holds `RECORD_AUDIO`
  (invariant 7).

## Screens

### Transcript

A line whose `speechKind` is `media` shows a small "Media" chip after the speaker and its text in the muted color;
`call` shows "Call" and normal text. With no `speechKind` but a `speechGuess` of `media` or `call` (shadow mode), the
chip is outlined and reads "Media?" or "Call?". `unsure` and `person` show nothing. TalkBack reads the chip as
"Media" or "Probably media".

### Marking

The long-press menu gains a divider and four items: **Person here**, **Media**, **Call** and **Clear kind**. A tap
sends `PATCH api/v1/segments/{id}` with `speechKind` and updates the line at once, rolling back with a snackbar on
failure, as the wearer mark does. The conversation's ⋮ menu gains **Other voices are media** and **Other voices are
people**, which send `POST api/v1/conversations/{id}/speech` and reload the conversation.

### List

A conversation row with `mediaShare` of 0.8 or more shows a "Media" chip. The Conversations top bar gains **Hide
media** (a filter chip under the bar while on), which reloads the list with `?media=hide`; it is kept in the view
model's saved state, not across launches.

### Review inbox

Kind `speech`: "Was this a TV or a video?" (or "Was this a call?" for a `call` guess) with the stretch's lines,
**Yes** and **No**; a tap on the item opens the conversation. Answers go through `POST /review/speech/{id}/accept`
and `/reject` as the other kinds do.

### Device tab

A **Phone context** switch under capture settings: "Tell your server when this phone plays sound through its
speaker or is in a call. Only times are sent, never what played." Developer mode lists the last 20 ranges sent
(kind, route, start and length).

## Phone context

`PhoneContextRecorder` (`:app`, `capture/`) starts and stops with the capture service. It uses only calls that need
no permission:

- `AudioManager.registerAudioPlaybackCallback`: the active players' usages. A **media** range is open while a player
  with usage media, game or unknown is active, the route for media is the built-in loudspeaker
  (`getAudioDevicesForAttributes` on Android 13 and later, the output device list on 12) and the music stream volume
  is above 0. It closes 2 s after the last such player stops, or at once when the route leaves the loudspeaker.
- `AudioManager.addOnModeChangedListener`: a **call** range is open while the mode is `IN_CALL`,
  `IN_COMMUNICATION`, `CALL_REDIRECT` or `COMMUNICATION_REDIRECT`; its route is the communication device's type
  (`speaker`, `earpiece`, `headset`, `bluetooth`, `other`), updated by `addOnCommunicationDeviceChangedListener`
  and kept as the longest-held route of the call.

The rules that turn those events into ranges live in `:core` (`ContextRangeTracker`, pure Kotlin, tested on the JVM
with scripted events); the `:app` class only feeds it events and the clock. A range shorter than 3 s is dropped, a
range open when capture stops closes at that moment, and a range longer than 12 h is cut at 12 h.

Closed ranges go to Room (`context_outbox`: `id`, `kind`, `route`, `startMs`, `endMs`) and `ContextUploader` sends
them in batches of up to 500 to `POST /api/v1/context/ranges` every 60 s while the switch is on and `/info` lists
`context-ranges`, with the bookmark uploader's answers (200 deletes, 400 drops the batch, 401/403/404/405 pause).
Ranges older than 7 days that never went out are deleted.

## Decisions

**One client.** `SpeechClient` (`core/api`): `markSegment(id, kind)`, `markConversation(id, kind)`,
`sendRanges(items)`. The marks do not go through `ConversationsClient`, so its fakes stay; the segment and list
models gain optional fields only: `Segment.speechKind`, `speechGuess`, `speechScore`, `speechMarked`,
`ConversationSummary.mediaShare`.

**Feature gates.** Chips, marks, the filter and the inbox kind need `speech-kind`; recording and sending ranges need
`context-ranges`. Without them the parts are hidden and nothing is recorded.

**No permission, no app names.** Android does not tell a non-system app which app plays, and Nytka does not ask:
no notification access, no usage access, no `READ_PHONE_STATE`, no `RECORD_AUDIO`. A range holds a kind, a route and
two times.

**Battery.** Callbacks only, no polling: the recorder reads the volume and the route when a callback fires.

**Privacy.** Ranges say when the phone made sound or was in a call; they go only to the owner's server. Nothing about
them reaches a log line, the debug report or a notification beyond counts. The README's "What it sends" says so.

**Strings.** Both languages, under a header comment per task. Ukrainian: "Медіа", "Медіа?", "Дзвінок", "Дзвінок?",
"Тут людина", "Медіа", "Дзвінок", "Прибрати позначку", "Інші голоси — медіа", "Інші голоси — люди", "Сховати
медіа", "Це був телевізор чи відео?", "Це був дзвінок?", "Контекст телефона".

## Conflicts between the server API and the app

1. **Older apps and the inbox.** Today's app drops `speech` items; nothing is answered blind.
2. **A mark on a wearer line.** The server allows marking any line; the app offers the kind items on every line,
   including "Me" lines, so a TV voice the label rule took for the wearer can be fixed.

## Out of scope

- Recording or analysing the phone's own audio; app names, titles, artists.
- Cast, smart-TV or Home Assistant state.
- Hiding media lines from the transcript: they stay, muted.
