package io.github.hanenashi.travelz;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** A fixed-command bridge to the installed Termux helper. */
final class TermuxBridge {
    private static final AtomicInteger REQUEST_CODE = new AtomicInteger();
    private static final Map<String, Delivery> ACTIVE = new HashMap<>();

    private static final class Delivery {
        final WeakReference<MainActivity> owner;
        final String operation;
        final long token;

        Delivery(MainActivity activity, String operation, long token) {
            owner = new WeakReference<>(activity);
            this.operation = operation;
            this.token = token;
        }
    }

    private TermuxBridge() {}

    static void start(MainActivity activity, String operation, String documentId, long token) {
        String[] arguments;
        switch (operation) {
            case "status":
            case "sync":
            case "trip":
            case "docs":
                arguments = new String[]{operation, "--json"};
                break;
            case "open":
                if (documentId == null || !documentId.matches("[a-z][a-z0-9-]{0,63}"))
                    throw new IllegalArgumentException("Invalid document ID");
                arguments = new String[]{"open", documentId, "--json"};
                break;
            default:
                throw new IllegalArgumentException("Unknown operation");
        }

        String id = UUID.randomUUID().toString();
        Intent callback = new Intent(activity, TermuxResultReceiver.class)
            .setData(Uri.parse("travelz://termux/" + id));
        PendingIntent result = PendingIntent.getBroadcast(activity, REQUEST_CODE.incrementAndGet(),
            callback, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_MUTABLE);
        Intent command = new Intent("com.termux.RUN_COMMAND")
            .setClassName("com.termux", "com.termux.app.RunCommandService")
            .putExtra("com.termux.RUN_COMMAND_PATH", "~/.local/bin/travelzctl")
            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arguments)
            .putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", result);
        synchronized (ACTIVE) { ACTIVE.put(id, new Delivery(activity, operation, token)); }
        try {
            if (activity.startService(command) == null) throw new IllegalStateException("Termux unavailable");
        } catch (RuntimeException error) {
            synchronized (ACTIVE) { ACTIVE.remove(id); }
            result.cancel();
            throw error;
        }
    }

    static void deliver(Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        if (data == null || !"travelz".equals(data.getScheme()) || !"termux".equals(data.getHost())) return;
        Delivery delivery;
        synchronized (ACTIVE) { delivery = ACTIVE.remove(data.getLastPathSegment()); }
        if (delivery == null) return;
        MainActivity activity = delivery.owner.get();
        if (activity != null) activity.onTermuxResult(delivery.operation, delivery.token, intent);
    }

    static void forget(MainActivity activity, long token) {
        synchronized (ACTIVE) {
            ACTIVE.entrySet().removeIf(row -> row.getValue().owner.get() == activity
                && row.getValue().token == token);
        }
    }

    static void forgetAll(MainActivity activity) {
        synchronized (ACTIVE) {
            ACTIVE.entrySet().removeIf(row -> row.getValue().owner.get() == activity);
        }
    }
}
