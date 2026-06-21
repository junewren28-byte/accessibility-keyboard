package org.example.speechboard;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.provider.UserDictionary;
import android.util.Log;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final String TAG = "SpeechBoard";
    private static final int MICROPHONE_REQUEST = 100;
    private static final int CONTACTS_REQUEST = 101;
    private static final String SPEECHBOARD_IME =
            "org.example.speechboard/.SpeechInputMethodService";
    private static final String SPEECH_PREFS = "speech_settings";
    private static final String BIAS_PHRASES_KEY = "bias_phrases";
    private TextView activeKeyboard;
    private TextView launchTestResult;
    private EditText testField;
    private EditText biasPhrases;
    private boolean activationPickerShown;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Log.i(TAG, "Setup activity created");
        setTitle(R.string.setup_title);

        int padding = dp(24);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padding, padding, padding, padding);
        layout.setBackgroundColor(Color.WHITE);

        TextView title = text(getString(R.string.setup_title), 30, true);
        layout.addView(title);

        TextView description = text(getString(R.string.setup_description), 21, false);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descriptionParams.setMargins(0, dp(20), 0, dp(20));
        layout.addView(description, descriptionParams);

        activeKeyboard = text("", 22, true);
        layout.addView(activeKeyboard);

        Button microphone = button(R.string.grant_microphone);
        microphone.setOnClickListener(v -> requestMicrophone());
        layout.addView(microphone);

        Button settings = button(R.string.open_keyboard_settings);
        settings.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        layout.addView(settings);

        Button picker = button(R.string.show_keyboard_picker);
        picker.setOnClickListener(v -> {
            InputMethodManager manager = getSystemService(InputMethodManager.class);
            manager.showInputMethodPicker();
        });
        layout.addView(picker);

        Button launchTest = button(R.string.run_launch_test);
        launchTest.setOnClickListener(v -> runLaunchTest());
        layout.addView(launchTest);

        launchTestResult = text(getString(R.string.launch_test_not_run), 20, true);
        layout.addView(launchTestResult);

        TextView biasLabel = text(getString(R.string.bias_phrases_label), 20, true);
        LinearLayout.LayoutParams biasLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        biasLabelParams.setMargins(0, dp(16), 0, dp(4));
        layout.addView(biasLabel, biasLabelParams);

        biasPhrases = new EditText(this);
        biasPhrases.setHint(R.string.bias_phrases_hint);
        biasPhrases.setTextSize(18);
        biasPhrases.setSingleLine(false);
        biasPhrases.setMinLines(3);
        biasPhrases.setText(getSharedPreferences(SPEECH_PREFS, MODE_PRIVATE)
                .getString(BIAS_PHRASES_KEY, ""));
        LinearLayout.LayoutParams biasParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(120));
        layout.addView(biasPhrases, biasParams);

        Button saveBias = button(R.string.save_bias_phrases);
        saveBias.setOnClickListener(v -> saveBiasPhrases());
        layout.addView(saveBias);

        Button importBias = button(R.string.import_bias_sources);
        importBias.setOnClickListener(v -> importProfilePhrases());
        layout.addView(importBias);

        testField = new EditText(this);
        testField.setHint(R.string.test_hint);
        testField.setTextSize(22);
        testField.setMinHeight(dp(72));
        testField.setSingleLine(false);
        LinearLayout.LayoutParams testParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(120));
        testParams.setMargins(0, dp(16), 0, dp(8));
        layout.addView(testField, testParams);

        TextView instructions = text(getString(R.string.instructions), 20, false);
        LinearLayout.LayoutParams instructionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        instructionsParams.setMargins(0, dp(24), 0, 0);
        layout.addView(instructions, instructionsParams);

        setContentView(layout);
        requestMicrophone();
    }

    @Override
    protected void onResume() {
        super.onResume();
        String current = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        boolean active = SPEECHBOARD_IME.equals(current);
        boolean enabled = isSpeechBoardEnabled();
        Log.i(TAG, "Keyboard status: enabled=" + enabled
                + ", active=" + active + ", current=" + current);
        if (active) {
            activeKeyboard.setText(R.string.active_speechboard);
        } else if (enabled) {
            activeKeyboard.setText(R.string.enabled_not_active);
        } else {
            activeKeyboard.setText(R.string.not_enabled);
        }
        activeKeyboard.setTextColor(active
                ? Color.rgb(0, 100, 0)
                : Color.rgb(180, 0, 0));
        if (enabled && !active && !activationPickerShown) {
            activationPickerShown = true;
            activeKeyboard.postDelayed(() -> {
                InputMethodManager manager = getSystemService(InputMethodManager.class);
                manager.showInputMethodPicker();
            }, 300);
        }
        if (active) {
            testField.requestFocus();
            testField.postDelayed(() -> {
                InputMethodManager manager = getSystemService(InputMethodManager.class);
                manager.showSoftInput(testField, InputMethodManager.SHOW_IMPLICIT);
            }, 300);
        }
    }

    private void runLaunchTest() {
        if (!isSpeechBoardEnabled()) {
            showLaunchTestFailure(getString(R.string.launch_test_not_enabled));
            return;
        }

        String current = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        if (!SPEECHBOARD_IME.equals(current)) {
            showLaunchTestFailure(getString(R.string.launch_test_not_selected, current));
            return;
        }

        long startedAt = System.currentTimeMillis();
        launchTestResult.setText(R.string.launch_test_running);
        launchTestResult.setTextColor(Color.rgb(160, 100, 0));
        testField.requestFocus();
        InputMethodManager manager = getSystemService(InputMethodManager.class);
        manager.showSoftInput(testField, InputMethodManager.SHOW_IMPLICIT);
        waitForInputView(startedAt, System.currentTimeMillis() + 5000);
    }

    private void waitForInputView(long startedAt, long deadline) {
        long inputViewStart = getSharedPreferences("diagnostics", MODE_PRIVATE)
                .getLong("last_input_view_start", 0);
        if (inputViewStart >= startedAt) {
            launchTestResult.setText(R.string.launch_test_passed);
            launchTestResult.setTextColor(Color.rgb(0, 100, 0));
        } else if (System.currentTimeMillis() >= deadline) {
            showLaunchTestFailure(getString(R.string.launch_test_never_launched));
        } else {
            launchTestResult.postDelayed(() -> waitForInputView(startedAt, deadline), 100);
        }
    }

    private void showLaunchTestFailure(String message) {
        launchTestResult.setText(getString(R.string.launch_test_failed, message));
        launchTestResult.setTextColor(Color.rgb(180, 0, 0));
    }

    private void saveBiasPhrases() {
        getSharedPreferences(SPEECH_PREFS, MODE_PRIVATE)
                .edit()
                .putString(BIAS_PHRASES_KEY, biasPhrases.getText().toString())
                .apply();
        activeKeyboard.setText(R.string.bias_phrases_saved);
        activeKeyboard.setTextColor(Color.rgb(0, 100, 0));
    }

    private void importProfilePhrases() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, CONTACTS_REQUEST);
            return;
        }
        importProfilePhrasesWithPermission();
    }

    private void importProfilePhrasesWithPermission() {
        List<String> dictionaryWords = loadDictionaryWords();
        List<String> contactNames = loadContactNames();
        SpeechBiasPhrases.MergeResult result = SpeechBiasPhrases.merge(
                biasPhrases.getText().toString(), dictionaryWords, contactNames);
        biasPhrases.setText(result.text);
        getSharedPreferences(SPEECH_PREFS, MODE_PRIVATE)
                .edit()
                .putString(BIAS_PHRASES_KEY, result.text)
                .apply();
        activeKeyboard.setText(getString(R.string.bias_sources_imported,
                result.dictionaryAdded, result.contactsAdded));
        activeKeyboard.setTextColor(Color.rgb(0, 100, 0));
    }

    private List<String> loadDictionaryWords() {
        List<String> words = new ArrayList<>();
        try (Cursor cursor = getContentResolver().query(
                UserDictionary.Words.CONTENT_URI,
                new String[]{UserDictionary.Words.WORD},
                null,
                null,
                null)) {
            if (cursor == null) {
                return words;
            }
            int wordColumn = cursor.getColumnIndex(UserDictionary.Words.WORD);
            while (cursor.moveToNext()) {
                if (wordColumn >= 0) {
                    words.add(cursor.getString(wordColumn));
                }
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not import user dictionary words", error);
        }
        return words;
    }

    private List<String> loadContactNames() {
        List<String> names = new ArrayList<>();
        try (Cursor cursor = getContentResolver().query(
                ContactsContract.Contacts.CONTENT_URI,
                new String[]{ContactsContract.Contacts.DISPLAY_NAME_PRIMARY},
                null,
                null,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC")) {
            if (cursor == null) {
                return names;
            }
            int nameColumn = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY);
            while (cursor.moveToNext()) {
                if (nameColumn >= 0) {
                    names.add(cursor.getString(nameColumn));
                }
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not import contact names", error);
        }
        return names;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CONTACTS_REQUEST) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                importProfilePhrasesWithPermission();
            } else {
                activeKeyboard.setText(R.string.contacts_permission_denied);
                activeKeyboard.setTextColor(Color.rgb(180, 0, 0));
            }
        }
    }

    private boolean isSpeechBoardEnabled() {
        InputMethodManager manager = getSystemService(InputMethodManager.class);
        for (InputMethodInfo method : manager.getEnabledInputMethodList()) {
            if (SPEECHBOARD_IME.equals(method.getId())) {
                return true;
            }
        }
        return false;
    }

    private void requestMicrophone() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    MICROPHONE_REQUEST);
        }
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.BLACK);
        view.setTextSize(size);
        if (bold) {
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return view;
    }

    private Button button(int text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(20);
        button.setMinHeight(dp(64));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, dp(8));
        button.setLayoutParams(params);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
