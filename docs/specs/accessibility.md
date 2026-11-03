# Nytka for Android, accessibility

Nytka is worn and read by people who may not see the screen, may not hear it, may not tap small targets or may run
the phone at the largest text size. This spec says what "accessible" means for the app in checks a machine runs on
every pull request, so it stays true as screens change. The wider track it belongs to (more recorders, more people):
[`multi-device.md`](multi-device.md). The target is WCAG 2.2 level AA where it applies to a native app, plus
Android's own rules (48 dp touch targets, TalkBack, font scale).

Verified against app `main` at `6aa146a` on 2026-11-03. Nothing here was run on a phone with TalkBack: every
check below runs on the JVM under Robolectric. A short manual pass with a real TalkBack is listed at the end and is
not a gate.

## Done when

1. **Names.** On each covered screen, every node that can be clicked, toggled, long-pressed or set has a name a
   screen reader can speak: text or a content description in its merged semantics. An icon that carries meaning
   has a description; a decorative icon has `contentDescription = null` and is not focusable on its own.
2. **Roles and state.** A switch, checkbox, radio button, tab, button, slider and text field exposes the matching
   `Role`, and a toggle exposes its `ToggleableState` (a selected tab or chip exposes `selected`). No node has a
   click action and no role unless it is a list row that opens something, which reads as a button.
3. **Touch targets.** Every node with a click, toggle or long-press action has a touch target of at least 48 dp by
   48 dp (`assertTouchHeightIsAtLeast`, `assertTouchWidthIsAtLeast`), counting the padding Compose adds around a
   control. The visible shape may be smaller.
4. **Font scale 2.0.** With `fontScale = 2.0` (and again at 1.3), each covered screen: clips no text whose
   `maxLines` is unset (`TextLayoutResult.didOverflowHeight` and `didOverflowWidth` false); never needs sideways
   scrolling (no node wider than the screen); keeps every action reachable (`performScrollTo` then
   `assertIsDisplayed` for each node of check 3); and keeps the top bar's capture status and the bottom bar's tab
   names readable (text, not an icon alone).
5. **Contrast, text.** A unit test computes the WCAG contrast ratio of every foreground/background token pair in
   `NytkaLight`, `NytkaDark`, `NytkaLightExtras` and `NytkaDarkExtras`, and fails under 4.5:1:
   each `onX` over `X` (primary, secondary, tertiary, error, their containers, background, surface, inverse,
   `surfaceVariant`, capture and its container), and `onSurface` and `onSurfaceVariant` over every surface tier
   (`surface`, `surfaceContainer`, `surfaceContainerLow`, `surfaceContainerLowest`, `surfaceContainerHigh`,
   `surfaceContainerHighest`, `surfaceVariant`).
6. **Contrast, parts of controls.** The same test fails under 3:1 for what marks a control or state without text:
   `outline` over `surface` (field and chip borders), `primary`, `error`, `tertiary` and `capture` over `surface`.
   `outlineVariant` is a divider and is exempt. The wallpaper (dynamic) scheme is the system's; only the four
   reserved meanings (`keepingMeanings`) are checked, with the same rules.
7. **Colour is never the only signal.** The capture state, a decision badge, an error and a muted line each have
   words in the node's merged text or description, not only a colour or an icon. A per-state test lists the
   expected spoken text for each `CaptureStatus` and `ListStateKind`.
8. **Traversal.** For each covered screen and fixture state, the merged semantics tree read top to bottom,
   start to end (what TalkBack swipes through) equals an expected list of spoken labels kept in the test. A change
   to order shows up as a failing, reviewable diff. Section titles have `heading()`; the screen's top-level
   container is a `isTraversalGroup`; `traversalIndex` is used only to fix a visual order that differs from the
   reading order.
9. **No gesture-only actions.** A long-press, swipe or drag that does something has the same action as a
   `customActions` entry on the node or as a visible control. A test performs each through the semantics action,
   not the gesture.
10. **Announcements.** A status, notice or error that appears without the user acting (connection lost, a failed
    upload, a correction snackbar) is in a `liveRegion` or posted through a snackbar, so TalkBack says it. A test
    asserts the live region on each state change in the fixtures.
11. **Coverage cannot rot.** A test lists every `AppTab` route and `DEVICE_ROUTE` against the coverage table below
    and fails when a route is missing from both the covered and the not-yet-covered column, so a new screen is
    either covered or named as a debt.
12. No new permission, no new dependency in the shipped app (the test libraries are `testImplementation` and
    `debugImplementation` only), no Room schema change, and every new string is in `values/strings.xml` and
    `values-uk/strings.xml` (`StringsParityTest` already checks it).

## Coverage

| Screen | Composable | Fixture states | Covered by |
|---|---|---|---|
| Home | `TodayScreen` | empty, a few items, an item needing a decision, offline | step 2 |
| Timeline | `TimelineScreen` | empty, a day with scenes and fragments, error | step 2 |
| Conversations | `ConversationsScreen`, `ConversationCard` | empty, rows with chips (AI, Media, speech), tag filter on | step 2 |
| Device | `DeviceScreen` pendant block, `StatusCard` | not paired, connected, muted, reconnecting, refused | step 2 |
| Settings | the sections of `DeviceScreen` (capture, briefs, location, language, appearance, phone microphone) | each section expanded | step 2 |
| Chrome | top bar capture status, tab bar | each connection state, three tabs and five tabs | step 2 |
| Not yet covered | conversation page and player, search, tasks, ask, people and review, memories, topics, over time, voice, developer, first run | | later, named here so the debt is visible |

Steps: this spec ships first. The second pull request adds the test base and the checks above, then fixes what they
find on the covered screens; each fix comes with the test that failed. The not-yet-covered screens follow in later
pull requests, one or two at a time, each moving a row into the covered column.

## How the checks are built

- **Dependencies.** `androidx.compose.ui:ui-test-junit4` as `testImplementation` and `ui-test-manifest` as
  `debugImplementation`, both versioned by the Compose BOM already in use. Tests run under Robolectric
  (`@RunWith(AndroidJUnit4::class)`, `createComposeRule()`), so `./gradlew check` runs them; no emulator.
- **No Hilt in tests.** Every screen takes its view model as a parameter with a `hiltViewModel()` default. The
  tests build the view model with the fakes the view model tests use (`FakeToday`, `FakeSettings`,
  `FakePendant`, the client fakes) and pass it in. A screen that cannot be tested this way is a finding: split
  its stateless part out.
- **Shared helpers** in `app/src/test/.../ui/a11y/`: `assertNamed()` (check 1), `assertRoles()` (2),
  `assertTouchTargets()` (3), `withFontScale(2f) { }` (4), `spokenOrder()` returning the merged labels in order (8)
  and `Contrast.ratio(a, b)` (5, 6). Each helper has its own test, including a negative one that proves it fails
  on a node that breaks the rule.
- **Theme under test.** Every screen test runs in `NytkaTheme` with the light and the dark scheme; a screen that
  hard-codes a colour outside the tokens is a finding.
- **Files.** `docs/specs/accessibility.md` (this), `docs/reference/screens.md` gets an "Accessibility" note per
  covered screen when the pass lands, and the README gets an "Accessibility" section saying what the app supports
  (TalkBack, font scale to 200%, 48 dp targets, contrast) in the same pull request as the first visible fix.

## Known gaps before the pass

Read from the code at `6aa146a`, to be confirmed by the checks rather than trusted:

- No Compose UI test exists; nothing guards labels, roles, targets or font scale today.
- Close buttons on tag chips use `IconButton(Modifier.size(32.dp))` in `ui/tags/TagChips.kt` and
  `ui/tags/TagFilter.kt`, under the 48 dp target of check 3.
- The theme tokens already pass checks 5 and 6 (computed on 2026-11-03, lowest text pair `onSurfaceVariant` over
  `surfaceContainerHighest` at 6.35:1 light and 6.83:1 dark; lowest part-of-control pair `outline` over `surface`
  at 4.19:1 light), so the contrast test starts as a guard, not a fix.
- 18 icons are set to `contentDescription = null`; which of them are decorative is for the check to decide.

## Not machine-checkable (manual, not a gate)

A person with TalkBack on a phone walks Home, Timeline, Conversations and Device once per release: every control is
found by swiping, nothing is announced twice, nothing important is silent. The same with Switch Access, with
"Remove animations" on and with the display size at the largest. Findings become issues, and a finding that a
machine could have caught becomes a check here.

## Out of scope

Captions or transcripts of the app's own audio output (there is none; playback is the user's recording), a
high-contrast theme beyond the two Nytka schemes, right-to-left layouts, and the home-screen widget (Glance has its
own semantics and gets its own check in a later pull request).
