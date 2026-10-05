# People: acceptance on a phone

Run these on a phone with a debug build of `main` against a server from its `main` with
`people.voiceMatching` on, your voice enrolled, `audio.retentionDays` 14 and a calendar feed. A failed
check goes back to the task that owns it (named in brackets). Source: `docs/plans/2026-10-05-people.md`.

- [ ] 1. **Older server** (0.12, or the People routes blocked) [A-P2, A-P3, A-P5]: People tab lists
  people, rows open the old dialog; no cards, no inbox icon; the brief switch says the server needs an
  update. No crash.
- [ ] 2. **People tab on `main`** [A-P2, A-P3]: people by last seen with fact counts (server 0.15 or
  later; by name before). Rename, merge, delete and delete-and-forget each work from the person page.
- [ ] 3. **Person page** [A-P3]: edit and clear the note; add a fact, add it again (`409` message);
  edit and delete one; delete asks first; the deleted fact does not come back after "Regenerate
  summary".
- [ ] 4. **Name suggestion** [A-P6]: talk with a second person who says "I'm Olena" for a minute; after
  the summary the conversation shows the banner; Accept names the voice; lines show "Olena"; tapping
  "Olena" opens her page.
- [ ] 5. **Cards** [A-P4]: with voice matching on and two conversations with an unnamed voice, a card
  appears and its clip plays (TalkBack reads "Play clip"); Save names it; Skip hides another for 7 days;
  Not a person removes one.
- [ ] 6. **Inbox** [A-P5]: it lists a name, a voice match and a low-confidence label; each accept and
  reject leaves the list.
- [ ] 7. **Meeting briefs** [A-P8]: switch on (allow notifications when asked); a calendar event with
  "Olena" as attendee 30 minutes ahead brings one notification; the lock screen shows only "A meeting
  brief is ready"; no second notification on the next run.
- [ ] 8. **Consent chime** [A-P9]: on, 5 minutes: it chimes while recording; with the pendant muted, no
  chime; with the enrollment screen open, no chime. Also try silent mode and Do Not Disturb and write
  down what you hear (UNKNOWN until you do).
- [ ] 9. **Ukrainian** [all]: switch the phone to Ukrainian; every new screen, card, notification and
  setting reads in Ukrainian (server field messages may stay English).
- [ ] 10. **No personal text in logs** [all]: `adb logcat | grep -iE "nytka"` during 4 to 8 shows no
  name, fact, note, line, event title or URL.

## Tags and roles (app 0.16.0, server 0.19.0 or later)

Source: `docs/plans/2026-10-05-tags.md`. Run after the People checks above.

- [ ] 11. **Tag chips** [A-T2]: open a conversation, tap **+ Tag**, type `Work`, tap Done: the chip says
  `work`. Add `dog walker`: the chip says `dog-walker`. Add a 21st tag: "This item has 20 tags." Tap ✕:
  the chip goes at once. With a read token (not admin) the chips show without **+ Tag** and ✕.
- [ ] 12. **Same on a person** [A-T2]: the person page shows the same chips and sheet.
- [ ] 13. **Filter** [A-T3, A-T4]: tap a chip on a conversation: Conversations shows only that tag with a
  "Tag: work" chip; the chip's ✕ clears it. The **Tag** action opens a picker of tags in use, most used
  first with counts. An empty result says "Nothing tagged x". The same on People. Leaving the app
  clears the filter.
- [ ] 14. **Proposed tags** [A-T5]: after a new conversation is summarized, a "Suggested tags" row
  appears (up to 3). ✓ adds the chip, ✕ drops it and it never comes back for that conversation. The
  review inbox lists the same proposals.
- [ ] 15. **Roles, the repairman case** [A-T6]: talk with someone who is introduced by what they do
  ("майстер приїхав"). The banner says "A voice may be the repairman" with **Add as repairman**: accept
  it, and a person "Repairman" appears marked "Name not known yet" and tagged `repairman`. Later, when
  his name is said, the next suggestion for that voice says "may be <name>": accept it, and the person
  is renamed (or merged with an existing person of that name). **Add name** with a name that already
  exists says it is taken (it never merges).
- [ ] 16. **Older server** [all tag tasks]: against a server without `tags`, nothing about tags shows
  and no tag call is made.
- [ ] 17. **Ukrainian** [A-T2 to A-T6]: every tag and role screen reads naturally in Ukrainian.
- [ ] 18. **No tag or role text in logs** [all]: `adb logcat | grep -iE "nytka"` during 11 to 15 shows
  no tag name, role or person name.
