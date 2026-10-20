# When something is off

What a message means and what to do about it. Back to the [README](../../README.md).

| What you see | What to do |
|---|---|
| "The server refused the token." on **Test connection** | The token differs from `Nytka__AdminToken` in the server's `.env`. Paste it again, whole. |
| Another error on **Test connection**, such as "Failed to connect" or "Unable to resolve host" | The phone cannot reach that address; an unreachable one takes 15 seconds to fail. Open the address plus `/healthz` in the phone's browser: `{"status":"healthy"}` means the server is up, so recheck the address and the private-network switch in Nytka. Anything else is the network: the VPN is off, a firewall is in the way, or `NYTKA_BIND` on the server points elsewhere. |
| "Without nearby devices Nytka cannot reach the pendant." | Android stops asking after two refusals. Open Nytka's app info (long-press its icon), allow *Nearby devices* under Permissions, come back and tap **Allow** again. Nytka resumes first run at the step you reached. |
| "Pendant not supported", or a message about the audio codec | Update the pendant's firmware with the official Omi app, then uninstall that app again. |
| The chip stays on "Waiting", or the pairing list is empty | The official Omi app (or another phone) still holds the pendant, Bluetooth is off, or the pendant is out of range or flat. |
| "Paused: The server refused the token." | The server's token changed. Enter the new one under Device → Settings; nothing queued is lost. |
| "The app needs an admin token." | The token is a `read` token. Enter the server's `Nytka__AdminToken`, or an `admin` token made under Developer mode → Access tokens. |
| "This server needs an update" | The server has no endpoint for that screen. Update it to 0.4.0 or later; capture keeps working meanwhile. |
| "Unreachable since …" | The phone cannot reach the server. Audio waits in the queue and uploads by itself. |
| A conversation shows **Summary failed** | The server's language model failed: a wrong key, an endpoint that is down, a model name it does not know. The server's admin finds the reason at `GET /api/v1/status`, under `ai.lastError`. After the fix, use ⋮ → **Regenerate summary**. |
| No conversation gets a title or a summary | The server has no language model set up. Set its base URL and model in the server's `.env`, or under Developer mode → Server settings; see the [server docs](https://github.com/nytka-app/server/blob/main/docs/reference/configuration.md#the-language-model). |
| **Regenerate summary** says "No summary can be made now: the conversation is still open, or the server has no language model set up." | Wait until the conversation ends (by default after two minutes without speech), or set up the model. |
| The **Device** tab has no **Pendant storage** card | The card shows when a pendant is paired and the server lists `offline-sync` (server 0.4.0 or later). Its other states are in [Offline sync](../explanation/offline-sync.md#offline-sync). |
| Conversations appear but stay empty after you pull down to refresh | The server could not transcribe the speech. Turn on [developer mode](../reference/developer-mode.md) and tap **Check status**: it shows the server's last error, usually the transcription URL, key or model in the server's `.env`. |
| The recording notification disappears on its own | Some phones stop background apps. Set Nytka's battery use to *Unrestricted* in its system settings; [dontkillmyapp.com](https://dontkillmyapp.com) has the steps for each maker. |
