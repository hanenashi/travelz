# TRAVELZ

Small static travel hub for GitHub Pages.

## Shape

- `index.html` — trip dashboard
- `trips/current/` — current-trip frontend
- `assets/` — shared UI, PIN gate, encrypted trip loader
- `sw.js` — offline fallback/cache
- `travault13` — separate private repository for sensitive documents

## Privacy model

The public repo contains only the shell and public-safe metadata. Current-trip detail is intended to live in an encrypted `trip.enc.json` payload. The PIN gate is convenience/privacy, not a replacement for the private vault. Passport scans, insurance PDFs, booking documents, account details and similar material must stay in the private repository.

The gate uses PBKDF2-SHA-256. A separate derived key is used to decrypt AES-GCM trip data in the browser. Unlock state is remembered locally for up to 24 hours and can be cleared with the lock button.

## GitHub Pages

In repository **Settings → Pages**, choose **Deploy from a branch**, then select `main` and `/ (root)`.

Expected project URL:

`https://hanenashi.github.io/travelz/`

## PIN setup

A temporary PIN drop file exists in the private `travault13` repository under `setup/PIN_DROP.txt`. Put a temporary Travelz-only PIN there and commit it. Do not reuse a banking/device/password-manager PIN. After the gate is configured, the current file can be cleared; Git history should still be treated as private storage, not a secret manager.
