package io.github.hanenashi.travelz;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;

/** Offline document shelf backed by Termux's private Git clones. */
public final class MainActivity extends Activity {
    private static final String RUN_COMMAND = "com.termux.permission.RUN_COMMAND";
    private static final int PERMISSION_REQUEST = 7;
    private static final int BG = Color.rgb(11, 15, 20);
    private static final int PANEL = Color.rgb(21, 31, 44);
    private static final int INK = Color.rgb(238, 243, 248);
    private static final int MUTED = Color.rgb(147, 162, 180);
    private static final int ACCENT = Color.rgb(140, 215, 255);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private JSONObject statusData;
    private JSONObject tripData;
    private JSONArray documents;
    private String notice;
    private String activeOperation;
    private long activeToken;
    private boolean busy;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        render();
        if (ready()) refreshAll(false);
    }

    @Override protected void onDestroy() {
        TermuxBridge.forgetAll(this);
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private boolean termuxInstalled() {
        return getPackageManager().getLaunchIntentForPackage("com.termux") != null;
    }

    private boolean ready() {
        return termuxInstalled() && checkSelfPermission(RUN_COMMAND) == PackageManager.PERMISSION_GRANTED;
    }

    private void refreshAll(boolean keepNotice) {
        if (!ready() || busy) return;
        if (!keepNotice) notice = null;
        queue.clear();
        queue.add("status");
        queue.add("trip");
        queue.add("docs");
        runNext();
    }

    private void runNext() {
        if (queue.isEmpty()) {
            busy = false;
            activeOperation = null;
            render();
            return;
        }
        start(queue.removeFirst(), null);
    }

    private void start(String operation, String documentId) {
        if (!ready()) {
            notice = "Grant Termux command permission to use the offline vault.";
            render();
            return;
        }
        busy = true;
        activeOperation = operation;
        long token = ++activeToken;
        render();
        if (operation.equals("open")) {
            // Android blocks a background Termux process from launching a PDF
            // viewer. Bring Termux forward first, then ask it to open the file.
            Intent launch = getPackageManager().getLaunchIntentForPackage("com.termux");
            if (launch != null) startActivity(launch);
            handler.postDelayed(() -> {
                if (busy && activeToken == token) dispatch(operation, documentId, token);
            }, 900);
        } else {
            dispatch(operation, documentId, token);
        }
        long deadline = operation.equals("sync") ? 75_000 : 20_000;
        handler.postDelayed(() -> {
            if (busy && activeToken == token) {
                TermuxBridge.forget(this, token);
                busy = false;
                queue.clear();
                notice = operation.equals("sync") ? "Sync timed out. Local files are still available."
                    : "Termux did not reply. Check its permission and helper installation.";
                render();
            }
        }, deadline);
    }

    private void dispatch(String operation, String documentId, long token) {
        try {
            TermuxBridge.start(this, operation, documentId, token);
        } catch (RuntimeException error) {
            busy = false;
            queue.clear();
            notice = "Termux could not start the helper. Check the setup below.";
            render();
        }
    }

    void onTermuxResult(String operation, long token, Intent intent) {
        if (!busy || token != activeToken || !operation.equals(activeOperation)) return;
        try {
            TermuxReply reply = TermuxReply.from(intent);
            JSONObject data = reply.data;
            if (reply.exitCode != 0 || !data.getBoolean("ok")) {
                notice = data.optString("message", "The helper could not finish this request.");
                if (operation.equals("docs")) documents = null;
            } else {
                validatePayload(operation, data);
                switch (operation) {
                    case "status": statusData = data; break;
                    case "trip": tripData = data; break;
                    case "docs": documents = data.getJSONArray("documents"); break;
                    case "open": notice = "Opening document in your PDF or image viewer."; break;
                    case "sync": notice = "Sync complete. Refreshing local files."; break;
                    default: throw new JSONException("Unexpected operation");
                }
            }
        } catch (JSONException | RuntimeException error) {
            notice = "Termux returned an invalid result. Check the helper installation.";
            if (operation.equals("docs")) documents = null;
        }
        busy = false;
        if (operation.equals("sync")) {
            refreshAll(true);
        } else if (operation.equals("open")) {
            render();
        } else {
            runNext();
        }
    }

    private void validatePayload(String operation, JSONObject data) throws JSONException {
        switch (operation) {
            case "status":
                data.getJSONObject("travelz_repo");
                data.getJSONObject("vault_repo");
                break;
            case "trip":
                for (String key : new String[]{"id", "title", "route", "start", "end"}) {
                    if (data.getString(key).length() > 500) throw new JSONException("Trip field too long");
                }
                break;
            case "docs":
                JSONArray rows = data.getJSONArray("documents");
                if (rows.length() > 100) throw new JSONException("Too many documents");
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    if (!row.getString("id").matches("[a-z][a-z0-9-]{0,63}")
                            || row.getString("label").length() > 100
                            || !row.has("available") || !(row.get("available") instanceof Boolean))
                        throw new JSONException("Invalid document entry");
                    String type = row.getString("type");
                    if (!type.equals("pdf") && !type.equals("image"))
                        throw new JSONException("Invalid document type");
                }
                break;
            case "sync":
                data.getJSONObject("repositories");
                data.getString("time");
                break;
            case "open":
                if (!data.getBoolean("launched")) throw new JSONException("Viewer did not launch");
                break;
            default:
                throw new JSONException("Unexpected operation");
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode != PERMISSION_REQUEST) return;
        if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) refreshAll(false);
        else {
            notice = "Run commands in Termux permission is required for the offline document shelf.";
            render();
        }
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void add(LinearLayout parent, View child, int marginTop) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(marginTop);
        parent.addView(child, params);
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), Color.rgb(43, 55, 69));
        return drawable;
    }

    private LinearLayout card(LinearLayout parent, int top) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(background(PANEL, 20));
        add(parent, card, top);
        return card;
    }

    private Button button(String label, Runnable action) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(BG);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(background(ACCENT, 14));
        button.setMinHeight(dp(52));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(24), dp(20), dp(36));
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            view.setPadding(dp(20), bars.top + dp(24), dp(20), bars.bottom + dp(36));
            return insets;
        });
        scroll.addView(content);
        setContentView(scroll);

        TextView brand = text("TRAVELZ", 16, ACCENT, true);
        brand.setLetterSpacing(0.18f);
        add(content, brand, 0);
        add(content, text("Offline trip stash", 30, INK, true), 18);
        add(content, text("Private documents stay in Termux on this phone.", 14, MUTED, false), 8);

        LinearLayout state = card(content, 24);
        add(state, text("LOCAL STATUS", 12, ACCENT, true), 0);
        String lastSync = statusData == null ? null : statusData.optString("last_sync", null);
        add(state, text(lastSync == null || lastSync.isEmpty() ? "Never synced on this phone"
            : "Last sync: " + lastSync, 14, INK, false), 10);
        if (statusData != null) {
            JSONObject publicRepo = statusData.optJSONObject("travelz_repo");
            JSONObject vaultRepo = statusData.optJSONObject("vault_repo");
            add(state, text("Trip data: " + repoLabel(publicRepo)
                + "  ·  Private vault: " + repoLabel(vaultRepo), 13, MUTED, false), 8);
        }
        if (busy) add(state, text("Working…", 14, ACCENT, true), 10);
        if (notice != null) add(state, text(notice, 14, INK, false), 10);

        if (!termuxInstalled()) {
            add(state, text("Install and open Termux before using the offline stash.", 14, MUTED, false), 10);
        } else if (!ready()) {
            add(state, text("Grant 'Run commands in Termux', then return here.", 14, MUTED, false), 10);
            add(state, button("Grant permission", () ->
                requestPermissions(new String[]{RUN_COMMAND}, PERMISSION_REQUEST)), 14);
        } else {
            Button sync = button("Sync now", () -> start("sync", null));
            sync.setEnabled(!busy);
            add(state, sync, 16);
        }

        LinearLayout tripCard = card(content, 18);
        add(tripCard, text("CURRENT TRIP", 12, ACCENT, true), 0);
        add(tripCard, text(tripData == null ? "Trip not loaded" : tripData.optString("title", "Trip"),
            23, INK, true), 10);
        if (tripData != null) {
            add(tripCard, text(tripData.optString("route", ""), 14, MUTED, false), 8);
            add(tripCard, text(tripData.optString("start", "") + " → "
                + tripData.optString("end", ""), 13, ACCENT, false), 10);
        }

        add(content, text("DOCUMENTS", 13, ACCENT, true), 28);
        if (documents == null) {
            add(content, text("Document list unavailable. Check Termux setup or tap Sync now.",
                14, MUTED, false), 12);
        } else if (documents.length() == 0) {
            add(content, text("No documents have been added for this trip.", 14, MUTED, false), 12);
        } else {
            for (int i = 0; i < documents.length(); i++) {
                JSONObject row = documents.optJSONObject(i);
                if (row == null) continue;
                String id = row.optString("id", "");
                String label = row.optString("label", "Document");
                boolean available = row.optBoolean("available", false);
                LinearLayout doc = card(content, 10);
                add(doc, text(label, 17, INK, true), 0);
                add(doc, text(available ? "Saved on this phone" : "File missing from local vault",
                    13, available ? MUTED : Color.rgb(255, 155, 155), false), 6);
                Button open = button("Open document ↗", () -> start("open", id));
                open.setEnabled(available && !busy);
                add(doc, open, 14);
            }
        }

        LinearLayout setup = card(content, 24);
        add(setup, text("SETUP & DIAGNOSTICS", 12, ACCENT, true), 0);
        add(setup, text("Termux: " + (termuxInstalled() ? "installed" : "missing")
            + "  ·  Command permission: " + (ready() ? "granted" : "needed"), 13, MUTED, false), 10);
        if (statusData != null) {
            JSONObject publicRepo = statusData.optJSONObject("travelz_repo");
            JSONObject vaultRepo = statusData.optJSONObject("vault_repo");
            if (publicRepo != null) add(setup, text("Trip files: " + publicRepo.optString("path", ""),
                12, MUTED, false), 10);
            if (vaultRepo != null) add(setup, text("Private vault: " + vaultRepo.optString("path", ""),
                12, MUTED, false), 8);
        }
        add(setup, text("The helper runs from ~/.local/bin/travelzctl. Sync updates the two clean Git clones only.",
            12, MUTED, false), 10);
        if (termuxInstalled()) {
            Button termux = button("Open Termux", () -> {
                Intent launch = getPackageManager().getLaunchIntentForPackage("com.termux");
                if (launch != null) startActivity(launch);
            });
            add(setup, termux, 16);
        }
    }

    private String repoLabel(JSONObject repo) {
        if (repo == null || !repo.optBoolean("exists", false)) return "missing";
        return repo.optBoolean("dirty", false) ? "local changes" : "ready";
    }
}
