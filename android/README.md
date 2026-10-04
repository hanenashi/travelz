# Travelz for Android

A small native offline shelf for the current trip and its private documents.
The app uses no GitHub token, SSH key, or private document copy. Termux owns the
two local Git clones and opens documents with Android's installed viewers.

## Build

JDK 17 or newer and Android SDK platform 36 are required. From `android/`:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk` and is ignored by
Git. Install it on an authorized Pixel with `adb install -r`.

## Phone setup

1. Install both Termux clones and `travelzctl` as described in
   [the helper README](../tools/termux/README.md).
2. Set `allow-external-apps=true` in Termux and reload its settings.
3. Open Travelz and grant **Run commands in Termux**.
4. Tap **Sync now** when online. The trip and document list remain available
   from the local clones without a network connection.

Tap a document to bring Termux forward and open its local PDF or image with an
Android viewer. Press Back from the viewer to return through Termux. The app
has a setup and diagnostics card with clone locations and readiness state.

The V0 app intentionally has no background sync, embedded PDF viewer, or
trip-planning features. It shows only the current trip listed first in
`trips/index.json`.
