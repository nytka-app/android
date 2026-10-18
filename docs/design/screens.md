# Screens

The redesign of the main screens. Each section says what changes from today (as the README
describes it), the layout, and the empty, loading and error states. Every screen uses the tokens in
[tokens.md](tokens.md) and so works in light and dark with no per-screen color; where dark needs
something different, the section says so. Copy in quotes is the app's existing string where one
exists, otherwise a proposal.

Rules that hold on every screen:

- **Loading** is a skeleton of the real layout (rows of `surfaceContainer` blocks), never a centered
  spinner, except for actions under 2 s, which show a progress indicator in the button.
- **Empty** names what will appear and how it gets there, with at most one action.
- **Errors** use the fixed sentences in `ui/ServerNotices.kt` ("This server needs an update", "This
  item no longer exists", "The app needs an admin token.") plus one action: **Retry**, **Open
  Device** or **Enter token**. A failed list keeps whatever it last showed and puts the error in a
  banner above it, so a flaky network never blanks the screen.
- **Offline** (server unreachable) is not an error on reading screens: a one-line banner
  "Unreachable since 14:02. Audio waits on the phone." and the last data.
- Pull to refresh on every list.

## Navigation

Today: six tabs (Conversations, Tasks, Memories, People, Ask, Device) with `Icons.Filled` stand-ins
(Ask is `Face`, Memories is `Favorite`, Device is `Settings`; `ui/AppTab.kt`).

Proposed: five tabs, Material Symbols Rounded, outlined when not selected and filled when selected.

| Tab | Icon | Holds |
|---|---|---|
| Conversations | `forum` | capture card, next meeting, conversations by day |
| Tasks | `task_alt` | unchanged |
| People | `group` | review inbox, "Who is this?", people, unnamed voices |
| Ask | `manage_search` | two tabs inside: **Ask** and **Memories** |
| Device | `settings_remote` | pendant, server, your voice, settings |

Open question 2 in the [README](README.md#open-questions-for-yehor) decides this. The icons need
vector drawables copied into `res/drawable/` (the extended icons artifact is about 10 MB before R8;
UNKNOWN whether R8 strips it well enough here).

## Set up Nytka

Today: four steps in one screen (`ui/firstrun/FirstRunScreen.kt`): Server, Permissions, Consent,
Pairing.

Layout: a step indicator ("Step 1 of 4", plus four short bars in primary), the step title in
headlineSmall, one paragraph of why, the fields, and one filled button at the bottom. The wordmark
sits above step 1 only.

| Step | Content | States |
|---|---|---|
| 1 Server | **Scan QR code** as the first, filled button; below it "or enter it by hand": address, token (masked, with a show toggle), the **Private network (allow plain HTTP)** switch. **Test connection** | Testing: the button shows progress and the fields lock. Error: the sentence under the field it concerns ("The server refused the token." under the token; "Unable to resolve host" under the address) and the `/healthz` hint from the README's table. Success: a check and "Connected to your server, version 0.25.0", then the step advances by itself after 1 s |
| 2 Permissions | Two rows, *Nearby devices* and *Notifications*, each with why, and "Nytka never uses the phone's microphone or location." **Allow** | Refused twice: the row turns to "Without nearby devices Nytka cannot reach the pendant." with **Open app info** |
| 3 Consent | The note, as now, in bodyLarge; **I understand** checkbox; **Continue** disabled until ticked | none |
| 4 Pairing | The thread-wave dashed and moving slowly (searching), "Turn the pendant on and hold it near the phone." **Pair pendant**, **Set up later** as a text button | Nothing found after 30 s: "No pendant found. The official Omi app or another phone may still hold it." Found: the pendant's name and battery, **Done** |

The plain-HTTP switch, when on, shows an `error`-colored line under it: "Audio travels unencrypted.
Use this only on a network you trust." It is the one warning in first run.

## Conversations

### Capture card

Today: a `StatusChip` at the top and an `ElevatedCard` with lines for battery, server, queue and a
**Mute** button (`ui/StatusCard.kt`, `ui/StatusChip.kt`).

Proposed: one card (large shape, `surfaceContainer`) that leads with the state, drawn by the mark:

| State | Wave | Word | Second line |
|---|---|---|---|
| Recording | moving, `recording` color | **Recording** | "Pendant 67% · uploaded 14:31" |
| Muted | flat, onSurfaceVariant | **Muted** | "Double-tap the pendant to record again" |
| Waiting | dashed, outline | **Waiting for the pendant** | "Out of range, off, or held by another app" |
| Off | none | **Not recording** | **Start** |
| Paused by the server | moving, `recording` | **Recording** | `error` line: "Paused: The server refused the token." with **Enter token** |

One button, **Mute** or **Unmute**, tonal, on the right. Queue length shows only when it is above
0: "12 chunks waiting". Over 80% it turns to an `error` line with the percentage. The card replaces
the chip; the top bar keeps only the title, search and the bookmark count.

### Next meeting

When `BriefsClient.upcoming(120)` returns a brief, a `tertiaryContainer` card under the capture
card: "In 25 min · Design sync with Olena", the first line of the brief, and **Open brief**
(a sheet with the full brief). Nothing when the server has no calendar feed or no brief; no error is
ever shown here, since the notification path already handles failures. Open question 4.

### List

Grouped by day with sticky headers ("Today", "Yesterday", "Mon 14 Oct"). A row: title in
titleMedium, two lines of summary, then labelMedium "14:02–14:37 · 35 min" and chips
(**Summarizing**, **Summary failed** in `error`, tags). Bookmarked conversations show a star
before the time.

| State | Shows |
|---|---|
| Loading | capture card (it is local and always ready), then three skeleton rows |
| Empty, recording | "Nothing yet. Speech shows up here a few minutes after it is said." |
| Empty, not paired | "Pair the pendant to start." **Pair pendant** |
| No language model | rows show the start of the transcript, and a one-time banner: "Your server has no language model, so there are no titles or summaries." **How to set one up** (the server README) |
| Error | banner over the last list, **Retry** |

### Conversation

Summary first (bodyLarge), tasks as checkboxes, then the play bar (sticky at the top of the
transcript once scrolled to), then the transcript. A speaker label is labelMedium in the speaker's
color: six speaker colors derived from primary, tertiary and secondary at tone 40 (light) and 80
(dark), so labels stay legible in both. Media lines are onSurfaceVariant with a **Media** chip; the
name-suggestion banner is `tertiaryContainer`.

## Ask and Memories

### Ask

A text field at the bottom (500-character counter from 450), the answer above it in bodyLarge with
`[n]` as small primary-colored pills, sources as a numbered list below. Empty: three example
questions as suggestion chips taken from the owner's own recent conversation titles (no network
call beyond the conversations list), e.g. "What did Olena say about the invoice?". Loading: the
question stays, the answer area shows three skeleton lines. Errors: the three fixed sentences from
the README, each with **Try again**.

### Memories

The list as today, each memory in bodyLarge with its source conversation's title and day as a
labelMedium link. **+** is a FAB. Empty: "Lasting facts about you appear here as the server finds
them in your conversations. You can add one yourself." **Add a memory**. Delete asks first and says
"It will not come back from later conversations."

## People and your voice

### People

Top to bottom: the review inbox row ("4 to review" with a `tertiary` count badge; hidden at 0),
the "Who is this?" cards in a horizontal pager, the people list, **Unnamed voices**.

"Who is this?" card: `tertiaryContainer`, the conversation title and day, up to three lines in
quotes, a play button with the clip's length ("0:08"), a name field with people suggestions, then
**Save**, **Skip** and **Not a person**. A match card reads "Is this Olena?" with **Yes**, **Not
them**, **Skip**. Under the buttons, small: "Nothing is named until you tap."

| State | Shows |
|---|---|
| Loading | two skeleton rows; no card skeleton (cards pop in when ready) |
| Empty | "People appear here once your server hears them. Name a voice to start." with **Unnamed voices** when there are some |
| Voice matching off | no cards; a quiet row "Voice groups are off. People settings" |
| Read token | rows without edit actions, and a banner "The app needs an admin token." |
| Older server | "This server needs an update" |

A person row: avatar circle with the initial on `secondaryContainer` (no photos; the app has
none), the name, "Last heard 2 h ago · 14 facts". Role-only people show the role and "Name not
known yet" in onSurfaceVariant.

### Your voice (enrollment)

Today: a card on Device and an enrollment screen (`ui/voice/VoiceScreen.kt`).

Layout: language chips (Ukrainian, Russian, English), then the sentence to read in headlineSmall,
one at a time, with "2 of 3" and a **Next sentence** text button. Below: the live level as the
thread-wave (the mark again, driven by the pendant's level), and the counter in displaySmall:
"34 s" with "at least 30 s · stops at 120 s". A progress bar fills to 30 s in tertiary, then to 120 s
in primary. **Send** turns filled at 30 s; **Cancel** is a text button.

| State | Shows |
|---|---|
| Not connected | "Connect the pendant to read your voice." and the wave dashed |
| Reading | as above; a line under the counter: "This reading is not saved as a conversation." |
| Sending | the button shows progress, "Sending 41 s of speech" |
| Refused | the server's reason as a fixed sentence: "Less than 20 seconds of speech. Read the sentences again, a little slower." / "Another voice or noise got in. Find a quiet place and start over." **Start over** |
| Enrolled | the card on Device: "Enrolled 3 Oct from 9 samples · learned from 212 segments since", **Add more**, **Re-enroll**, and in the overflow **Forget what it learned** and **Forget my voice** (asks first) |

## Device

Sections as cards, in this order: **Pendant** (name, firmware, battery in displaySmall, storage
sync, LED and mic gain sliders), **Your server** (address, version, "Last upload 14:31", **Change**),
**Your voice**, **Recording** (mute schedule, consent chime, phone context), **Meeting briefs**,
**Language of summaries**, **About** (version; seven taps for Developer mode).

Each card's error lives in the card: "Pendant not supported" in the Pendant card, "Unreachable
since 14:02" in the server card. A locked server setting shows a lock icon and "Set by the server's
environment" in onSurfaceVariant. The firmware notice is a `tertiaryContainer` row in the Pendant
card: "Firmware 3.0.22 is out. Update it with the official Omi app."

### Server settings, tokens and webhooks

These stay in Developer mode and keep their layouts; they take the tokens, the mono face for
secrets and URLs, and the shared error rules. A secret shown once sits in a `surfaceContainerHigh`
box with **Copy** and "Nytka cannot show it again."

## Widget and notification

The widget's white `widget_background.xml` becomes `surfaceContainer` from the Glance theme, with
the mark in the same three states as the capture card. The ongoing notification's small icon is the
thread-wave; its title is the state word ("Recording", "Muted", "Waiting for the pendant").

## Dark theme notes

- Surfaces are ink (`#121318`), not pure black; the capture card sits one container step up so it
  reads as the screen's focus without a shadow.
- The recording red brightens to `#FF8A80`, which keeps 8.1:1 on ink and stays distinct from the
  pinker error `#FFB4AB` by shape, as in light.
- Speaker colors use tone 80 so a run of names never drops below 4.5:1.
