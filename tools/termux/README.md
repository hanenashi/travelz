# Travelz local helper

`travelzctl` is the fixed local API used by the Android app. It needs Python 3,
Git, Termux and `termux-open`. It reads the public trip JSON and the curated
`docs.json` in the matching private vault folder. It never returns PDF contents
or credentials to the app.

## Install on Teneichan

Keep both clones in Termux private storage so Git and the vault work offline:

```sh
mkdir -p ~/git ~/.local/bin
git clone git@github.com:hanenashi/travelz.git ~/git/travelz
git clone git@github.com:hanenashi/travault13.git ~/git/travault13
ln -s ~/git/travelz/tools/termux/travelzctl ~/.local/bin/travelzctl
chmod 755 ~/git/travelz/tools/termux/travelzctl
```

On the current Pixel, these clones already exist. The older shared-storage
Travelz clone at `/storage/emulated/0/GIT/travelz` has separate history and is
intentionally left alone. The helper prefers `~/git/travelz` and only falls
back to the shared-storage clone when the private-home clone is absent.

For a different layout, set `TRAVELZ_REPO` and `TRAVAULT_REPO` in the Termux
environment. The Android app uses Termux's default environment, so its normal
installation should use the `~/git/` layout.

## Commands

```sh
travelzctl status --json
travelzctl trip --json
travelzctl docs --json
travelzctl open flight-eticket --json
travelzctl sync --json
```

`sync` checks both repos before pulling. It refuses local changes and uses
fast-forward-only pulls. It updates the vault first, then Travelz; if the
second pull fails, the response reports that the first may already be updated.
No conflict is merged or reset automatically.

The manifest is `travault13/trips/<trip-id>/docs.json`. It contains only
curated IDs, labels, types and filenames within that trip folder. The helper
rejects path traversal and symlinks that escape the folder. `open` accepts a
document ID, never an arbitrary path.

## Android connection

Termux needs `allow-external-apps=true` in `~/.termux/termux.properties`.
Grant Travelz the Android **Run commands in Termux** permission. The app sends
fixed commands through `com.termux.RUN_COMMAND` and receives bounded JSON
callbacks. Opening a document briefly brings Termux to the foreground because
Android blocks its PDF viewer launch while Termux is in the background.

Run the local helper tests from the Travelz repository with:

```sh
python3 -m unittest discover -s tests -v
```
