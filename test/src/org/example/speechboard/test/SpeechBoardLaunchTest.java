package org.example.speechboard.test;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

public final class SpeechBoardLaunchTest extends Instrumentation {
    private static final String IME =
            "org.example.speechboard/.SpeechInputMethodService";
    private static final long TIMEOUT_MS = 5000;

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            verifyInputViewLaunches();
            result.putString("stream", "PASS: SpeechBoard input view launched.\n");
            finish(Activity.RESULT_OK, result);
        } catch (AssertionError error) {
            result.putString("stream", "FAIL: " + error.getMessage() + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void verifyInputViewLaunches() {
        Context context = getTargetContext();
        InputMethodManager manager = context.getSystemService(InputMethodManager.class);
        boolean enabled = false;
        for (InputMethodInfo method : manager.getEnabledInputMethodList()) {
            if (IME.equals(method.getId())) {
                enabled = true;
                break;
            }
        }
        require(enabled, "SpeechBoard is installed but not enabled as an input method.");

        String current = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        require(IME.equals(current),
                "SpeechBoard is enabled but not selected; current keyboard is " + current + ".");

        long startedAt = System.currentTimeMillis();
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(
                "org.example.speechboard",
                "org.example.speechboard.MainActivity"));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
        waitForIdleSync();

        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            long inputViewStart = context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
                    .getLong("last_input_view_start", 0);
            if (inputViewStart >= startedAt) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Test interrupted while waiting for SpeechBoard.");
            }
        }
        throw new AssertionError(
                "SpeechBoard is selected, but onStartInputView() never ran within "
                        + TIMEOUT_MS + " ms.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
