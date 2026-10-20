# Offline sync

How the app copies the audio the pendant stored while the phone was away. Back to the [README](../../README.md).

While the phone is out of range, the pendant stores what it hears on its own card. When the phone
comes back, Nytka reads that storage and uploads it with the times the audio was recorded, so it
lands in the right conversations. It needs pendant firmware 3.0.20 or later and server 0.4.0 or
later. On anything else the **Pendant storage** card on the **Device** screen says why, or is not
there, and live capture is not affected.

A sync starts by itself whenever the pendant connects. The card shows it, and the recording
notification adds "· syncing 42%". **Stop** holds it until the next connection or **Sync now**.

| The card says | What it means |
|---|---|
| "Nothing stored", or "About 12 min stored. It syncs when connected." | That much was waiting at the last check: the line shows "Nothing stored" until the phone has read the pendant. **Sync now** starts a sync when the pendant is connected. |
| "Checking the pendant…" | Nytka is asking the pendant what it holds. **Stop** cancels. |
| "Syncing 42%, 38 KB/s, about 3 min left" | Under way. The rate depends on the Bluetooth link. |
| "About 2 h stored. Waiting for your answer." | The first sync found a large backlog. Tap **Import or discard…** and answer the question below. |
| "Waiting for uploads: the queue is 61% full." | The phone's queue is over 50% full. The sync waits and goes on below 40%, so it never drops audio. |
| "Paused: the link dropped. It resumes when the pendant reconnects." | The pendant went out of range. The next connection carries on from where it stopped. |
| "Paused: live audio is losing packets. It resumes shortly." | Live audio lost over 5% of its packets in one read window (Nytka needs at least 50 live packets to judge). The sync pauses for 30 seconds; the third pause in a row ends it until the next connection or **Sync now**. |
| "The pendant did not answer (status 9). Trying again in 30 s." | The pendant's storage failed to answer. Status 9 means its card is not ready yet, and -1 is a timeout. Nytka tries again after 30 seconds, then after 2 minutes, then every 10 minutes. |
| "The server did not answer. Trying again in 30 s." | The phone could not reach the server. Same tries. |
| "Stopped. Sync now continues where it left off." | You pressed **Stop**. |
| "Offline sync needs firmware 3.0.20 or later; this pendant has 3.0.19. Update it with the official Omi app." | Update the firmware, then uninstall the official app again. |
| "This pendant does not offer offline storage." (or "…did not report its firmware", "…its features") | The pendant cannot store audio for Nytka to read. Live capture is not affected. |

Under the status line, the card shows **Synced** and **Lost** once they are above zero. They count
audio since Nytka's recording service last started. *Lost* is audio the pendant no longer held when
Nytka came to read it: its storage filled and overwrote the oldest audio, or the firmware freed it
early after an interrupted transfer. "Pendant clock was off by 6 min, times corrected" appears when
the pendant's clock had run wrong (after a crash, say) and Nytka fixed the times. The first sync of
a pendant that holds more than 2 hours asks "Import the stored audio?": **Import** reads it all,
which takes a while, and **Discard…** frees it on the pendant unread, after a confirmation.

- **Storage is freed after your server has the audio.** Nytka tells the pendant to free stored audio
  only after the server accepted it; the exceptions are audio dropped for a mute and a backlog you
  discard. If you kill Nytka or switch Bluetooth off in the middle of a sync, the next sync carries on
  without repeating text. Firmware 3.0.21 also frees a little on its own during a transfer, so an
  interruption can cost a few seconds, which the card counts as *Lost*. Stored audio your server
  refuses (`400`, `409` or `413`) stays parked on the phone, and the pendant keeps its copy.
- **Muted stretches are dropped.** The pendant keeps no mute state, so it records while the phone is
  away whatever you chose. Nytka logs every mute change and, before it uploads anything, drops each
  stored frame within 2 seconds of a muted stretch. A mute stays open until you unmute, so it covers
  a whole absence, and muted audio never reaches the server.
- **The pendant's clock.** At each connection to a pendant that supports offline sync, Nytka sets
  its clock from the phone. The pendant stamps stored audio with that clock.
- **What sync cannot recover.** Audio that a stalled Bluetooth link dropped while the phone was
  connected (the pendant never stores it), audio the pendant overwrote or freed early (shown as
  *Lost*), a backlog you discard, and muted stretches, which are dropped on purpose.
