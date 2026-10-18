# Tokens

Material 3 roles, so every screen that reads `MaterialTheme.colorScheme` today picks them up without
a change. The screens use 12 roles in all, `error` most (38 uses), then `primary` (10),
`onSurfaceVariant` (6) and `secondaryContainer` (5); one literal color exists, `RecordingRed`
(`#D32F2F`) in `ui/StatusChip.kt`.

## Color

Seeded from the launcher: indigo `#2B2F6B` and cream `#F1E9DA`. Ratios are WCAG contrast against
the surface named, measured 2026-10-19; AA needs 4.5 for text and 3 for icons and outlines.

### Light

| Role | Value | Use | Contrast |
|---|---|---|---|
| primary | `#3B3F8F` | buttons, links, selected tab | 8.6 on surface |
| onPrimary | `#FFFFFF` | text on primary | 9.2 |
| primaryContainer | `#E1E0FF` | tonal buttons, selected chips | |
| onPrimaryContainer | `#14175C` | | 12.5 |
| secondary | `#5C5D72` | secondary icons | |
| secondaryContainer | `#E2E0F9` | status chip, filter chips | |
| onSecondaryContainer | `#191A2C` | | |
| tertiary | `#9A4521` | madder: suggestions, review count, briefs | 6.1 on surface |
| tertiaryContainer | `#FFDBCD` | the "Who is this?" card, suggestion banner | |
| onTertiaryContainer | `#3A0F00` | | 13.0 |
| error | `#BA1A1A` | failures only | 6.1 |
| errorContainer | `#FFDAD6` | | |
| background, surface | `#FBF8F3` | paper | |
| surfaceContainerLow | `#F5F2EC` | list rows | |
| surfaceContainer | `#EFECE6` | cards | |
| surfaceContainerHigh | `#E9E6E1` | sheets, dialogs | |
| onSurface | `#1B1B21` | text | 16.2 |
| onSurfaceVariant | `#46464F` | secondary text, times | 8.8 |
| outline | `#777680` | dividers that matter, idle dot | 4.2 |
| outlineVariant | `#C8C5D0` | hairlines | |

### Dark

| Role | Value | Contrast |
|---|---|---|
| primary | `#BFC2FF` | 10.9 on surface |
| onPrimary | `#23286B` | 7.8 |
| primaryContainer | `#3A3E87` | |
| onPrimaryContainer | `#E1E0FF` | 7.4 |
| secondary | `#C5C4DD` | |
| secondaryContainer | `#444559` | |
| onSecondaryContainer | `#E2E0F9` | |
| tertiary | `#FFB598` | 10.9 |
| tertiaryContainer | `#7B2E0C` | |
| onTertiaryContainer | `#FFDBCD` | |
| error | `#FFB4AB` | 10.9 |
| errorContainer | `#93000A` | |
| background, surface | `#121318` | ink |
| surfaceContainerLow | `#1A1B21` | |
| surfaceContainer | `#1F1F25` | |
| surfaceContainerHigh | `#292A30` | |
| onSurface | `#E4E1E9` | 14.4 |
| onSurfaceVariant | `#C7C5D0` | 10.9 |
| outline | `#918F9A` | |
| outlineVariant | `#46464F` | |

### Outside the Material roles

| Token | Light | Dark | Use |
|---|---|---|---|
| recording | `#C4282B` (5.4) | `#FF8A80` (8.1) | the recording dot and the live wave; never for errors |
| muted | onSurfaceVariant | onSurfaceVariant | the flat wave and "Muted" |
| thread | `#F1E9DA` | `#F1E9DA` | the mark on indigo; launcher and widget only |
| ink | `#2B2F6B` | `#2B2F6B` | the launcher background, the widget header |

Recording and error are both red, so they differ in **shape**: recording is always a filled dot or a moving wave, error is always text with
an icon (`error` outline). The design never relies on the two reds alone.

## Type

| Role | Face, weight | Size / line | Use |
|---|---|---|---|
| displaySmall | Onest 600 | 36 / 44 | big numbers: seconds read, battery on the Device card |
| headlineSmall | Onest 600 | 24 / 32 | screen titles in large top bars, first-run step titles |
| titleLarge | Onest 600 | 22 / 28 | top bar titles |
| titleMedium | Roboto 500 | 16 / 24 | card titles, conversation titles in the list |
| bodyLarge | Roboto 400 | 16 / 24 | transcript, summaries, memories |
| bodyMedium | Roboto 400 | 14 / 20 | list secondary text |
| labelLarge | Roboto 500 | 14 / 20 | buttons |
| labelMedium | Roboto 500 | 12 / 16, +0.5 tracking | chips, times, "Me · voice" |
| mono | system monospace | 13 / 18 | tokens, secrets, server URLs in Developer mode |

Every other M3 role keeps its default. Times and counts use tabular figures
(`fontFeatureSettings = "tnum"`). Onest ships as one variable TTF in `res/font/` (UNKNOWN size; check
before adding, budget 150 KB). No downloadable fonts: the `oss` flavor has no Play services.

## Shape

| Token | Radius | Use |
|---|---|---|
| extraSmall | 4 dp | chips' inner elements |
| small | 8 dp | chips, text fields |
| medium | 16 dp | cards (matches the widget's 16 dp in `widget_background.xml`) |
| large | 24 dp | bottom sheets, the capture card |
| full | | the status dot, avatars, FABs |

## Spacing

A 4 dp grid. Screen gutter 16 dp; card padding 16 dp; between cards 12 dp; between a label and its
value 4 dp; list rows 72 dp tall with two lines, 56 dp with one. The existing screens already use 16
and 4 (`StatusCard.kt`), so this names what is there.

## Motion

Only the capture wave moves on its own: one cycle per 1.2 s while recording, still when
`Settings > Accessibility > Remove animations` is on (`animator duration scale` 0). Everything else
uses the M3 defaults.

## How it maps to the code

`ui/theme/Color.kt` holds `NytkaLightColors` and `NytkaDarkColors` with the values above.
`NytkaTheme` gains `dynamicColor: Boolean = true`: true keeps today's wallpaper colors, false uses
the brand scheme. `NytkaPreviewTheme` passes false, so previews show the brand scheme and stay the
same on every machine. No screen changes, and the app looks the same until question 1 in the
[README](README.md#open-questions-for-yehor) is decided.

Still to wire, each in its own PR:

1. `recording` as a `NytkaExtraColors` composition local, replacing `RecordingRed` in
   `StatusChip.kt` and the widget.
2. Onest in `res/font/` and a `Typography` in `ui/theme/Type.kt`.
3. `Shapes` in `ui/theme/Shape.kt`.
4. The **Wallpaper colors** setting, if chosen (a README change in the same PR).
