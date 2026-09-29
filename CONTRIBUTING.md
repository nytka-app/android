# Contributing

Issues and pull requests are welcome. For anything larger than a fix, open an issue first so we
can agree on the approach; the [vision](https://github.com/nytka-app/server/blob/main/docs/vision.md)
says what Nytka is and is not. Server problems belong in
[nytka-app/server](https://github.com/nytka-app/server/issues).

## Set up

You need JDK 21, the Android SDK with platform 37.0 and build-tools 37.0.0, and
[gitleaks](https://github.com/gitleaks/gitleaks).

```bash
git config core.hooksPath .githooks
cp .private-terms.example .private-terms   # list what must never appear in this repository
./gradlew check
```

The hooks refuse commits whose author or committer is not a GitHub noreply address, scan staged
files and messages for the terms in `.private-terms`, and run gitleaks. Without a pendant, turn on
developer mode and its fake pendant, which replays `app/src/main/assets/fake_pendant.nytk`.

## Rules

- Conventional commits (`feat:`, `fix:`, `docs:` ...); they drive the changelog and the version.
- Run `./gradlew ktlintFormat` before committing; `check` fails on what it cannot fix.
- Every change comes with tests. Capture tests drive `FakePendant`: never commit a recording of a
  real person, not even your own.
- The chunk format is shared with the server: change its golden bytes in both repositories or in
  neither.
- A Room schema change needs a migration and the exported schema under `core/schemas/`.
- Never log audio, transcript text or tokens, and never put them in the debug report.
- Never request `RECORD_AUDIO`.
