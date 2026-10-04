package io.github.hanenashi.travelz;

import android.content.Intent;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/** Validates the bounded Termux callback before the UI uses its JSON. */
final class TermuxReply {
    private static final int MAX_BYTES = 64 * 1024;
    final int exitCode;
    final JSONObject data;

    private TermuxReply(int exitCode, JSONObject data) {
        this.exitCode = exitCode;
        this.data = data;
    }

    static TermuxReply from(Intent intent) throws JSONException {
        Bundle result = intent == null ? null : intent.getBundleExtra("result");
        if (result == null || !result.containsKey("err") || !result.containsKey("exitCode"))
            throw new JSONException("Termux did not return a complete result");
        if (result.getInt("err") != -1)
            throw new JSONException("Termux rejected the command; check its permission and settings");
        String stdout = result.getString("stdout");
        Object lengthValue = result.get("stdout_original_length");
        if (stdout == null || lengthValue == null)
            throw new JSONException("Termux returned no result");
        long originalLength;
        try { originalLength = Long.parseLong(String.valueOf(lengthValue)); }
        catch (NumberFormatException error) { throw new JSONException("Invalid Termux result length"); }
        int bytes = stdout.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_BYTES || originalLength < 0 || originalLength > bytes)
            throw new JSONException("Termux result was truncated or too large");
        JSONObject data = new JSONObject(stdout.trim());
        if (!data.has("ok") || !(data.opt("ok") instanceof Boolean))
            throw new JSONException("Unexpected helper response");
        return new TermuxReply(result.getInt("exitCode"), data);
    }
}
