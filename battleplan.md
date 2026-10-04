# Travelz Android / Offline Battle Plan

## Mission

Build a small native Android companion for **Travelz** that gives fast offline access to the current trip and private travel documents on the Pixel.

The app is not a replacement for the GitHub Pages site. It is the offline/local front end.

The architecture should stay deliberately simple:

- **travelz GitHub Pages** = online dashboard / editable trip hub
- **travault13 private repo** = sensitive documents and private archive
- **Termux on Pixel** = local backend, Git client, private file owner
- **Travelz Android app** = native offline UI that talks to Termux through RUN_COMMAND
- **Android viewer apps** = PDF/JPEG/etc rendering after Termux opens a file

Do not duplicate Git, GitHub authentication, SSH keys, or vault storage inside the Android app.

---

## Existing repos / reference implementation

### Travelz

Repo: `hanenashi/travelz`

Current public site already exists and is live on GitHub Pages.

Current relevant files:

- `index.html`
- `assets/`
- `trips/current/`
- `trips/index.json`
- `sw.js`
- `readme.md`

The Pages site is intended to remain public-safe. Sensitive docs do not belong in this repo.

### Private vault

Repo: `hanenashi/travault13`

This repo is private and is the intended archive for passports, insurance, tickets, hotel receipts, Revolut-related notes, images and other sensitive travel material.

The Android app must never need a GitHub token to access this repo. The local Termux clone owns that job.

### Overtura reference

Repo: `hanenashi/overtura`

Use its Android companion as the proven model for Termux integration.

Especially inspect:

- `android/README.md`
- `android/app/src/main/java/io/github/hanenashi/overtura/TermuxBridge.java`
- `android/app/src/main/java/io/github/hanenashi/overtura/TermuxReply.java`
- `android/app/src/main/java/io/github/hanenashi/overtura/TermuxResultReceiver.java`
- `android/app/src/main/java/io/github/hanenashi/overtura/QueryState.java`
- `android/app/src/main/java/io/github/hanenashi/overtura/MainActivity.java`

Important existing precedent from Overtura:

- native Android / platform views
- no runtime framework dependency
- Termux `com.termux.RUN_COMMAND`
- `com.termux.permission.RUN_COMMAND`
- `allow-external-apps=true`
- background execution through `$PREFIX/bin/sh`
- one-shot `PendingIntent` callback
- request IDs / operation tokens
- result validation
- timeouts / stale result handling
- no reading of SSH keys from Android app

Reuse the pattern, not Overtura-specific session/SSH behavior.

---

## Core design decision

The Android app should **not** directly read Termux private storage.

Instead:

```text
Travelz Android
    |
    | Termux RUN_COMMAND
    v
travelzctl
    |
    +-- sync
    +-- status
    +-- trip
    +-- docs
    +-- open <id>
    +-- paths / diagnostics
```

Termux remains the owner of:

```text
~/git/travelz
~/git/travault13
```

The Android app receives small JSON replies and sends fixed, controlled commands.

For documents, Termux should open the local file through Android intent handling rather than streaming file contents back into the app.

Preferred local viewer flow:

```text
Travelz app -> travelzctl open eticket -> termux-open local.pdf -> Android PDF viewer
```

---

## V0 target

Ship a useful offline companion quickly. Do not turn this into a giant travel-management app.

### Main screen

Aim for something roughly like:

```text
TRAVELZ                         synced 18:10
Czechia 2026

NEXT
CI67  Taipei -> Prague
23:30

DOCUMENTS
E-ticket
Insurance
Passport Stan
Passport wife
Moxy Vienna

MONEY
CZK  Revolut
EUR  Revolut

[ SYNC NOW ]
```

The exact visual design can evolve, but the first version should be clean, dark-mode friendly and usable one-handed on Pixel.

### V0 features

1. Show current trip summary offline.
2. Show a simple next/upcoming item.
3. Show sync state and last successful sync time.
4. Show curated private document list.
5. Tap document -> ask Termux to open local file with Android viewer.
6. Manual `Sync now` button.
7. Show useful sync failure states clearly.
8. Provide a small setup/diagnostics screen for Termux prerequisites and local paths.

No background sync required for V0.

---

## Termux-side helper: `travelzctl`

Implement a small shell script in the Travelz repo, for example:

```text
tools/termux/travelzctl
```

The installer/setup can later copy or symlink it into:

```text
~/.local/bin/travelzctl
```

or another stable Termux path.

### Required commands

#### `travelzctl status --json`

Returns machine-readable state without modifying anything.

Suggested shape:

```json
{
  "ok": true,
  "travelz_repo": {
    "path": "/data/data/com.termux/files/home/git/travelz",
    "exists": true,
    "branch": "main",
    "dirty": false
  },
  "vault_repo": {
    "path": "/data/data/com.termux/files/home/git/travault13",
    "exists": true,
    "branch": "main",
    "dirty": false
  },
  "last_sync": "2026-10-04T18:10:00+09:00"
}
```

Do not expose secrets, tokens, SSH config contents or repository remotes unless explicitly needed.

#### `travelzctl sync --json`

Run conservative pulls:

```sh
git -C "$TRAVELZ_REPO" pull --ff-only
git -C "$VAULT_REPO" pull --ff-only
```

Do not auto-reset, auto-stash, auto-merge, force-pull, or delete local changes.

If a repo is dirty or pull cannot fast-forward, return a clear error and leave it untouched.

Suggested result:

```json
{
  "ok": true,
  "travelz": "updated",
  "vault": "unchanged",
  "time": "2026-10-04T18:10:00+09:00"
}
```

Possible states can include:

- `updated`
- `unchanged`
- `dirty`
- `offline`
- `auth_error`
- `git_error`

Keep V0 parsing simple and deterministic.

#### `travelzctl trip --json`

Return a small current-trip summary for native rendering.

Do not depend on decrypting browser-only encrypted payloads if that complicates V0. Prefer a local private/public-safe source specifically intended for the Android companion.

Possible source options, in order of preference:

1. a dedicated machine-readable local file generated into the vault clone, or
2. a dedicated Android-safe JSON file in Travelz if it contains no sensitive data.

Suggested output:

```json
{
  "ok": true,
  "title": "Czechia 2026",
  "route": "Sapporo -> Taipei -> Prague -> Pardubice -> Vienna -> Taipei -> Sapporo",
  "next": {
    "title": "CI67 Taipei -> Prague",
    "time": "2026-10-06T23:30:00+08:00",
    "note": "TPE Terminal 1"
  },
  "money": [
    {"currency": "CZK", "note": "Revolut"},
    {"currency": "EUR", "note": "Revolut"}
  ]
}
```

#### `travelzctl docs --json`

Return a curated document list.

Do not recursively dump the whole private vault into the app.

Prefer a tiny manifest in the private repo, for example:

```text
current/docs.json
```

Example:

```json
{
  "documents": [
    {"id": "eticket", "label": "E-ticket", "path": "current/eticket.pdf", "type": "pdf"},
    {"id": "insurance", "label": "Insurance", "path": "current/insurance.pdf", "type": "pdf"},
    {"id": "passport_stan", "label": "Passport Stan", "path": "current/passport-stan.jpg", "type": "image"},
    {"id": "passport_wife", "label": "Passport wife", "path": "current/passport-wife.jpg", "type": "image"},
    {"id": "moxy", "label": "Moxy Vienna", "path": "current/moxy-vienna.pdf", "type": "pdf"}
  ]
}
```

`travelzctl` must validate paths so a malicious/accidental manifest cannot escape the configured vault root.

Reject:

- absolute paths
- `..` path traversal
- symlink escape outside vault root if practical to check
- missing files

#### `travelzctl open <id> --json`

Resolve the document ID using the curated manifest, validate it, and open with Android through Termux.

Likely implementation:

```sh
termux-open "$resolved_path"
```

Return JSON saying whether the launch request succeeded.

Do not let the Android app submit arbitrary file paths or arbitrary shell fragments.

---

## Configuration

Keep paths configurable, but start with sensible defaults:

```text
~/git/travelz
~/git/travault13
```

Possible config file:

```text
~/.config/travelz/config
```

For V0, a tiny shell-compatible or JSON config is enough. Avoid dependency-heavy parsers.

Example values:

```text
TRAVELZ_REPO=$HOME/git/travelz
VAULT_REPO=$HOME/git/travault13
```

Never place credentials in the Travelz repo or Android app preferences.

---

## Android app structure

Create under:

```text
android/
```

A standalone Android project similar in spirit to Overtura's Android companion is preferred.

Suggested package:

```text
io.github.hanenashi.travelz
```

Keep dependencies minimal. Platform Android APIs are preferred.

Suggested classes:

```text
MainActivity.java
TermuxBridge.java
TermuxResultReceiver.java
TermuxReply.java
QueryState.java
TravelzApi.java
TravelzModels.java
```

Names are not sacred; clarity is more important.

### Termux bridge

Adapt the Overtura bridge pattern.

Allowed operations should be fixed strings mapped to fixed helper commands, for example:

```text
status
sync
trip
docs
open:<validated-id>
```

Avoid a generic `run arbitrary command` interface.

### Permissions / setup

Document and handle:

```text
com.termux.permission.RUN_COMMAND
```

Termux must also have:

```text
allow-external-apps=true
```

The app should detect/report when either prerequisite is missing and still provide useful setup instructions.

### Timeouts

Use finite timeouts like Overtura.

Suggested V0 behavior:

- status/trip/docs: short timeout
- sync: longer timeout, e.g. 30-60s
- open: short timeout

The UI must never hang waiting for Termux.

---

## Data / privacy boundary

### Public Travelz repo may contain

- UI code
- route summaries
- non-sensitive trip timing
- generic money notes
- tools / helper scripts
- Android source

### Private vault contains

- passport copies
- insurance files
- ticket PDFs
- booking documents
- account details
- anything personally sensitive
- `current/docs.json` if it reveals sensitive filenames or categories the user prefers private

### Android app must not store

- GitHub PATs
- SSH private keys
- banking credentials
- passport file copies in app-private duplicate storage unless a future feature explicitly requires it

Termux/local Git already provides offline persistence; do not duplicate sensitive files without reason.

---

## Repository layout target

Possible end state:

```text
travelz/
├── index.html
├── assets/
├── trips/
├── tools/
│   └── termux/
│       ├── travelzctl
│       └── README.md
├── android/
│   ├── README.md
│   ├── settings.gradle
│   ├── build.gradle
│   └── app/
├── battleplan.md
└── readme.md
```

Private repo:

```text
travault13/
└── current/
    ├── docs.json
    ├── eticket.pdf
    ├── insurance.pdf
    ├── passport-stan.jpg
    ├── passport-wife.jpg
    └── moxy-vienna.pdf
```

Do not create fake sensitive files in the public repo.

---

## Implementation phases

### Phase 1 - inspect and copy proven Termux transport pattern

1. Read `hanenashi/overtura/android/README.md`.
2. Read its `TermuxBridge.java`, receiver/reply/state classes.
3. Document which pieces are reused conceptually.
4. Do not copy Overtura-specific SSH/session commands.

### Phase 2 - build/test `travelzctl`

Implement:

```text
status --json
sync --json
trip --json
docs --json
open <id> --json
```

Add shell tests where reasonable.

Test cases must include:

- missing Travelz repo
- missing vault repo
- clean/up-to-date repos
- dirty repo refusal
- offline pull failure
- malformed docs manifest
- path traversal attempt
- missing document
- valid document open command generation

Do not let tests open real user documents.

### Phase 3 - Android skeleton

Build a minimal app that can:

- launch
- query `status`
- show Termux permission/config errors
- render a small status card

Use Overtura-style callback handling.

### Phase 4 - trip/docs UI

Add:

- current trip header
- next item
- docs list
- money notes
- last sync

Load via helper JSON, not directly from GitHub.

### Phase 5 - document opening

Tap document -> `open <id>` -> Termux -> Android viewer.

Verify on Pixel with at least:

- PDF
- JPEG/PNG

No test should expose sensitive file contents in logs.

### Phase 6 - sync UI

Add manual sync button and states:

```text
Syncing...
Updated
Already current
Offline
Local changes - not touched
Authentication failed
```

After successful sync, refresh trip/docs/status.

### Phase 7 - polish

Only after V0 works:

- better icons
- richer schedule
- archive trip selection
- background sync
- notifications
- widgets
- deeper Pages integration

Do not start these early.

---

## Build / validation expectations

Prefer the same general Android toolchain style as Overtura unless there is a strong reason not to.

At minimum, before claiming V0 works:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Also run shell tests for `travelzctl`.

On Pixel, manually verify:

1. Termux installed.
2. `allow-external-apps=true` enabled.
3. RUN_COMMAND permission granted.
4. `travelzctl status --json` works in Termux.
5. App status query works.
6. Sync works online.
7. Disconnect network.
8. Trip summary still works from local clone.
9. Private document list still works.
10. Opening PDF/image still works offline.
11. Reconnect and sync recovers normally.

Do not clear or reset the user's Termux environment during testing.

---

## Important non-goals for V0

Do NOT:

- put private docs into the public Travelz repo
- add GitHub PAT handling to the Android app
- implement Git inside Android
- read Termux SSH keys
- expose a generic arbitrary-shell-command bridge
- auto-resolve Git conflicts
- auto-reset dirty repos
- copy sensitive files to shared storage just for convenience
- build an embedded PDF viewer unless native Android viewing proves insufficient
- create background services before manual sync is proven solid
- turn Travelz into a general trip-planning platform

---

## Security notes

Treat the native app as a UI for a constrained local API.

The helper is the trust boundary.

For every Android-triggered operation:

- use fixed commands
- validate arguments
- keep output size bounded
- JSON-encode replies
- reject unexpected/malformed reply shapes
- never return secret material unnecessarily
- never pass user-controlled values through `sh -c` without strict validation

For docs, IDs are safer than paths.

Good:

```text
open eticket
```

Bad:

```text
open ../../whatever
```

Very bad:

```text
run "$(curl evil...)"
```

---

## Current trip context for UI placeholders

Current trip is Czechia 2026.

Useful non-sensitive items already represented in Travelz include:

- Sapporo -> Taipei -> Prague -> Pardubice -> Vienna -> Taipei -> Sapporo
- China Airlines route
- Taipei lounge attempt
- Prague arrival and onward rail
- Moxy Vienna Airport stay
- Revolut / CZK / EUR / JPY / TWD prep notes
- Czech connectivity / eSIM TODO

Use these as development placeholders only where already public-safe.

Do not copy booking numbers, passport data, Revolut account details or other private values into source/tests/screenshots.

---

## CLI coding brief

Start by reading this file, then inspect the existing Travelz repo and Overtura Android reference.

Build the smallest vertical slice first:

```text
Travelz Android button
    -> Termux RUN_COMMAND
    -> travelzctl status --json
    -> callback
    -> validated JSON
    -> native status UI
```

Once that works, extend the same path to `trip`, `docs`, `open` and finally `sync`.

Prefer boring, explicit code over abstraction. This is travel infrastructure; reliability matters more than cleverness.

Keep commits small and leave the repo in a buildable/testable state after each phase.
