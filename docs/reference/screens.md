# The screens

Every tab of the app, search, appearance and tags. Back to the [README](../../README.md).

## The tabs

Every switch shows a check in its knob when it is on, so the state does not rest on color alone.
Addresses, tokens and signing secrets are set in a monospaced font.

- **Conversations.** A status card, a search icon and your conversations by day; the day's name
  stays at the top while its cards scroll. A card shows the title, two lines of summary, and the time
  range and length. A thin moving line and **Summarizing** show while the server writes a summary;
  **Summary failed** with a red border and a triangle shows when it could not. The status card says,
  with a triangle and what is safe, when the server is more than 15 minutes behind on transcripts.
  An empty list says what will appear and how. While the list loads it shows three
  grey placeholder cards. When your server cannot be reached the list says **Offline** and that your
  recordings stay on the phone; pull down to try again. Other failures say what failed and that your
  recordings are not affected, with **Retry**. Tasks, People, Memories and Ask use the same states. Open a conversation for its
  summary, its tasks and the transcript, with the speaker above each run of words when the
  transcription provider names speakers. Speakers differ by shape, not colour: you are a **You** tag,
  a named person is a name led by one of seven marks (disc, square, triangle, diamond, ring, plus,
  bar) given in order of first appearance, and a voice nobody has named is a saffron **Voice 2** tag
  with **Who is this?**, which opens the naming dialog. The paragraph being heard gets a darker ground
  and a pause glyph. Its ⋮ menu has **Rename** (an empty title restores
  the generated one), **Regenerate summary** (not while the conversation is open) and **Delete**.
  When the server kept the audio (14 days by default, and none for imported conversations), a player
  is docked at the bottom: a plain track with a tick wherever the speaker changes, the position,
  length and who is talking, **Follow** (the transcript scrolls to the paragraph being heard; on by
  default), −10 and +10 seconds, play or pause, and the speed (1×, 1.25×, 1.5×, 2×). Tap the time
  beside a paragraph to play from there. Pauses between speech are not stored, so they are skipped.
  If the server cannot be reached the player says so in one line and stays. Playback stops when you
  leave the screen or the app.
  Touch and hold a paragraph to say whose it is: **This is me**, **This is not me**, or **Clear**
  (only where you marked it). Your mark beats the server's guess and shows as "Me · marked"; a line
  the server's voice match or the provider calls yours shows "Me · voice" or "Me · provider". Needs
  server 0.12.0 and an admin token.
  **Speech kind.** On a server that lists `speech-kind`, a line the server took for a TV, a video or a
  call shows a chip: **Media** (muted text) or **Call**. While the server only watches, a guess shows as
  an outlined **Media?** or **Call?**. Touch and hold a line for **Person here**, **Media**, **Call** or
  **Clear kind**; it shows at once and comes back, with a note, if the server refuses. This works on
  your own lines too. The ⋮ menu has **Other voices are media** and **Other voices are people**, which
  mark every line that is not yours and read the conversation again. Needs an admin token; without the
  feature there is no chip and no menu entry.
  When the server has a name suggestion for a voice in the conversation ("A voice may be Olena"), a
  saffron card above the transcript shows the line that carries it with **Accept**, **Not Olena** and
  **Later**, which only hides the card on this screen (Undo in the snackbar brings it back).
  Accept names the voice, so every line of it shows the name; Not Olena keeps the name from being
  suggested again for that voice. Nothing is applied until you tap. Tap a person's name above a run
  to open their page in the People tab. Needs server 0.14.0 and an admin token.
  When the same name is suggested for several voices (the server sends `sameName`, 0.21.0 and later), a
  third button **Accept all 16** accepts every pending suggestion of that name at once, in the banner and
  in the inbox, and says "Added 16 voices to Olena", with "2 skipped" when some no longer applied. It
  sits beside Accept and never runs by itself. If the pending ones disagree, nothing changes and the app
  says to accept them one by one. A server without the route answers 404: the app says it needs an
  update and hides the button until the screen is opened again.
  On a server that lists `roles` the suggestion may be a role: someone known by what they do before you
  know their name. A role alone reads "A voice may be the repairman" with **Add as repairman** and **Not
  that**; a name and a role read "A voice may be Mykola, the repairman". The role is shown as the server
  sends it. Add as repairman creates a person called "Repairman" (or "Repairman 2") and tags them
  `repairman`; nothing is created until you tap. A server without `roles` shows the name only.
  Titles, summaries and tasks come from a language model on your server: without one, a row shows the
  start of the transcript instead.
- **Tasks.** What your conversations left you to do, newest first, with the conversation's title and
  day. Tick a task to complete it, tap it to open its conversation, and use ⋮ to edit or delete it
  (delete asks first, with a red **Delete**). Done tasks sit below, collapsed, in a dimmer color and never struck through. The model finds the tasks. On a server that lists `task-create`
  (0.20 or later) **+** adds one of your own, with an optional due date from a date picker; such a task has no
  conversation, so tapping it opens nothing. A task with a due date shows "Due …" under its line, in red when it is
  open and the day has passed; ⋮ → **Due date** sets, changes or clears it. The date is a plain calendar day, shown
  as sent. An older server shows no **+** and no due dates.
- **Memories.** Lasting facts about you that the server took from your conversations, grouped by how long
  ago it learned them (Today, This week, This month, Earlier). Each is set in the reading type with its
  source, a link that opens the conversation (a memory you add yourself has none). **+** adds one, the
  pencil edits one, and the trash icon forgets one: it leaves the list at once and an **Undo** snackbar
  stays for a few seconds; when it goes away the server is asked to delete it, and a memory forgotten
  does not come back from later conversations. If you close the app before the snackbar is gone, the
  memory stays.
- **People.** Everyone Nytka knows, most recently heard first and never-heard last, each with their
  first letter in a circle (a voice without a name gets a ? on a different colour), when
  they were last heard and how many facts the server holds about them. People a voice match asks about
  ("Is this Olena?") come first with **Needs you**. A server that does not send
  those two fields yet gets the list sorted by name with the number of lines each person said. Below
  it, **Unnamed voices**: tap one to name it. Where the server has no person page, tap a person for
  **Rename**, **Merge into another person** and **Delete** (with or without the stored voice model).
  Pull down to refresh. Needs server 0.7.0 or later; the list is not kept on the phone.
  Tap a person on a server with person pages (0.14.0 or later) to open their **person page**: a large
  monogram, when they were last heard, a line with a lock saying the voice print lives on your server
  (and from how many lines), a **Rename** button (**Add name** for a person known by a role), **your note** (up to 500 characters; only you write
  it, the model never reads or changes it; empty it and save to remove it), their **facts** (newest
  first, 50 at a time, **Show more** for older ones) each with why it is there ("They said", "Said about
  them", "Mentioned" or "Added by you") and the conversation it came from, **Open tasks** owed to them,
  **Promises** (on a server that lists `ledger`, 0.20 or later) and their ten newest **Conversations**; tap a fact, task or conversation to open the conversation.
  **Promises** has two lists: **You owe them** (your commitments to this person) and **They owe you**
  (what they said they would do for you), each with the open ones, newest first, and **Done (n)** to show the
  finished ones (up to 100 per list); a promise opens its conversation, if it has one.
  **Add a fact** adds one; a fact has the same pencil and trash as a memory, with **Undo** (a deleted
  fact does not come back from later conversations). The page's ⋮ has **Merge into…**. At the very
  bottom, **Forget this person** asks first and names what goes: the voice print (also in the
  transcription service), the facts and the name. Adding a fact that
  exists, or taking a name someone has, says so under the field. A server without the page says it
  needs an update. Facts and notes need an admin token to change.
  **Review inbox**: when the server lists the `review` feature and something waits, an inbox icon with
  a count sits in the top bar. It lists name suggestions ("Is this Olena?" with the line that carries
  the name), voice matches (with the similarity) and lines Nytka is unsure are yours ("Did you say
  this?"), each as a card with a saffron question on top (a decision, not a failure) and **Yes** and **No**. Nothing is applied until you tap; the row goes at once and
  comes back if the server refuses. Tap the text of a card to open its conversation. An empty inbox, a loading list, an offline server and a failure each say so in words. Needs an admin token.
  A role reads "Is this the repairman?" or "Is this Mykola, the repairman?".
  **Known by a role.** A person who has a role and no name yet shows their role as the name with a
  second line "Name not known yet", in the list and on their page, whose ⋮ then has **Add name** in
  place of Rename. It opens the usual rename dialog, empty; saving sets the name. A name someone else
  has is refused ("taken") as for any rename. Later the server may suggest the name it hears for them:
  accepting that, when the name already belongs to another person, merges the role-only person into
  them, and the app says "Merged with {name}". Needs `roles`; an older server never sends the flag.
  A server that lists `tag-suggestions` adds proposed tags to the inbox ("Tag this conversation
  work?", "Tag Anna repairman?"), each with **Yes** and **No**; a person's tag opens that person. A tag
  that the item has no room for (20 tags) stays in the list with "This item has 20 tags."
  A server that lists `speech-kind` adds stretches of speech it is unsure about ("Was this a TV or a
  video?", or "Was this a call?" when it guesses a call), with the stretch's lines. **Yes** keeps the
  guess as your mark (a TV or video when it was unsure), **No** marks the stretch as a person. A
  stretch you or someone else already marked leaves the list without a notice.
  **Who is this?** cards sit above the list when your server groups voices (it needs
  `people.voiceMatching` on and keeps audio for at least a day). A card has the same saffron header; it shows the conversation, up to
  three lines in quotes and a clip of at most 10 seconds, played from your server with your token and never
  stored on the phone. On a group, type a name or pick someone you know and tap **Save**, or tap
  **Skip** (hidden for 7 days) or **Not a person**. On a match ("Is this Olena?") tap **Yes**,
  **Not them** or **Skip**. Nothing is named until you tap. Needs an admin token.
  **⋮ → People settings** edits the server's `people` settings (`people.suggestNames`, `people.facts`,
  `people.voiceMatching`, `people.voiceThreshold`) without developer mode; it needs an admin token.
  Under `people.voiceMatching` it says that recording people without their consent is illegal in some
  places, and the server's refusal (such as "Enroll your voice first.") shows under the switch in the
  server's English. The same screen has **Delete all voice groups and voiceprints**, which asks first and
  then removes every voice group, every voiceprint of a named person and every pending voice match from
  your server. It does not reach the voice models the transcription service keeps: those go with
  **Delete and remove voice model** on one person.
- **Ask.** Type a question (up to 500 characters) and tap send for an answer drawn from your
  conversations and memories, with its sources numbered below it. Your question is shown above the answer.
  Tap a source row, or its numbered pill in the answer, to open the conversation it came from; a memory with none opens nothing. When no source supports an answer, the tab says "Nothing in your
  conversations answers this." and shows none. "The server
  has no model set up, or is restarting.", "The model could not answer. Try again." and "The server
  took too long to answer. Try a shorter question." cover the server's own failures. Needs server
  0.8.0 or later; an older server says it needs an update.

Screens fade into each other (300 ms in, 150 ms out) and nothing else moves.

The bar at the bottom holds five tabs, each with its label: Conversations, Tasks, Ask, People and
Memories (Памʼять in Ukrainian). People carries a saffron count of the name suggestions and other
review items waiting for your answer; the review inbox icon on the People screen uses the same badge.

**Capture status.** Every main screen shows what the pendant's audio is doing at the top, in four states
that differ in shape and words, not only in colour, so none can be mistaken for another:

| State | Glyph | Line under the top bar |
|---|---|---|
| **Recording** | Filled green disc | Solid, with a stitch travelling along it |
| **Muted** | Two bars | Dashed |
| **Waiting for pendant** | Broken ring, turning once in 4 s | Dotted |
| **Recording off** | Ring with a slash | None |

Green means only that audio is being captured now. With *Remove animations* on in Android's settings the
ring and the stitch stand still; the shapes and words stay. TalkBack reads the state when it changes. The
pendant's own light shows its link to the phone, not recording. Tap the capture status to open the
**Device** screen: the pendant, its storage and settings, the server, the settings,
[Phone context](device.md#phone-context), [Your voice](device.md#your-voice), **Appearance** and the version.

**Appearance.** Nytka uses its own linen-and-ink colours in light and dark, with Onest for the interface,
Literata for reading and JetBrains Mono for addresses and tokens, all bundled in the app.
**Match wallpaper colours** (off by default) lets buttons and surfaces follow your wallpaper; recording
green, the saffron of questions that need you and the red of failures keep their colours. The font
licences (SIL Open Font License 1.1) are under Device → About → **Font licences**.

**Search** opens from the magnifier above the conversation list. It looks through transcripts,
titles, summaries and memories, in Ukrainian and English, while you type: from one letter or digit,
after a short pause. **All**, **Conversations** and **Memories** (chips with a check when selected) narrow it; **All** also finds people by name
(a server with the People tab), and a person opens their page in the People tab. A hit shows the title or
the memory and a snippet with the matches in bold, and opens its conversation when it has one. Before you type, with no hits, offline and on a failure the list says so. Every
word must match, exactly or as the start of a word. Endings of Ukrainian words need the server's optional dictionary:
see the [server docs](https://github.com/nytka-app/server/blob/main/docs/explanation/features.md#ukrainian-search-optional).

**Tag chips.** Tapping a tag on a conversation opens the Conversations list with only the conversations
that have it, and tapping one on a person page does the same in People (the unnamed voices, which carry
no tags, drop out). The filter lasts until you leave the app; a server without the `tags` feature
keeps the full list.

## Tags

Short words you put on conversations and people (`work`, `family`, `repairman`); only you add
them. A server that lists the `tags` feature (0.17.0 or later) shows them; an older one shows no tags
and no tag controls. A row in Conversations and People shows up to three tags and "+N" for the rest.
A conversation and a person page show all of them under the header.

With an admin token, **+ Tag** opens a sheet: type a name, or pick one of the tags already in use
(most used first, narrowed as you type), then tap it or **Done**. The server cleans the name up (lower
case, `dog walker` becomes `dog-walker`) and its answer is what the chips show. A chip's **✕** removes
the tag at once and puts it back with a message if the server refuses. A name the server does not accept
says "Use letters, digits, - or _ (up to 32)"; a 21st tag says "This item has 20 tags." A read token
shows the chips without **+ Tag** or **✕**. Tag names are personal: they are never written to a log or
the debug report, and the phone keeps no copy.

**Filter.** On a server with the `tags` feature, Conversations and People have a **Tag** action (in the
row above the list on Conversations, in the top bar on People). It opens the tags in use that some
conversation (or person) carries, most used first, with how many; type to narrow them, tap one. A chip
"Tag: work" under it shows the filter and its **✕** clears it. The list pages as the full one does, and
an empty one says "Nothing tagged work". The filter survives rotation and lasts until you clear it or
leave the app; it is not saved on the phone. People leaves out the unnamed voices while it is on.

**Media in the list.** On a server that lists `speech-kind`, a conversation whose speech is 80 percent or
more media shows a **Media** chip on its row. **Hide media**, in the row above the list, leaves those
out: a chip "Media hidden" under it shows the filter and its **✕** brings them back. It works with the
tag filter, pages like the full list, and an empty result says "Nothing here but media". It survives
rotation and lasts until you clear it or leave the app; it is not saved on the phone. Without
`speech-kind` there is no chip and no action.

**Proposed tags.** A server that lists `tag-suggestions` may propose tags it read in a
summary. On a conversation, a "Suggested tags" row under the header shows each name on a saffron card with **Add**
and **Not this** (never proposed again for that conversation); the person page shows the same
row for tags proposed for that person once the server proposes them. Nothing is added until you tap: the
tag appears at once on **Add**, a refusal keeps the proposal and says why ("This item has 20 tags." when the
item is full), and one the server no longer holds (answered elsewhere) drops and the page reads again.
A failure of the list shows no row. A server without the feature shows none and the app does not ask it.
