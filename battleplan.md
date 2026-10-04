# Travelz Android / Offline Battle Plan

## Purpose

Travelz is a hidden trip stash, not a trip planner. The GitHub Pages site is a
PIN-curtained online view of public-readable trip data. `travault13` is the
private GitHub repository for PDFs, scans and other documents. The Android app
provides quick access to local copies of those files when the network is poor
or absent.

The PIN on Pages is a visual curtain. It does not encrypt or access-control
the public JSON. Private documents must remain in `travault13`.

## Architecture

```text
Travelz Pages                  online public-readable trip view
Travelz Android               native offline document shelf
       |
       | fixed Termux RUN_COMMAND operations; bounded JSON replies
       v
~/.local/bin/travelzctl       local helper, installed in Termux private home
       |
       +-- ~/git/travelz      public trip data clone
       +-- ~/git/travault13   private document clone
       +-- termux-open        Android PDF/image viewer handoff
```

The app has no GitHub token, SSH key, Git implementation or duplicate document
storage. It asks Termux for status, current trip details and a curated list of
document IDs. It sends an ID back to open a document. `travelzctl` validates the
manifest and path, then asks Android to view the local file.

The app follows the `com.termux.RUN_COMMAND` and one-shot PendingIntent pattern
proven by Overtura's Android companion. `allow-external-apps=true` and the
`com.termux.permission.RUN_COMMAND` grant are required. Java code uses fixed
operations and bounded result parsing. There is no generic shell-command UI.

Android blocks a background Termux process from launching a PDF viewer on the
Pixel. On a document tap, Travelz brings Termux to the foreground first and
then sends the background `open` request. The viewer opens from Termux. Back
from the viewer currently returns through Termux; returning directly to
Travelz would be a future usability improvement.

## Trip layout and source of truth

Both repositories use the same stable trip ID:

```text
travelz/trips/2026-czechia/index.html
travelz/trips/2026-czechia/trip.json
travault13/trips/2026-czechia/docs.json
travault13/trips/2026-czechia/*.pdf
```

The first entry in `travelz/trips/index.json` is the current trip for V0. The
private `docs.json` lists only chosen documents with `id`, `label`, `file` and
`type`. No passport placeholders are in the manifest. The initial real batch
contains a flight e-ticket, a hotel receipt and a travel insurance PDF.

The public trip JSON also links to those files on GitHub for online use; GitHub
sign-in controls access to the private repository. ChatGPT/Codex can maintain
both repositories through its authorized GitHub workflow.

## Phone state

The Pixel has fresh clones in Termux private storage at `~/git/travelz` and
`~/git/travault13`. Its older public clone at
`/storage/emulated/0/GIT/travelz` has divergent local history. It was left
untouched. `travelzctl` prefers the private-home clone and uses the shared
clone only as a fallback when `~/git/travelz` is absent.

The installed helper should be a symlink from `~/.local/bin/travelzctl` to the
file in the private-home Travelz clone, so a successful Git sync updates the
helper too. See [the helper setup](tools/termux/README.md) and
[Android setup](android/README.md).

## V0 scope

Implemented:

1. Current trip title, route and dates from the local public clone.
2. Curated private document list from the local vault clone.
3. Tap a document to open the local PDF in an Android viewer.
4. Manual fast-forward-only sync and last successful sync time.
5. Local repo, permission and helper diagnostics.
6. Fixed command bridge with callback IDs, timeouts and bounded JSON parsing.

Intentionally deferred: next-flight prediction, money cards, planning tools,
background sync, notifications, widgets, embedded PDF rendering and archive
trip selection. These do not help the initial hidden-stash use case enough to
justify the additional moving parts.

## Helper contract

`tools/termux/travelzctl` is a small Python 3 command-line program. All
operations return one JSON object and avoid dumping Git stderr or file contents:

```text
status --json     clone existence, branch, dirty state, last sync
sync --json       preflight both clones, pull --ff-only, update last sync
trip --json       current trip's public title, route and dates
docs --json       curated IDs, labels, types and local availability
open <id> --json validate ID and path, call termux-open
```

`sync` refuses dirty repos before either pull. It updates the vault first and
the public repo second. If the second pull fails, the vault may already be
updated; the JSON reports completed operations. It never resets or merges.

The manifest must stay inside the matching vault trip folder. The helper
rejects absolute names, traversal, duplicate IDs and symlink escape. An
unavailable file remains visible in `docs` but cannot be opened. Tests use
temporary fixture repos and files; they do not open real documents.

## Verification status

- Python helper tests cover curated docs, ID-only open, traversal and symlink
  escape, missing files/repos, dirty sync refusal and failed pull state.
- Android Gradle unit-test task, lint and debug APK assembly pass. There are
  currently no JVM unit-test sources; the device flow was checked on the Pixel.
- On the Pixel, app status/trip/docs loaded; manual sync completed; document
  tap brought Termux forward and opened the flight PDF in the installed viewer.
- A Termux `open` call also opened the same PDF directly. Document contents
  were not copied into app storage or logs.
- Full radio-off testing has not been run because it would interrupt the
  remote ADB/SSH session. The read and open paths use local clones and files.

Before treating a future version as complete, repeat `lintDebug`,
`assembleDebug`, helper tests and the Pixel smoke check. When an image is
actually added to the vault, test its viewer handoff too.

## Next improvements if they become useful

- Make the viewer's Back action return directly to Travelz without disturbing
  an existing Termux session.
- Add a compact multi-trip selector after there is a second real trip folder.
- Add more documents by placing them in the matching private trip folder and
  adding a curated entry to `docs.json`.

Keep the primary workflow boring: add a document to the private repo, sync the
phone, tap its label. ChatGPT can help find and maintain the right file.
