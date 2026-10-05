# Nytka Android Speech Kind Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show each transcript line's kind (media, call, guesses in shadow mode), let the owner mark lines and whole
conversations, filter media out of the list, answer `speech` items in the review inbox, and send the phone's own
playback and call times as context ranges.

**Architecture:** One new client `SpeechClient` in `:core` (`core/api`), so the fakes of `ConversationsClient` stay;
optional fields on existing models; the range rules in a pure-Kotlin `ContextRangeTracker` in `:core`, fed by an
`:app` recorder that only listens to `AudioManager` callbacks; a Room outbox and an uploader shaped like the
bookmark ones.

**Tech Stack:** as the app today (CLAUDE.md, "Stack"); no new dependency, no new permission.

**Spec:** [`docs/specs/speech-kind.md`](../specs/speech-kind.md). Server: nytka-app/server `docs/specs/speech-kind.md`
and its plan `docs/plans/2026-10-05-speech-kind.md` (tasks S-1 to S-6). Each task below can be built against the
server spec with MockWebServer; it merges after the server task whose routes it calls.

## Global Constraints

- CLAUDE.md invariants hold, above all 6 (no transcript text, token or server address in logs or the debug report)
  and 7 (never `RECORD_AUDIO`). No new `<uses-permission>` at all.
- Do not add methods or parameters to `ConversationsClient`.
- Every string in `values/strings.xml` **and** `values-uk/strings.xml`, appended at the end under a header comment
  naming the task (`<!-- Speech kind: marks -->`); `StringsParityTest` checks both.
- Gate on `/api/v1/info` features: `speech-kind` (A-S1, A-S3, A-S4), `context-ranges` (A-S2).
- Tests: JUnit 4, hand-written fakes, `MainDispatcherRule`, Turbine, MockWebServer for `:core`, Robolectric for the
  Room migration.
- Each PR: `./gradlew ktlintFormat`, then `./gradlew check assembleOssDebug assemblePlayDebug` green; conventional
  commit; the README section of what it changes.

## Review Focus

1. **No permission, no app name.** The manifest diff is empty; ranges hold kind, route and times only.
2. **Callbacks, not polling.** No timer reads `AudioManager`; the only timer is the uploader's 60 s tick.
3. **Older servers.** No feature flag, no UI and no upload (tests per view model and for the uploader).
4. **Strings** in both languages.

## Tasks and parallel work

| Wave | Tasks, each in its own worktree | Starts after (merge order) |
|---|---|---|
| 1 | A-S1, A-S2 | A-S1: server S-1; A-S2: server S-2 |
| 2 | A-S3 | A-S1 |
| 2 | A-S4 | A-S1 and server S-5 |

**Shared files**; rebase on `main` before merging and keep both sides:

| File | Tasks |
|---|---|
| `res/values/strings.xml`, `res/values-uk/strings.xml`, `README.md` | all (append only) |
| `core/api/ApiModels.kt`, `SpeechClient.kt`, `app/di/SpeechModule.kt` | A-S1 (A-S2 adds `sendRanges` there) |
| `ui/conversations/ConversationScreen.kt`, `ConversationViewModel.kt` | A-S1 |
| `ui/conversations/ConversationsScreen.kt`, `ConversationsViewModel.kt` | A-S3 |
| `ui/people/review/ReviewViewModel.kt`, `ReviewScreen.kt`, `core/api/PeopleModels.kt` | A-S4 |
| `capture/CaptureService.kt`, `core/queue/QueueDatabase.kt`, `core/schemas/` | A-S2 |

## File Structure

```
core/src/main/kotlin/io/github/nytka_app/core/
  api/SpeechClient.kt          marks (segment, conversation), ranges upload                    (A-S1, A-S2)
  context/ContextRangeTracker.kt  events -> closed ranges, pure Kotlin                         (A-S2)
  queue/ContextRow.kt, ContextOutboxDao.kt                                                     (A-S2)
  upload/ContextUploader.kt                                                                    (A-S2)
app/src/main/kotlin/io/github/nytka_app/
  di/SpeechModule.kt                                                                           (A-S1)
  capture/PhoneContextRecorder.kt   AudioManager callbacks -> tracker                          (A-S2)
  ui/conversations/SpeechChip.kt                                                               (A-S1)
  ui/device/PhoneContextSection.kt                                                             (A-S2)
```

### Task A-S1: models, client, chips and marks

**Files:**
- Create: `core/.../api/SpeechClient.kt`; `app/di/SpeechModule.kt`; `ui/conversations/SpeechChip.kt`
- Modify: `core/.../api/ApiModels.kt` (`ServerInfo.FEATURE_SPEECH_KIND`; `Segment.speechKind: String? = null`,
  `speechGuess: String? = null`, `speechScore: Double? = null`, `speechMarked: Boolean = false`;
  `ConversationSummary.mediaShare: Double = 0.0`), `ConversationScreen.kt` (chip, menu items, ⋮ items),
  `ConversationViewModel.kt` (inject `SpeechClient`; `markKind(segmentId, kind?)` optimistic with rollback;
  `markOthers(kind?)` then reload; `speechAccess` from `/info`)
- Test: `core/src/test/.../api/SpeechApiTest.kt`; `app/src/test/.../ui/conversations/ConversationSpeechTest.kt`;
  new `FakeSpeech.kt`; the existing view-model tests gain the constructor argument only

**Interfaces** (all `suspend`, all `ApiResult`): `markSegment(id: Long, kind: String?): Segment`
(`PATCH api/v1/segments/{id}` `{ "speechKind": kind }`); `markConversation(id: String, kind: String?): Int`
(`POST api/v1/conversations/{id}/speech` `{ "kind": kind }`, answer `{ marked }`).

Steps:
- [ ] MockWebServer tests first: paths, methods, bodies (a null kind is sent as JSON `null`, not omitted); decoding a
  segment without the new fields gives the defaults; `400`, `403`, `404` map to their kinds.
- [ ] View-model tests: without `speech-kind` no chip, no menu item, no call; a mark updates the line at once and rolls
  back on failure with a notice; a guess without a kind shows the outlined chip; `markOthers` reloads.
- [ ] Implement; strings both languages; README: "Conversations" (kinds, marks).

### Task A-S2: phone context

**Files:**
- Create: `core/.../context/ContextRangeTracker.kt`, `core/.../queue/ContextRow.kt`, `ContextOutboxDao.kt`,
  `core/.../upload/ContextUploader.kt`, `app/.../capture/PhoneContextRecorder.kt`,
  `app/.../ui/device/PhoneContextSection.kt`; schema `core/schemas/.../6.json`
- Modify: `QueueDatabase.kt` (version 6, `AutoMigration(5, 6)`, the new entity), `SpeechClient.kt`
  (`sendRanges(items): RangeAnswer`), `CaptureService.kt` (start and stop the recorder and the uploader with
  capture), `Settings.kt`/`SettingsStore.kt` (`phoneContext`, default true), `DeviceTab.kt` (the section), developer
  screen (last 20 ranges)
- Test: `ContextRangeTrackerTest.kt` (JVM), `QueueDatabaseMigrationTest.kt` (5 to 6), `ContextUploaderTest.kt`,
  `SpeechApiTest.kt` (ranges), `PhoneContextSectionTest` or its view-model test

**Tracker contract** (events in, closed ranges out, a clock injected): `onPlayback(activeUsages: Set<Usage>,
mediaOnSpeaker: Boolean, musicVolume: Int)`, `onMode(mode: Mode)`, `onCommunicationDevice(route: Route)`,
`onStop()`. Media opens when an active usage is media, game or unknown, the media route is the loudspeaker and the
volume is above 0; it closes 2 s after that stops being true, or at once on a route change. A call opens on
`IN_CALL`, `IN_COMMUNICATION`, `CALL_REDIRECT`, `COMMUNICATION_REDIRECT` and closes on `NORMAL`, with the route held
longest. Ranges under 3 s are dropped; over 12 h are cut; `onStop` closes what is open.

Steps:
- [ ] Tracker tests first with scripted events: a 10 s video on the speaker gives one media range; the same on
  headphones gives none; volume 0 gives none; a pause of 1 s keeps one range, of 3 s gives two; a Telegram-style
  `IN_COMMUNICATION` call that switches to the speaker after 5 s gives one call range with route `speaker`; stop
  closes an open range; a 2 s blip is dropped.
- [ ] Room 5 to 6 migration test; uploader tests as `BookmarkUploaderTest` (200 deletes, 400 drops, 401/403/404/405
  pause, nothing sent without `context-ranges` or with the switch off, at most 500 per request, rows older than 7 days
  deleted).
- [ ] `PhoneContextRecorder`: registers the three callbacks on start, unregisters on stop, reads volume and route only
  inside callbacks; no logging of times. No test with a real `AudioManager`; the tracker carries the logic.
- [ ] Strings both languages; README: "What the app sends" (ranges), "Device".

### Task A-S3: list chip and filter

**Files:** Modify `ConversationsScreen.kt` (row chip; **Hide media** action and chip), `ConversationsViewModel.kt`
(`hideMedia` in `SavedStateHandle`; lists with `media=hide` through `SpeechClient.conversations(...)` so
`ConversationsClient` stays); Test `ConversationsViewModelTest.kt`

Steps:
- [ ] Tests: a row with `mediaShare` 0.8 shows the chip, 0.79 does not; the filter reloads with `media=hide` and pages;
  without `speech-kind` no action.
- [ ] Implement; strings; README.

### Task A-S4: review inbox

**Files:** Modify `core/.../api/PeopleModels.kt` (`ReviewProposal.speechKind: String? = null`), `ReviewViewModel.kt`
(kind `speech` accepted by `ReviewRow.of`), `ReviewScreen.kt` (the question by guess, Yes and No, tap opens the
conversation); Test `ReviewViewModelTest.kt`

Steps:
- [ ] Tests: a `speech` item renders with its lines; Yes calls `.../speech/{id}/accept`, No `.../reject`, the row leaves
  at once and returns on failure; an older server's inbox is unchanged.
- [ ] Implement; strings; README: "Review inbox".
