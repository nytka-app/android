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
