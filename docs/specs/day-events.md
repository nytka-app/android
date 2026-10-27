# Nytka for Android, day events: the app side

The server spec is nytka-app/server [`docs/explanation/features.md#day-events`](https://github.com/nytka-app/server/blob/main/docs/explanation/features.md#day-events)
and `GET /api/v1/events?date=&tz=` (`docs/reference/api.md`). It turns a local day into typed, timestamped
events. This spec says what the app shows and calls: the Timeline gets quiet one-line rows between its scenes.
No new tab, no new screen.

## Done when

1. `GET /api/v1/events` is called once per Timeline day, with `date` (yyyy-MM-dd) and `tz` (the phone's IANA zone), only when `/api/v1/info` lists `events`.
2. Events and scenes make one list, oldest first by time; an event goes before a scene that starts at the same moment. Fragment rows count as the time of their first scene.
3. Each kind has its own one-line wording with the clock time in the phone's zone: joined and left name the person (`voiceLabel`, else "Someone"), media and calls add `detail.category` when there is one, `silence` says how long (`detail.minutes`), `alone` says you were alone.
4. An event tied to a scene that is private (the scene has a `sensitivity`) is not shown, so a name never leaks past "Private · therapy. Tap to show."
5. An event with a `personId` opens that person; the others are plain text.
6. Against a server without `events`, or when the events call fails (any kind), the Timeline shows its scenes as before: no error, no notice, no call made on a server without the feature.
7. An unknown `kind` or a `detail` of an unexpected shape is skipped or ignored; nothing crashes.
8. Every new string is in `values/strings.xml` and `values-uk/strings.xml`; the README and `docs/reference/screens.md` describe the rows.

## Executable checks

| # | Check | Where |
|---|---|---|
| 1 | The request path, `date` and `tz`; a full body decodes; `400` (no field errors) is a failure of kind `Server`, `404` of kind `NotFound` | `core` `EventsApiTest` |
| 2 | `detail` as an object, as a JSON string and absent all read right (`category`, `minutes`) | `core` `EventsApiTest` |
| 3 | Events interleave with scenes and fragments by time; ties put the event first | app `TimelineViewModelTest` |
| 4 | A server without `events` is not asked | app `TimelineViewModelTest` |
| 5 | A failing events call leaves the scenes in place and sets no error | app `TimelineViewModelTest` |
| 6 | Events of a private scene, and unknown kinds, are dropped | app `TimelineViewModelTest` |
| 7 | Stepping to another day asks for that day's events | app `TimelineViewModelTest` |
| 8 | `./gradlew check assembleOssDebug assemblePlayDebug` | CI |

Not run: a device or emulator pass against the live server (rendering of the rows is checked by reading, not
by a screenshot).

## Interpretation

- `source` ("segments", "speech", "context") is not shown: the wording is the same whichever source made the event.
- The events call is optional garnish. It never delays the scenes: the scenes show as soon as they arrive.
- `place_changed` is not computed by the server, so the app has no wording for it.
