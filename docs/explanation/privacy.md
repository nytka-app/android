# What stays on the phone

What the app keeps, what it sends, and to whom. Back to the [README](../../README.md).

Audio waiting to upload, in the app's private storage, until the server has it; the settings; the
token, encrypted with a key that never leaves the Android Keystore; how far the pendant's storage
has been read; the time and result of the last firmware check; the ids (not the text) of the meeting briefs already posted, at most 50; the [phone-context](../reference/device.md#phone-context) stretches not yet sent (kind, route, two times, and for activity and now-playing one word from a fixed list: never an app name, a title or an artist); the names and coordinates of your [saved places](../reference/device.md#location-tags) and the one coarse reading still waiting to be tagged; a log of when you muted and unmuted (times and on or off, nothing else); and, only with [Transcribe on this phone](../reference/device.md#transcribe-on-this-phone) on, the model you copied, copies of the audio still waiting for it and the draft text it made, kept 7 days and never uploaded. Android
backups and phone-to-phone transfers skip all of it, so a new phone starts with first run. The
pendant keeps the audio it stored until your server has it. The app talks to your server and nothing
else, apart from the once-a-day [firmware notice](../reference/device.md#pendant-firmware-notice) (GitHub, switchable): no
analytics, no crash reporting, no Omi cloud.

Diagnostics samples hold counters and short status words: no audio, no words, no server address, no
token. They stay on the phone for 7 days and leave it only when you export them or switch on *Send
diagnostics to my server*, which sends them to your own server and nowhere else.
