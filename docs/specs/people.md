# Nytka for Android, people: the app side of the People milestone

The server side is merged in nytka-app/server: [`docs/specs/people.md`](https://github.com/nytka-app/server/blob/main/docs/specs/people.md)
and its plan's section "App (android, not designed here)". This spec says what the app shows and calls.
The scope was set by the owner; this document only decides how. How it is built:
[`docs/plans/2026-10-05-people.md`](../plans/2026-10-05-people.md).

This repository had no `docs/specs/` until now: specs lived in the server repository. A spec about the
app alone lives here.

## Done when

1. A **People** tab lists people with name, when they were last heard and how many facts they have.
2. A **person page** shows the note (editable), facts (add, edit, delete, each with its basis), recent
   conversations and open tasks, and has Rename, Merge and Delete (with the option to forget the voice).
3. **"Who is this?" cards** sit at the top of the People tab when the server lists `voice-groups`: each
   plays its clip and can be named, confirmed, skipped or rejected.
4. A **review inbox** lists name suggestions, voice matches and low-confidence "is this you" labels,
   each with Accept and Reject.
5. A conversation with a pending **name suggestion** shows a banner with Accept and Reject.
6. With **meeting briefs** switched on (off by default), a brief the server made posts one
   notification, from a WorkManager job every 15 minutes.
7. With the **consent chime** switched on (off by default), the phone plays a short chime every N
   minutes (5 to 60) while the pendant records. No microphone is used.
8. `people.suggestNames` and `people.voiceMatching` are switched in the existing Server settings screen,
   reachable from the People tab, with the legal-duty sentence under voice matching.
9. A transcript line shows its person's name; tapping a named run opens that person.
10. Every new string is in `values/strings.xml` and `values-uk/strings.xml`.
11. Against a server without a route, each screen says "This server needs an update" or hides the
    entry, as the table in "Older servers" says. Nothing crashes.

## What exists today

Verified against `main` at `4cc643d` (app) and server `main` at `9b649f9`.

- **People screen under Device** (`ui/people/PeopleScreen.kt`, `PeopleViewModel.kt`), reached from the
  Device tab's People section (`ui/device/DeviceTab.kt`, route `people`). It lists people sorted by
  name, unnamed voices (`GET /api/v1/voices`), and per person an actions dialog: Rename, Merge, Delete,
  Delete and forget (`DELETE /people/{id}?forget=true`).
- **`PeopleClient`** (`core/api/PeopleClient.kt`): `people`, `voices`, `renamePerson`, `nameVoice`,
  `mergePerson`, `deletePerson`, `forgetPerson`. `Person` has `id, name, createdAt, voices, segments`.
  `PeopleViewModelTest` implements the interface in a private fake, so adding methods to `PeopleClient`
  breaks that fake.
- **Transcript labels** (`ui/conversations/ConversationViewModel.kt`, `paragraphsOf`): a line shows
  "Me" when `isUser`, else `personName`, else the provider's `speaker`. Since server P-1, `personName`
  follows the server's three-step label rule, so item 9's label already shows; only the tap is new.
- **Marks on a line** (A-M, `markSegment`): long-press gives This is me / This is not me / Clear.
- **Enrollment** (A-E, `ui/voice/`): a view model that emits typed notices (`VoiceNotice`) which the
  screen turns into string resources. New screens copy this; older view models still hold English text.
- **Features and scope.** Each view model calls `InfoClient.info()` itself (`DeviceViewModel`,
  `FirstRunViewModel`, `StorageSyncController`); there is no shared cache. The app refuses a `read`
  token (`FirstRunViewModel.kt:151`, `DeviceViewModel.kt:169`). `ServerInfo` knows `offline-sync` and
  `voice` only.
- **Errors** (`ui/ServerNotices.kt`): 404 or 405 on a list is "This server needs an update", 404 on an
  item "This item no longer exists", 403 "The app needs an admin token."
- **Audio**: `ExoAudioPlayer` streams `GET /conversations/{id}/audio` through OkHttp with the bearer
  header (`NytkaApi.authorized`), no disk cache. Its interface `AudioPlayer.prepare(conversationId)`
  is tied to conversations.
- **Server settings** (`ui/developer/server/`, developer mode only) list every key the server sends,
  grouped by prefix, with switches for `bool`, `Set`/`Not set` for secrets, and a `400`'s field
  messages under the field. `people.*` and `calendar.*` already appear there.
- **Notifications**: channels `recording` and `alerts` (`alerts/Notifier.kt`, names hard-coded in
  English); `POST_NOTIFICATIONS` is asked only in first run; `Notifier.canPost` checks it.
- **WorkManager**: `UploadDrainWorker` is a `@HiltWorker` with a unique 15-minute periodic job,
  scheduled in `NytkaApp.onCreate`.
- **Tabs**: `AppTab` has five entries with English labels in code; `AppTabTest` asserts "the five tabs
  of the vision". Search (`ui/search/`) sends no `kinds` for "All", so a new server already returns
  `person` hits there; they have no conversation and open nothing.
- **Flavors** `oss` and `play` have no source sets of their own (`app/src` holds `main` and `test`).

## Screens

### People tab

A sixth tab, **People**, between Memories and Ask. Six items leave about 60 dp each on a 360 dp phone,
under Material's five-item guidance, so the bar sets `alwaysShowLabel = false` (the selected tab shows
its label) and every tab label moves into string resources. The People screen moves out of Device into
this tab; Device keeps a one-line pointer.

- **Top bar:** title, a review-inbox icon with a count badge (hidden while the inbox route is missing
  or empty), and ⋮ with **People settings**.
- **Cards** (below) when the server lists `voice-groups`.
- **People**, most recently heard first, never-heard last, then by name: name, last seen ("Today
  14:03", "Yesterday", "3 Oct"), fact count ("4 facts"). A row opens the person page.
- **Voices not named yet** as today (tap to name).
- Pull to refresh. The list is read on every visit; nothing is cached on the phone.

`GET /api/v1/people` has no `lastSeenAt` and no fact count today (only MCP `list_people` has them, see
Conflicts 1). The app reads both as optional fields; without them it sorts by name and shows the
segment count instead.

### Person page

Route `person/{id}` in the People tab's own NavHost; other tabs open it through `onOpenPerson(id)`, as
they open a conversation today.

- **Header:** name, last seen, "Voice model: yes, from N lines" when `hasVoiceprint` (no vector exists on
  the phone; the count is `voiceprintSamples`).
- **Note:** your own text, up to 500 characters, edited in place; Save sends `PATCH { note }`, an
  emptied field sends `note: null`. The model never reads or writes it, and the screen says so once.
- **Facts**, newest first, 50 from the page, more with `GET /people/{id}/facts?before=`. Each shows its
  text, its basis as a word ("They said", "Said about them", "Mentioned", "Added by you"), and its
  conversation's title (tap to open). **+** adds (`409`: "They already have this fact"); ⋮ edits or
  deletes (asks first; a deleted fact never comes back from later conversations).
- **Open tasks** owed to them: text and conversation; tap opens the conversation.
- **Conversations**: the newest 10, title and day; tap opens one.
- **⋮:** Rename (`409`: "Someone is already called X"), Merge into…, Delete, Delete and forget voice.
  "Forget voice" asks the *transcription service* to drop its voiceprints (v0.6's `forget=true`);
  Nytka's own voiceprint of the person goes with any delete. The dialog says both.

### "Who is this?" cards

Shown at the top of the People tab, only when `/info` lists `voice-groups` (which the server sets only
while `people.voiceMatching` is on, the speaker model is there and audio is kept a day or more). At
most 4, as the server sends them.

- **Group card** ("Who is this?"): conversation title, up to three lines of text, a play button for
  the clip (at most 10 s), a name field with the existing people as suggestions, and **Save**,
  **Skip** (hidden 7 days), **Not a person**. Picking an existing person sends `{ personId }`; a typed
  name sends `{ name }` (the server reuses a person of that name).
- **Match card** ("Is this Olena?"): the same, with **Yes** (`{ personId }` of the card), **Not them**
  (`{ reject: true }`), **Skip**.
- The clip streams through ExoPlayer from `GET /people/cards/{kind}/{id}/clip` with the bearer header,
  no cache, and stops when the card leaves the screen. A `404` says "The audio of this card is gone"
  and the card list is read again.
- After any answer the card list and the people list are read again.

### Review inbox

Route `review` in the People tab. `GET /api/v1/review?limit=200`, newest first, one row per item:

| Kind | Row | Accept | Reject |
|---|---|---|---|
| `name` | "Is this {name}?" and the line that carries it | names the voice | never suggested again |
| `voice` | "Is this {name}?" with similarity as a percentage, its lines, no clip | links the lines | "Not them" |
| `label` | "Did you say this?" or "Is this someone else?", per `proposal.isUser` | stores Nytka's verdict as your mark | stores the opposite |

Each row opens its conversation on tap. Accept and Reject remove the row at once and restore it with a
snackbar if the server refuses; `409` means it was already answered (row goes, list reloads).

### Name-suggestion banner

The conversation screen reads `GET /people/suggestions` (pending) and keeps those with its
`conversationId`; at most one banner shows, the most confident: "{label} may be {name}" with the
evidence line, **Accept** and **Not {name}**. `{label}` is the evidence segment's current label, or
"A voice". Accept reloads the conversation, since labels change. Any failure to read suggestions hides
the banner silently.

### Transcript labels

Unchanged rule. A run whose segment has a `personId` gets a tappable label (an `onClickLabel` for
TalkBack) that opens the person page.

### Settings

- **Server side.** The existing Server settings screen gains an optional prefix filter; the People tab's
  "People settings" opens it filtered to `people` (`people.suggestNames`, `people.facts`,
  `people.voiceMatching`, `people.voiceThreshold`), reachable without developer mode. Under
  `people.voiceMatching` it shows: "Recording people without their consent is illegal in some places.
  Grouping voices keeps a voice model of each person you name; turn it on only where you may." The
  server's `400` ("Enroll your voice first.") shows under the switch as today; it is English, from the
  server.
- **On the phone**, in the Device tab's settings: **Meeting briefs** (switch, off) and **Consent chime**
  (switch, off, with an interval of 5 to 60 minutes, default 15).

### Meeting briefs

- **Switch on:** on Android 13 and later without `POST_NOTIFICATIONS`, ask for it with the existing
  `PermissionAnswer` flow; refused twice shows "Open settings". Granted, the setting is saved and a
  unique periodic job `briefs` (15 minutes, network required) is scheduled; switching off cancels it.
  `NytkaApp.onCreate` reschedules it when the setting is on.
- **Under the switch:** "Your server has no calendar feed" when `GET /settings` shows `calendar.icsUrl`
  not set; "This server needs an update" when the key is missing.
- **The job** reads `GET /briefs/upcoming?minutes=240` (240 is the largest `calendar.briefMinutes`),
  and posts one notification per `brief.id` not yet posted, for events not over. Posted ids are kept in
  settings, trimmed to those still listed, at most 50. A failure returns `Result.success()` and waits
  for the next run: a brief is not worth a retry storm.
- **The notification**, channel "Meeting briefs" (default importance): title "Brief: {event title}",
  start time, the brief in `BigTextStyle`. Visibility private, with a public version "A meeting brief
  is ready" so the lock screen shows no name or fact. Tap opens the app.
- A brief is made `calendar.briefMinutes` (default 30) before the meeting and polled every 15 minutes,
  so it arrives 15 to 30 minutes before by default; with 5 it may arrive after the start.

### Consent chime

- `ConsentChime` runs inside `CaptureService` and watches `CaptureStatus.recording`, the enrollment
  capture and the two settings. While recording and not enrolling, it plays once when at least N
  minutes passed since the last chime, then every N minutes. A flapping link does not chime on each
  reconnect.
- The sound is `ToneGenerator` (`TONE_PROP_ACK`, about 200 ms) on the notification stream: no asset,
  no microphone, no permission. It follows the notification volume; on silent it is not heard, and the
  setting says so.
- Never during enrollment, since the pendant would hear it in the reading.

## Decisions

**Token scope.** The app keeps requiring `admin`, as it does today. Reads (`/people`, `/people/{id}`,
facts, suggestions, `/review`, `/briefs/upcoming`) take `read`; every answer and edit, and both card
routes (also the `GET`s), need `admin`. A `403` shows "The app needs an admin token." The brief job uses
the same stored token.

**Older servers.** Feature flags do not tell releases apart: `people` arrived in 0.13.0 with only P-1,
the person page, facts, suggestions and cards in 0.14.0, review and briefs after 0.14.0 (unreleased on
2026-10-05). So the app gates on the flag where one exists and on each call's answer elsewhere, with no
version comparisons:

| Server | People tab | Person page | Cards | Inbox | Banner | Briefs |
|---|---|---|---|---|---|---|
| before 0.7 | needs update | no | no | no | no | needs update |
| 0.7 to 0.12 (no `people`) | list, old actions dialog | no | no | icon hidden | no | needs update |
| 0.13 (`people`) | list | `404` with the person still listed: needs update | no | icon hidden | no | needs update |
| 0.14 | list | yes | with `voice-groups` | icon hidden (`404`) | yes | needs update |
| main after 0.14 | list | yes | with `voice-groups` | yes | yes | yes |

A `404` on `GET /people/{id}` reads the list again: the person still listed means an old server, gone
means "This item no longer exists".

**Empty, error and offline states.** Every screen has loading, empty ("Nobody is named yet", "Nothing
waits for you", no cards section at all), error with **Retry**, and refresh on pull. Offline is an error
like any other: nothing is cached or queued on the phone, an edit that fails stays in its field with the
message. The brief job requires a network; the chime needs none.

**Accessibility.** Text buttons, not icon-only actions, on cards and inbox rows; icons have content
descriptions; the play button says "Play clip" or "Pause clip"; touch targets at least 48 dp; the basis
is a word, not a color; snackbars for results; the badge count is in the inbox icon's description.

**Privacy.**
- Never log names, notes, facts, lines, brief text, event titles or clip URLs (invariant 6 of
  CLAUDE.md). Failures carry the fixed sentences of `ServerNotices` or typed notices.
- No biometric data on the phone: the server never sends a voiceprint, centroid or fingerprint, and the
  app gets only `hasVoiceprint` and a count. The clip is streamed for playback and never written to disk.
- The lock screen shows no brief content (public version of the notification).
- Nothing new leaves the phone; every call goes to the user's server.

**Strings.** Every new UI text, notification text and channel name is a resource in both languages. View
models emit typed results (`PersonNotice`, `CardNotice`, ...) that the screen turns into text, as
`VoiceNotice` does. Plurals for counts ("1 fact", "4 facts"; Ukrainian one, few, many, other).

**Flavors.** No difference between `oss` and `play`: no new permission beyond `POST_NOTIFICATIONS`
(already declared), no Google service, no exact alarm.

## Conflicts between the server API and the app

1. **The list lacks last seen and fact count.** `GET /api/v1/people` returns `PersonRow` (`id, name,
   note, createdAt, voices, segments`); `PeopleStore.SummariesAsync` computes `lastSeenAt` and `facts`
   but only MCP `list_people` uses it. Asked of the server: add both fields to `GET /api/v1/people`
   (additive). Until then the app sorts by name and shows segment counts; N+1 calls to
   `/people/{id}` were rejected.
2. **Feature flags do not mark releases.** See "Older servers". Asked of the server: list `review` and
   `briefs` under `features` so the app can hide entries instead of probing.
3. **No calendar flag.** The brief switch reads `calendar.icsUrl` from `GET /settings` (admin) to say
   whether a feed is set.
4. **Server field messages are English.** A `400` on a setting (such as "Enroll your voice first.")
   shows as the server wrote it, also in the Ukrainian UI (invariant 6 allows field messages).
5. **No `conversationId` filter on suggestions.** The banner reads all pending suggestions (at most 200)
   and filters on the phone.
6. **Two forgets.** `DELETE /people/{id}?forget=true` asks the transcription service, while Nytka's own
   voiceprint goes with any delete and `DELETE /people/voiceprints` drops all of them. The app offers
   only the per-person options; "forget every voice model" stays a server call.
7. **Six tabs.** The vision (server `docs/vision.md`, "five tabs") and `AppTabTest` say five. The owner
   chose a People tab; the vision line and the test change with it.
8. **Search already returns people.** A new server answers "All" with `person` hits that open nothing in
   today's app. A-P6 opens them as person pages.

## Out of scope

- Adding a person by hand, marking a single line as a person (`PATCH /segments/{id}` with `personId`),
  setting a task's person, `voiceThreshold` beyond the generic settings field.
- A brief screen in the app, calendar settings (the ICS URL is environment-only on the server).
- `DELETE /people/voiceprints` and the voice evaluation route.
- Localizing older hard-coded English (view-model messages, notification channels of v0.1) beyond the
  tab labels A-P2 touches.
- Any cache of people, facts or briefs on the phone.

## Review focus

1. **Nothing applies itself.** Every name, match or label change in the app follows a tap on Accept,
   Save, Yes or a card answer; no code calls an accept route on load.
2. **No personal text in logs** or in the debug report; the notification's public version holds none.
3. **Older servers** follow the table: a `404` never crashes or shows "item gone" for a missing route.
4. **The chime** never plays while muted, disconnected or enrolling, and never touches the microphone.
5. **Strings** exist in both files with the same names (`./gradlew lint` warns on a missing
   translation; the plan adds a unit test that compares the two files' names).
