# Get started

What you need, installing the app and the first run, about 5 minutes. Back to the [README](../README.md).

## What you need

- A phone on Android 12 or later, with Bluetooth on.
- An Omi pendant on consumer firmware 3.0.x. Nytka checks the pendant's audio codec when it
  connects and tells you if the firmware needs an update. Offline sync needs firmware 3.0.20 or
  later; live capture works without it.
- A Nytka server the phone can reach, with its address and an `admin` token: `Nytka__AdminToken` in
  the server's `.env`, or an admin token made in the app. A `read` token is refused ("The app needs
  an admin token."). The
  [server README](https://github.com/nytka-app/server#install-in-15-minutes) sets one up with
  Docker Compose. Keep the address and the token where you can paste them on the phone: the token
  is a long random string. The tabs, the sync and the server settings below need server 0.4.0 or
  later. An older server still takes audio, and a screen it lacks says "This server needs an update".

## Install

1. Uninstall the official Omi app, or at least force-stop it: two apps cannot hold the pendant at
   once. To update the pendant's firmware later, install it again, update, and uninstall it.
2. On the phone, download `nytka-<version>.apk` from
   [Releases](https://github.com/nytka-app/android/releases/latest) and open it. Android asks once
   whether your browser may install apps; allow it, then tap **Install**. From a computer:
   `adb install nytka-<version>.apk`.

A newer APK installs over the old one the same way and keeps the queue and the settings.

## Install with Obtainium

[Obtainium](https://github.com/ImranR98/Obtainium) installs and updates Android apps straight from
their GitHub releases. Add this repository, `nytka-app/android`, as an app in Obtainium: it finds
`nytka-<version>.apk` under [Releases](https://github.com/nytka-app/android/releases/latest), the
same file the manual install step above uses, and offers the next one as it is published.

## First run (about 5 minutes)

Charge the pendant and keep it next to the phone. Open Nytka: the setup has four steps, shown as four segments at the top with the step in words.

1. **Server.** Enter the server's address (`https://…`) and an `admin` token, and tap **Connect** — or tap **Scan QR code**
   (offered first) and point the camera at the QR code the server's setup page shows, which fills both
   fields and runs the test for you. The address and token are set in a monospace font, and **Show**
   reveals the token. Scanning asks for the camera permission
   the first time; Nytka never uses it for anything else. The next step opens once the server answers
   and accepts the token. A `read` token stops here with "The app needs an admin token."
2. **Permissions.** Two rows say what each permission is for and show a check and **Allowed** once it
   is granted. Tap **Allow**. Android shows a prompt for *Nearby devices*, so Nytka can reach
   the pendant, and on Android 13 and later a second one for *Notifications*, for the recording
   notification and the alerts. There are no others, and Nytka never uses the phone's microphone or
   location.
3. **Consent.** Read the four points: people near you are recorded too, how to stop and delete, that
   recording people without their consent is illegal in some places and you answer for following the
   law (and the pendant also records while the phone is away), and where your recordings live. Tick
   **I understand**, which starts unticked, and tap **Continue**.
4. **Pairing.** Turn the pendant on, tap **Pair** and pick it in the list that opens.
   **Set up later** skips this; the **Device** screen has the same **Pair pendant** button.

Nytka now opens on **Conversations**. The capture status at the top reads **Waiting for pendant**,
with a broken ring turning slowly, while the phone connects to the pendant, then **Recording**, with a
green disc and a solid line under the top bar: the first run is done.
Say something: within a minute the status card shows "Server: Last upload …", and the speech shows
up under **Conversations** a few minutes after it is said. Pull the list down to refresh if a new
conversation hasn't appeared.

A server on a VPN such as Tailscale or WireGuard, without HTTPS, needs the **Private network (allow
plain HTTP)** switch on the server step and an address like `http://<address on the VPN>:8080`. It
allows plain `http://`, which carries your audio unencrypted: use it only on a network you trust.
