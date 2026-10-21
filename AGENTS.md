# Notes for coding assistants

See README.md for the build setup and AGENTS.md conventions under each module.

## "Build release"

When the user says "Build release", publish a new release:

1. Collect the changes since the last release: `git log --format=%s <last release commit>..main`
   (the last release is the newest `v*` tag on GitHub, or the `release` branch).
2. In `version.txt`, raise line 1 (version code, today's date as YYYYMMDD, or +1 if that is not
   higher) and line 2 (version name: minor bump for features, patch bump for fixes only).
3. Add a `## <version name>` section at the top of `CHANGELOG.md` (one line per bullet or paragraph, no hard wraps) describing the changes in plain,
   user-facing words, grouped by area. This becomes the notes on the Releases page.
4. Commit, push `main`, then `git push origin main:release`. (Tag pushes are blocked from the assistant's
   environment; CI creates the `v<version>` tag.)
5. Wait for the Actions run, check its annotations, and confirm the release signing certificate
   SHA-256 is `09d3184e2e50bd0f1fff3d66cebfc6935c57301da868d69cef0d59586c8a1f5a`.
6. Report the release link and a short summary.
