# Releasing

How versions are cut. Back to [CONTRIBUTING.md](../CONTRIBUTING.md).

A version number means progress: the patch counts what shipped, the minor counts milestones.

- Features and fixes ship as patch releases (`0.N.x`). Conventional commits on `main` keep the
  release-please PR up to date, as before; merging it tags the release. A `feat:` bumps the patch,
  not the minor (`bump-patch-for-minor-pre-major` in `release-please-config.json`). A breaking
  change still bumps the minor before 1.0 (`bump-minor-pre-major`).
- A milestone is a named goal planned in Plane, not a number. When it closes, cut the minor by
  hand: push an empty commit to `main` whose message ends with the `Release-As` footer, and
  release-please opens its PR for that version.

```bash
git commit --allow-empty -m "chore: release 0.<N+1>.0" -m "Release-As: 0.<N+1>.0"
```

Replace `<N+1>` with the next minor: after `0.26.x` that is `Release-As: 0.27.0`. Never edit
`.release-please-manifest.json` or a version string by hand.
