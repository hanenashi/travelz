# TRAVELZ

Small static travel hub for GitHub Pages.

## Shape

- `index.html` — trip dashboard
- `trips/<year>-<destination>/` — one frontend and data file per trip
- `assets/` — shared UI and PIN gate
- `sw.js` — offline fallback/cache
- `tools/termux/` — local helper for the Pixel's private Git clones
- `android/` — native offline document shelf
- `travault13` — separate private repository for sensitive documents

## Privacy model

The public repo contains the trip pages and readable JSON data. The PIN gate is a visual curtain for the GitHub Pages interface; anyone can inspect the source files or request the JSON directly. Put only information acceptable to publish in this repo. Scans, PDFs, booking documents, account details, and similar files belong in the private `travault13` repository.

The gate uses PBKDF2-SHA-256 to check the PIN. Unlock state is remembered locally for up to 24 hours and can be cleared with the lock button. It does not encrypt or access-control the public data.

## Trip folders

Use the same trip ID in both repositories, such as `trips/2026-czechia/`. Travelz keeps `index.html` and `trip.json` in that folder. The vault keeps a `README.md` and its PDFs or scans in the matching folder. The public trip JSON may link to private GitHub files; GitHub sign-in controls access to those files.

## GitHub Pages

In repository **Settings → Pages**, choose **Deploy from a branch**, then select `main` and `/ (root)`.

Expected project URL:

`https://hanenashi.github.io/travelz/`

## PIN setup

A temporary PIN drop file exists in the private `travault13` repository under `setup/PIN_DROP.txt`. Put a temporary Travelz-only PIN there and commit it. Do not reuse a banking/device/password-manager PIN. After the gate is configured, the current file can be cleared; Git history should still be treated as private storage, not a secret manager.
