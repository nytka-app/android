# Nytka for Android, accessibility

Nytka is worn and read by people who may not see the screen, may not hear it, may not tap small targets or may run
the phone at the largest text size. This spec says what "accessible" means for the app in checks a machine runs on
every pull request, so it stays true as screens change. The wider track it belongs to (more recorders, more people):
[`multi-device.md`](multi-device.md). The target is WCAG 2.2 level AA where it applies to a native app, plus
Android's own rules (48 dp touch targets, TalkBack, font scale).

Verified against app `main` at `058fafd` on 2026-11-05. Nothing here was run on a phone with TalkBack: every
check below runs on the JVM under Robolectric. A short manual pass with a real TalkBack is listed at the end and is
not a gate. The checks landed in the second pull request; the "Result of the first pass" section says what they found.

## Done when

1. **Names.** On each covered screen, every node that can be clicked, toggled, long-pressed or set has a name a
   screen reader can speak: text or a content description in its merged semantics. An icon that carries meaning
   has a description; a decorative icon has `contentDescription = null` and is not focusable on its own.
2. **Roles and state.** A switch, checkbox, radio button, tab, button, slider and text field exposes the matching
   `Role`, and a toggle exposes its `ToggleableState` (a selected tab or chip exposes `selected`). No node has a
   click action and no role unless it is a list row that opens something, which reads as a button.
3. **Touch targets.** Every node with a click, toggle or long-press action has a touch target
   (`SemanticsNode.touchBoundsInRoot`) of at least 48 dp by 48 dp, counting the space Compose adds around a control
   (the platform's minimum touch target). The visible shape may be smaller.
4. **Font scale 2.0.** With `fontScale = 2.0` (and again at 1.3), each covered screen, in a window tall enough to
   place every lazy item: clips no text whose `maxLines` is unset (`didOverflowHeight`, or a line wider than its
   box); never pushes an action past the screen's right edge; and meets checks 1 to 3 at that scale, so every action
   is still there, named and large enough. The capture status (top bar) says its state in words at every scale.
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
   words in the node's merged text or description, not only a colour or an icon. For the capture state, a test lists
   the spoken sentence of each `CaptureState` (`ChromeA11yTest`); the others are read through checks 1 and 8.
8. **Traversal.** The merged semantics tree read top to bottom, start to end (what TalkBack swipes through),
   equals an expected list of spoken labels kept in the test, with each node's role and heading mark. A change to
   order shows up as a failing, reviewable diff. Kept as golden lists for the Timeline day and the tag-filtered
   Conversations list; every other screen test prints its order when it fails. Section titles have `heading()`; the golden lists are kept for the tab bar, Tasks and People as well.
9. **No gesture-only actions.** A long-press, swipe or drag that does something has the same action as a
   semantics action or a visible control. `combinedClickable` publishes its long-press as `OnLongClick`, which
   TalkBack offers in its actions menu, and check 3 counts such a node as actionable. The app has no `pointerInput`
   gesture and no swipe; a new one needs a `customActions` entry and a test in the same pull request.
10. **Announcements.** A status, notice or error that appears without the user acting (connection lost, a failed
    upload, a correction snackbar) is in a `liveRegion` or posted through a snackbar, so TalkBack says it. The
    capture status, the one such element on a screen, has a test that finds its polite live region for every state
    (`ChromeA11yTest`).
11. **Coverage cannot rot.** `CoverageA11yTest` lists every `AppTab` route and `DEVICE_ROUTE` against the coverage
    table below and fails when a route is missing from both the covered and the debt list, so a new screen is
    either covered or named as a debt.
12. No new permission, no new dependency in the shipped app (the test libraries are `testImplementation` and
    `debugImplementation` only), no Room schema change, and every new string is in `values/strings.xml` and
    `values-uk/strings.xml` (`StringsParityTest` already checks it).

## Coverage

| Screen | Composable | Fixture states | Covered by |
|---|---|---|---|
| Home | `TodayScreen` | empty, a few items; light and dark; 1.0, 1.3, 2.0 | `HomeA11yTest` |
| Timeline | `TimelineScreen` | empty, a day with scenes, fragments and an event; same variants; golden order | `TimelineA11yTest` |
| Conversations | `ConversationsScreen` | empty, rows with chips (AI, media, failed), tag filter on; same variants; golden order | `ConversationsA11yTest` |
| Device and settings | `DeviceScreen`, which holds every settings section | paired, not paired; same variants | `DeviceA11yTest` |
| Capture status | `CaptureStatusIndicator`, the top-bar pill and the strip | every `CaptureState`; 1.0 and 2.0, light and dark | `ChromeA11yTest` |
| Tab bar | the bar inline in `LifeNavHost` (3 tabs) and `NytkaNavHost` (5 tabs), each run with a line of text for the screen | 3 and 5 tabs; 1.0, 1.3, 2.0, light and dark; People badge on; golden order | `TabBarA11yTest` |
| Tasks | `TasksTab` | open tasks (overdue, no conversation, with a due date), done list open, empty, failed; 1.0, 1.3, 2.0, light and dark; golden order | `TasksA11yTest` |
| People | `PeopleScreen` with its cards and review inbox | people (one named by role only), unnamed voices, cards, review badge, empty, failed; same variants; golden order; the monogram on its own | `PeopleA11yTest` |
| Not yet covered | Over time, Ask, Memories, the conversation page and player, search, review, topics, voice, developer, first run, the home-screen widget | | later, named here so the debt is visible |

Steps: this spec ships first. The second pull request adds the test base and the checks above and fixes what they
find on the covered screens. The not-yet-covered screens follow in later pull requests, one or two at a time, each
moving a row into the covered column and its route from the debt list to the covered map in `CoverageA11yTest`.

## How the checks are built

- **Dependencies.** `androidx.compose.ui:ui-test-junit4` as `testImplementation` and `ui-test-manifest` as
  `debugImplementation`, both versioned by the Compose BOM already in use. Tests run under Robolectric
  (`@RunWith(AndroidJUnit4::class)`, `createComposeRule()`), so `./gradlew check` runs them; no emulator.
- **No Hilt in tests.** Every screen takes its view model as a parameter with a `hiltViewModel()` default. The
  tests build the view model with the fakes the view model tests use (`FakeToday`, `FakeSettings`,
  `FakePendant`, the client fakes) and pass it in. A screen that cannot be tested this way is a finding: split
  its stateless part out.
- **Shared helpers** in `app/src/test/.../ui/a11y/`: `A11yChecks.kt` has `unnamed()` (check 1), `badRoles()` (2),
  `smallTargets()` (3), `clipped()` (4), `spokenOrder()` (8), `liveRegions()` (10), `setScreen(fontScale, dark)` and
  `assertClean()`, which fails with every finding and the spoken order; `Contrast.kt` has the WCAG ratio (5, 6).
  Each has its own test, including a negative one that proves it fails on a node that breaks the rule
  (`A11yChecksTest`, `ContrastTest`).
- **Real text, tall window.** Tests use `@GraphicsMode(NATIVE)` so text is measured, and a window tall enough for the
  lazy lists (`h3000dp`; `h16000dp` for the Device screen at 2.0), or a lazy item is composed but not placed.
- **Theme under test.** Every screen test runs in `NytkaTheme` with the light and the dark scheme; a screen that
  hard-codes a colour outside the tokens is a finding.
- **Files.** `docs/specs/accessibility.md` (this), `docs/reference/screens.md` gets an "Accessibility" note per
  covered screen when the pass lands, and the README gets an "Accessibility" section saying what the app supports
  (TalkBack, font scale to 200%, 48 dp targets, contrast) in the same pull request as the first visible fix.

## Result of the first pass

The gaps this spec expected from reading the code did not show up when the checks ran, on 2026-11-03:

- The 32 dp close buttons on tag chips are `IconButton`s; Material gives them a 48 dp touch target, so check 3 passes
  (the Conversations tag-filter state is under test). The Compose layer widens any pointer-input node to the
  platform minimum, which is why `A11yChecksTest` proves check 3 with that minimum switched off.
- The capture pill is 40 dp tall and tappable; `Surface(onClick)` widens it to 48 dp. Passes.
- Home, Timeline, Conversations and Device (paired and not) pass checks 1 to 4 at 1.0, 1.3 and 2.0, light and dark.
- The theme tokens pass checks 5 and 6 (lowest text pair `onSurfaceVariant` over `surfaceContainerHighest` at 6.35:1
  light and 6.83:1 dark; lowest part-of-control pair `outline` over `surface` at 4.19:1 light).
- The pass found no defect in the covered screens, so it changes no behaviour: it adds the tests that keep it so.
  What the checks cannot see is for the manual TalkBack pass below, and the 18 `contentDescription = null` icons were
  not reviewed one by one: a decorative icon next to text is correct, and one that acts alone would fail check 1.

## Result of the second pass

The tab bar, Tasks and People were added on 2026-11-05. The bar itself passes checks 1 to 4 with three and five tabs.
The checks found four defects in the shipped screens, each fixed with the smallest change:

- **Tasks:** the checkbox of each task had no name of its own. It now
  carries the task's text as its description.
- **People tab badge:** the bar item clears its icon's semantics when it shows a label, so the badge's "N need you"
  was never spoken. The item now has it as its `stateDescription`.
- **People section titles:** "People" and "Unnamed voices" were not marked as headings. They are now (check 8).
- **Monogram:** the letter in the round avatar was read out as well as the name next to it, and at font scale 2.0
  it outgrew its disc. The avatar is now silent and its letter ignores font scale, as the disc has a fixed size.

Two things are known and not fixed here:

- An overdue task is told apart from the others only by the error colour of its due line, which reads "Due Sunday,
  27 September" either way. Check 7 asks for words, so this is a gap; the fix needs a new string and its Ukrainian
  translation and is left for its own change.
- On People the top-bar actions (Tag, Review inbox, More) come after the list in the tree. The JVM cannot say whether
  TalkBack reads them in that order on a phone; the manual pass should.

`PeopleScreen` got a `reviewViewModel` parameter (default `hiltViewModel()`) so the test can pass a fake: a seam for
testing, not a behaviour change.

## Not machine-checkable (manual, not a gate)

A person with TalkBack on a phone walks Home, Timeline, Conversations, Tasks, People and Device once per release: every control is
found by swiping, nothing is announced twice, nothing important is silent. The same with Switch Access, with
"Remove animations" on and with the display size at the largest. Findings become issues, and a finding that a
machine could have caught becomes a check here.

## Out of scope

Captions or transcripts of the app's own audio output (there is none; playback is the user's recording), a
high-contrast theme beyond the two Nytka schemes, right-to-left layouts, and the home-screen widget (Glance has its
own semantics and gets its own check in a later pull request).
