# Nytka design

The design direction for Nytka: identity, theme tokens and the main screens of the Android app.
Written 2026-10-19. Nothing here is built yet except the color tokens in `ui/theme/Color.kt`, which
the app does not use by default (see [Tokens](tokens.md#how-it-maps-to-the-code)).

| File | Holds |
|---|---|
| this README | identity: the name, the mark, the tone, and the open questions |
| [tokens.md](tokens.md) | color, type, shape and spacing tokens, and how they map to Compose |
| [screens.md](screens.md) | the screens: layout, empty, loading and error states, dark and light |
| [mockup.html](mockup.html) | the clickable mockup's source; published at the link below |

**Mockup:** <https://claude.ai/artifact/1PaPQTgpkNBAFcwMnWyYRe> (private Artifact; open it signed in
as the owner). It shows Set up Nytka, Conversations with the capture card, a conversation, People with a
"Who is this?" card, Your voice, Ask and Memories, and Device, each in light and dark and with its
empty, loading and error states.

Sources read: `research/notes/naming.md`, `research/notes/competitors-and-growth.md`, this repo's
README and CLAUDE.md, `ui/theme/Theme.kt`, the launcher icon (`res/drawable/ic_launcher_foreground.xml`,
`res/values/ic_launcher_background.xml`) and the screens under `ui/`.

## What the design has to do

Nytka records everything said near its owner and keeps it on a server the owner runs. The research
says Nytka wins on what Omi will not do: a self-host that works, the owner's own models, no quotas.
So the design has two jobs, in this order:

1. **Make the recording state impossible to misread.** Recording, muted, waiting and away must
   differ in shape and word as well as color, on every screen that shows them, the widget and the
   notification included.
2. **Look like a tool the owner controls, not a cloud service.** Quiet surfaces, plain words, the
   server named as *your server*, no sparkles, no "AI" badges, no streaks or gamification.

## Name

**Nytka** (нитка, "thread"), chosen 2026-09-29 (`naming.md`). The thread is the idea: a day of speech
is one thread the app keeps whole, and the server pulls conversations, people and tasks out of it.

- Written **Nytka** in Latin script, **Нитка** in Ukrainian UI. Never all caps in running text.
- The name must not contain "Omi"; "for Omi" is fine in a description (`naming.md`). With
  Limitless and other pendants planned (`competitors-and-growth.md`), new copy should say
  "pendant", not "Omi", except where the device really is an Omi.

## Mark

The launcher icon already holds the right idea: a cream line on indigo that runs flat, swings into
a waveform and runs flat again (`ic_launcher_foreground.xml`, `#F1E9DA` on `#2B2F6B`). Keep it and
make it the mark:

- **The thread-wave.** One continuous stroke, round caps, flat at both ends, five swings of
  uneven height in the middle. Flat ends read as "thread", the swings as "voice". It works at 16 dp
  as a line and needs no lettering.
- **Monochrome icon** for Android 13 themed icons: already there, the same foreground as
  `<monochrome>` in `mipmap-anydpi/ic_launcher.xml`.
- **The live state of the mark.** In the capture card the same stroke animates its swings while
  recording, lies flat while muted and is dashed while waiting for the pendant (see
  [screens.md](screens.md#capture-card)). This ties the logo to the one state that matters.
- **Wordmark:** "nytka" in lower case in the display face (Onest, 600), letter-spacing −1%, the
  thread-wave to its left at cap height. Used on the first-run screen, the F-Droid listing and the
  READMEs; never inside the app's top bars.

Rejected: a necklace or pendant drawing (ties the brand to one device, and Limitless is next), a
microphone (Nytka never uses the phone's microphone), a sparkle or brain (the "AI" look the
research says not to compete on), a vyshyvanka cross-stitch pattern (pretty, but busy at icon size
and reads as souvenir; kept as an idea for the F-Droid feature graphic only).

## Color, in one paragraph

Ink indigo from the launcher is the primary, a warm paper neutral is the light surface, near-black
with an indigo bias is the dark one, and a madder (thread-red-brown) tertiary marks things the
owner should act on: name suggestions, review, briefs. Recording has its own red, separate from
error, so "recording" never looks like "failed". Exact values and contrast ratios are in
[tokens.md](tokens.md#color).

## Type, in one paragraph

Body text stays Roboto, the system face: no download, perfect Cyrillic, and the app is mostly
reading. Headlines and the few large numbers (battery, queue, seconds read) use **Onest**, an OFL
face with full Ukrainian Cyrillic, bundled under `res/font/`. Fixel (MacPaw, Ukrainian) was the
first choice; its license is UNKNOWN (see question 3).

## Tone

The README already has the voice; the app should match it.

- Say what happens and what to do: "The server refused the token. Paste it again, whole." Not
  "Oops! Something went wrong."
- Name the owner's things as theirs: *your server*, *your voice*, *your conversations*.
- No exclamation marks, no emoji, no "AI", "magic", "smart" or "assistant". The model is "the
  language model on your server" where it matters, and invisible elsewhere.
- Nothing is applied behind the owner's back, and the copy says so: "Nothing is named until you
  tap."
- Privacy statements are facts, not reassurance: "Only times are sent, never what played."
- Ukrainian strings are written, not translated word for word; the same rules apply.

## Open questions for Yehor

1. **Dynamic color.** Today the app uses only the wallpaper's colors (`Theme.kt`). The brand
   scheme makes recording and the madder accent consistent; wallpaper colors make Nytka feel native.
   Recommendation: brand scheme by default, a **Wallpaper colors** switch under Device → Settings.
   This PR only adds the scheme; the default stays wallpaper until you decide.
2. **Six tabs.** Material 3 navigation bars hold three to five destinations, and Nytka has six.
   Recommendation: fold **Memories** into **Ask** (a second tab inside it: "Ask" and "Memories"),
   since both are "what the server knows". Alternative: move Tasks into Conversations. See
   [screens.md](screens.md#navigation).
3. **Display face license.** Fixel's license is UNKNOWN: a third-party listing calls it OFL, and a
   FreeBSD ports review says it excludes residents of Russia and Belarus, which would make it
   non-free for F-Droid. Read the LICENSE in `github.com/MacPaw/Fixel` before choosing it over Onest.
4. **Meeting brief card in the app.** Briefs are notifications only today. The spec adds a "Next
   meeting" card on Conversations from `BriefsClient.upcoming`. Does the card belong there, or only
   in the notification?
5. **The phone's microphone.** The research names it the biggest reach lever and it breaks a stated
   principle. The design does not assume it; if it ever ships, the capture card needs a fifth state
   ("Phone microphone") with its own shape.
6. **Device-neutral copy.** Switch "pendant" copy to device-neutral now, or when the Limitless
   connector lands?
