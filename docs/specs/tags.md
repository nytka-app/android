# Nytka for Android, tags and roles: the app side

The server spec is nytka-app/server [`docs/specs/tags.md`](https://github.com/nytka-app/server/blob/main/docs/specs/tags.md):
free-form tags on conversations and people, roles for people known by what they do ("the repairman"),
and tags the model proposes. Read its interpretation of the owner's request first. This spec says what
the app shows and calls. How it is built: [`docs/plans/2026-10-05-tags.md`](../plans/2026-10-05-tags.md).

## Done when

1. Conversation rows, the conversation screen, people rows and the person page show their tags as chips.
2. On the conversation screen and the person page you can add a tag (with the tags in use as
   suggestions) and remove one.
3. The Conversations and People lists have a tag filter; a filtered list pages as the full one does.
4. Proposed tags show in the conversation's banner and in the review inbox, each with Accept and Reject.
5. A name suggestion with a role reads "{label} may be the repairman" or "{label} may be Mykola, the
   repairman"; a person known only by role shows "Name not known yet" on their page.
6. Every new string is in `values/strings.xml` and `values-uk/strings.xml`.
7. Against a server without the `tags`, `tag-suggestions` or `roles` feature, the app shows no chips,
   no filter, no proposals and no role wording, and nothing crashes.

## What exists today

Verified against app `main` at `2dc864e` and server `main` at `4ef9667`:

- `ConversationsClient` and `PeopleClient` have hand-written fakes (`FakeConversations`,
  `PeopleViewModelTest`'s fake); adding a method or parameter to either breaks them. New calls go in a
  new `TagsClient`, as the People plan did with `PersonPageClient`.
- JSON is decoded with `ignoreUnknownKeys` and `coerceInputValues` (`NytkaApi.kt`), so a new field on an
  older app is ignored, and a `null` for a non-null field with a default becomes the default. The
  server keeps `name` non-null on role suggestions for this reason (server spec, Conflicts 2): today's
  app shows "A voice may be Repairman".
- `ReviewRow.of` returns null for a kind the app does not know, so the server's new kind `tag` is
  invisible to today's app, never answered blind.
- The banner (`SuggestionBanner.kt`, `ConversationViewModel.bannerOf`) shows at most one name
  suggestion, the most confident, read from `GET /people/suggestions` and filtered on the phone.
- `ServerInfo` knows `offline-sync`, `voice`, `people`, `voice-groups`, `review`. The server adds
  `tags`, `tag-suggestions` and `roles`, one per part.
- Search (`ui/search/`) is unchanged by this spec.

## Screens

### Chips

A tag shows as a small assist chip with the name as stored (`repairman`, `робота`). Rows show at most 3
and "+N"; the conversation screen and the person page show all, under the title. Chips on rows are not
tappable (the row opens the item); on a page, tapping a chip opens the matching list filtered by it.

### Adding and removing

On the conversation screen and the person page, **+ Tag** opens a sheet: a text field and, below it,
the tags in use (`GET /tags?q=` as you type, most used first). Picking one or pressing Done sends
`PUT .../tags/{name}`; the answer is the item's tags, which replace the chips. A chip's ✕ (content
description "Remove tag {name}") sends `DELETE` and drops the chip at once, restoring it with a
snackbar on failure. `400` shows "Use letters, digits, - or _ (up to 32)"; `409` "This item has 20
tags." The app normalizes nothing itself: the server's answer is the truth.

### Filter

The Conversations and People top bars gain a **Tag** action that opens the same list of tags in use
(with counts). Picking one shows a dismissible chip "Tag: work" under the bar and reloads the list with
`?tag=`; clearing it reloads the full list. The filter lives in the view model's saved state, so it
survives rotation, and is not saved across launches. An empty filtered list says "Nothing tagged
{name}".

### Proposals

- **Banner.** The conversation screen reads `GET /tags/suggestions` (pending) and keeps those of this
  conversation with no person. Below the name banner (or alone) one row: "Suggested tags", each tag as a
  chip with ✓ and ✕ (text buttons "Add" and "Not this" for TalkBack). Accept adds the chip to the
  conversation; reject hides it. Proposals for people in this conversation are left to the inbox and the
  person page.
- **Person page.** The same row for pending proposals with that person's id.
- **Review inbox.** Kind `tag`: "Tag this conversation {tag}?" or "Tag {person} {tag}?", Accept and
  Reject, tap opens the conversation (or the person, for a person's tag). Answers go through
  `POST /review/tag/{id}/accept|reject` as the other kinds do.

### Roles

- **Banner and inbox, kind `name`:** with `role` and `named = false`: "{label} may be the {role}"; with a
  name and a role: "{label} may be {name}, the {role}". The role is shown as the server sends it
  (`repairman`, `майстер`). Accept and reject are unchanged.
- **People list and person page:** a person with `named = false` shows their name ("Repairman") and a
  second line "Name not known yet", and the page's ⋮ Rename reads **Add name**. Renaming sends the usual
  `PATCH`; the server sets `named`.

## Decisions

**One client.** `TagsClient` (`core/api`): `tags(q)`, `addConversationTag`, `removeConversationTag`,
`addPersonTag`, `removePersonTag`, `conversations(tag, before, limit)`, `people(tag)`,
`suggestions()`, `answerSuggestion(id, accept)`. The tag filter calls `TagsClient`, the unfiltered list
keeps calling `ConversationsClient` and `PeopleClient`, so their fakes stay. Models gain optional
fields only: `ConversationSummary.tags`, `ConversationDetail.tags`, `Person.tags`, `Person.named = true`,
`PersonPage.tags`, `PersonPage.named = true`, `NameSuggestion.role`, `NameSuggestion.named = true`,
`ReviewProposal.tag`, `role`, `named`.

**Feature gates.** Chips, add, remove and the filter need `tags`; proposals need `tag-suggestions`;
role wording needs `roles`. Without a flag the part is hidden, not "needs an update": tags are new, and
an older server simply has none. A `404` on a tag route with the flag listed reads as "This server
needs an update", as `ServerNotices` does today.

**Token scope.** The app keeps requiring `admin`. Reads take `read` on the server; every write needs
`admin`, and a `403` shows "The app needs an admin token."

**Nothing applies itself.** A proposal or a role becomes a tag only from a tap on Accept; no code calls
an accept route on load.

**Privacy.** Tag names are personal ("therapy"): never in a log line, the debug report or a
notification. Nothing about tags is stored on the phone; every list is read from the server.

**Strings.** Every text above in both languages, under a header comment per task. View models emit
typed notices (`TagNotice`), as `VoiceNotice` does. Ukrainian wording keeps the role as sent: "Можливо,
{label} — це {role}", "Можливо, {label} — це {name}, {role}", "Ім'я ще невідоме", "Додати тег",
"Тег: {name}", "Немає нічого з тегом {name}", "Запропоновані теги", "Додати", "Не цей".

## Conflicts between the server API and the app

1. **Role-only suggestions carry a name.** Decided on the server so today's app reads them sensibly.
   The new app tells them apart by `named`.
2. **Proposals have no `conversationId` filter.** As with name suggestions, the app reads the pending
   list (at most 200) and filters on the phone.
3. **One tag per filter.** The server takes one `tag`; the app offers one.

## Out of scope

- Tags in Search, on tasks, memories or bookmarks; tag colors.
- Renaming, merging or deleting a tag in the app (the server has the routes; a later "Manage tags"
  screen).
- A cache of tags on the phone.

## Review focus

1. **Nothing applies itself:** accept routes only from click handlers.
2. **No tag text in logs** or the debug report.
3. **Older servers:** no flag, no tag UI; no crash.
4. **Strings** in both files (the parity test).
