package io.github.hanenashi.travelz;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Receives only the one-shot PendingIntent callbacks registered by this app. */
public final class TermuxResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        TermuxBridge.deliver(intent);
    }
}
