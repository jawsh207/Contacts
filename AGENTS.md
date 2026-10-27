# Notes for coding assistants

See README.md for the build setup and the AGENTS.md conventions under each module.

## Attribution

Don't name or credit any AI assistant anywhere in this repo: not in files, commit messages,
commit authors or trailers (no `Co-Authored-By` or session links), release notes, or pull
requests. Commit as the repo owner, `jawsh207 <76641448+jawsh207@users.noreply.github.com>`.

## "Build release"

When the user says "Build release", publish a new release. (The repo owner can also run the
**Build release** workflow from the Actions tab, which bumps the version and writes the changelog
from commit subjects; doing it by hand as below gives more readable notes.)

1. Collect the changes since the last release: `git log --format=%s <last release commit>..main`
   (the last release is the newest `v*` tag on GitHub, or the `release` branch).
2. In `version.txt`, raise line 1 (version code, today's date as YYYYMMDD, or +1 if that is not
   higher) and line 2 (version name, always three parts MAJOR.MINOR.PATCH: every release bumps
   the patch number, e.g. 1.2.1 → 1.2.2, whatever it contains; the minor or major number changes
   only when the user asks for it, resetting the parts after it to 0). The release after 1.2 is
   1.2.1.
3. Add a `## <version name>` section at the top of `CHANGELOG.md` (one line per bullet or paragraph, no hard wraps) describing the changes in plain,
   user-facing words, grouped by area. This becomes the notes on the Releases page.
4. Commit, push `main`, then `git push origin main:release`. (CI creates the `v<version>` tag, so
   no tag push is needed.)
5. Wait for the Actions run, check its annotations, and confirm the release signing certificate
   SHA-256 is `09d3184e2e50bd0f1fff3d66cebfc6935c57301da868d69cef0d59586c8a1f5a`.
6. Report the release link and a short summary.
