# Developer mode

Measurements, the fake pendant, server settings, access tokens and webhooks. Back to the [README](../../README.md).

Tap the version under Device → About seven times. Developer mode shows the Bluetooth packet rate
and loss, the queue, the last upload and the server's status. Under **Pendant storage**, when the pendant can
store and the server supports it, it shows the pendant's ring (read, write, capacity and dropped),
the last transfer status, the measured rate, the loss of live audio during a sync, the frames dropped
for a mute, the records dropped for a bad stamp, the pendant's clock skew and the number of clock
segments. It has a fake pendant that replays a bundled text-to-speech recording,
a **Request high-priority connection** switch (off by default; capture restarts, and each connection then asks
Android for a 11.25 to 15 ms interval, to compare how often the link drops with and without it),
a button that saves the next 60 seconds of frames as a test fixture, and **Copy debug report** for
bug reports, which never includes the token. With a model imported, the
[Transcribe on this phone](device.md#transcribe-on-this-phone) screen gains a **Benchmark**. A saved fixture holds the voices of whoever spoke: keep
it private. Its **Server** section holds three screens.

**Server settings** edits the settings your server keeps in its database, grouped by prefix: `stt`,
`conversations`, `audio`, `llm`, `memories` and `search`. A setting that the server's `.env` sets is
locked: "Set by the server's environment". API keys show only **Set** or **Not set**, because they
live in the server's environment and the app can neither read nor change them. **Save** sends only
what you changed, all or nothing, and an emptied field restores the default. The server uses a new
value from its next job, with no restart. The `people` keys are also reachable from People → ⋮ → People settings,
and `llm.outputLanguage` from the Device screen's [Language of summaries](../how-to/everyday-use.md#everyday-use). What each setting does is in the
[server docs](https://github.com/nytka-app/server/blob/main/docs/reference/configuration.md#configuration).

**Access tokens** lists the server's named tokens: name, scope, when it was made and last used, and
whether it is revoked. **Create token** asks for a name and a scope, **Read** (conversations, tasks,
memories and search over the API, and MCP for agents) or **Admin** (everything, this app included).
The token shows once, with **Copy**; Nytka cannot show it again. **Revoke** ends a token at once. Give
[MCP clients](https://github.com/nytka-app/server/blob/main/docs/reference/api.md#mcp) a read token.

**Webhooks** lists the server's webhooks, each with an on and off switch and the status of its last
delivery. **+** adds one: a URL, the events (a conversation is summarized, a task is created, a task
is completed, a memory is created, or all of them) and a description. The signing secret shows once,
with **Copy**. A webhook's own screen has **Send test**, its last 30 deliveries and **Delete**. How a
receiver checks the signature is in the [server docs](https://github.com/nytka-app/server/blob/main/docs/reference/api.md#webhooks).
