# Why the link drops

What the diagnostics say about the Omi link dropping, what they rule out, and the cheapest next
experiment. Written for [nytka-app/server#280](https://github.com/nytka-app/server/issues/280) ("Link drops: 35 per
connected hour on 0.5.0, most of the down time in Doze"). Back to the [README](../../README.md).

**Short answer.** Most drops are not Doze and not storage reads. Three quarters of them end a link
that lived under a minute, and two in five end one that lived 32.5 s, with status 147, whatever the
app does. Links that get past the first minute drop about every 25 to 35 minutes. No change to how
the app connects is justified by this data; the next step is to capture who ends the link.

## What was measured

The server's `diagnostics` table starts on 2026-10-04, so the issue's 0.5.0 numbers (30 September)
cannot be reproduced. Two windows of app log rows were read instead, each with the connection
state changes (`OmiPendant`), the power state lines (`PowerStateLog`) and the sync lines
(`StorageSync`):

| Window | App versions | Links | Connected | Drops per connected hour | Status 147 |
|---|---|---|---|---|---|
| 2026-10-22 to 10-30 | 0.24 to 0.26.3 | 544 | 82.6 h | 6.6 | 521 |
| 2026-10-04 to 10-06 | 0.10 to 0.17 | 364 | 39.4 h | 9.2 | 348 |

Both are far below 35 per hour, and almost every drop is status 147 (`LINK_WENT_SILENT`, the app's
own name for a number Android does not document).

## Findings

1. **Flap episodes.** 74% of the drops end a link shorter than 60 s, and those links hold about 4%
   of the connected time (3.1 h of 82.6 h). 229 of 544 links (10-22 to 10-30) and 155 of 364
   (10-04 to 10-06) last between 30 and 35 s, almost all on a few days (10-23: 160, 10-27: 56).
   In every one the setup finishes normally (services, MTU, codec, storage, settings) and the link
   goes silent at about 32.5 s. A fixed lifetime points at a timer, not at radio noise. Without
   them, links of 60 s or more drop 1.75 times per connected hour (10-04 to 10-06: 2.44).
2. **Doze does not explain it.** Reconnecting is not slower in Doze: the time from `connectGatt` to
   connected has a median of 1.7 s in Doze and 3.9 s outside (10-04 to 10-06: 2.7 s and 2.0 s).
   Doze is 56% (71%) of the logged time and holds about the same share of the time between a drop
   and the next connect. The drop rate of links of 60 s or more is similar in every screen and
   Doze state (1.2 to 2.1 per hour; 1.6 to 4.5 in the earlier window). The issue's "62% of the down
   time in Doze" does not show up.
3. **Storage reads raise the risk a little and are not the main cause.** Counting only links
   outside the 30 to 35 s bin, a link drops within 30 s of a read window starting 6.0% of the time
   (12 of 200), against 2.0% for any other moment of the same links; within 60 s it is 9.5% against 3.5%
   (10-04 to 10-06: 7.1% against 2.7% and 17.3% against 4.7%). More than 90% of read windows end
   without a drop, and flap episodes also happen with no sync. The comparison is confounded: a
   window can only start on a link that survived its first seconds.
4. **ADVANCE answers "not ready" inside the flaps.** The `ADVANCE(n) answered 9` line (status 9,
   `StorageStatus.NOT_READY`, "the recorder's storage is still starting") is in 66% of the
   30 to 35 s links (75% in the earlier window), against 9% (8%) of the other short links and 28% (7%) of the
   long ones. The first one comes about 4.5 s after the link comes up and it repeats every 10 s
   until the drop. 35 other ADVANCEs timed out (`-1`). This is a lead, not a cause: a third of the
   flaps (a quarter in the earlier window) have no ADVANCE line at all.
5. **Signal is weak, not the discriminator.** RSSI while connected sits around -65 to -100 dBm, with
   long stretches near -85 to -97 on 10-23. The hourly comparison was too slow to finish
   against the read-only database, so there is no per-hour correlation.
6. **The battery-optimisation exemption cannot be judged.** The app neither asks for
   `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` nor logs whether it has it, so no row says which phone
   had it.

## What this rules in and out

- Not supported by the data: trying a direct connect first (`autoConnect=false`), because Doze does
  not slow reconnects; and an exemption prompt, because nothing shows it would change the drops.
- Still open, in order of cost: who ends the 32.5 s link. Candidates, none verified: the 30 s ATT
  transaction timeout (a setup request that never gets its response, plus about 2.5 s of setup), a
  30 s security procedure (the app never bonds), or a timer in the pendant's firmware that restarts
  it while its storage is not ready.

## Cheapest next experiments

1. **Capture one flap.** Turn on Bluetooth HCI snoop logging in the phone's developer options, wait
   for an episode, and read the disconnect event: its reason code says whether the phone's stack, the
   pendant or the radio ended the link. `adb logcat | grep -iE "bt|gatt"` during an episode shows the
   stack side. Needs the phone and a cable; no code.
2. **Log what the data lacks**, outside the pendant driver: bond state and the battery-optimisation
   exemption at connect, and the pendant's firmware string with each flap. With them the next export
   can split the flaps by firmware and by phone setting.
3. Only then test a connect-strategy or exemption change, against the same two metrics: drops per
   connected hour for links of 60 s or more, and the count of links between 30 and 35 s.

## Method

Log rows were read from the server with `lab sql nytka-postgres`, one day per query (a whole-range
query hits the statement timeout), from `diagnostics` where `payload->>'kind' = 'log'`, as in
[Everyday use](../how-to/everyday-use.md). A link runs from
`onConnectionStateChange ... newState=2` to the next `newState=0`; the power state is the last
`PowerStateLog` line before the moment; a read window is a `window from` line from `StorageSync`.
No audio, transcript, token or server address is in the rows, and none is quoted here.
