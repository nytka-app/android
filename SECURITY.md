# Security policy

## Reporting a vulnerability

Use GitHub private vulnerability reporting on this repository (Security tab → "Report a
vulnerability"). Don't open a public issue. Expect an acknowledgement within 7 days.

## Scope notes

The app holds the token of its user's Nytka server (encrypted with an Android Keystore key) and
audio waiting to upload, and it shows transcripts of the wearer and of the people around them.
Report any way another app can read the queue, the settings or the token, or make Nytka start or
stop capture; any path where audio, a transcript or the token leaks into logs, the debug report,
backups or files; any way audio leaves over plain HTTP while the private-network switch is off;
and any way the pendant keeps recording while the app shows it as muted.

## Supported versions

The latest release.
