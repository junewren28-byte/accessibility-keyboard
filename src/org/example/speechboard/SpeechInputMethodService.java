package org.example.speechboard;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InlineSuggestion;
import android.view.inputmethod.InlineSuggestionsRequest;
import android.view.inputmethod.InlineSuggestionsResponse;
import android.inputmethodservice.InputMethodService;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.inline.InlineContentView;
import android.widget.inline.InlinePresentationSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class SpeechInputMethodService extends InputMethodService
        implements RecognitionListener {
    private static final String TAG = "SpeechBoard";
    private static final String CHATGPT_PACKAGE = "com.openai.chatgpt";
    private static final String SPEECH_PREFS = "speech_settings";
    private static final String BIAS_PHRASES_KEY = "bias_phrases";
    private static final int YELLOW = Color.rgb(255, 221, 0);
    private static final int RED = Color.rgb(180, 0, 0);
    private static final int KEY_COLOR = Color.rgb(38, 50, 56);

    private LinearLayout root;
    private LinearLayout speechPanel;
    private LinearLayout autofillStrip;
    private TextView speechButton;
    private LinearLayout typingPanel;
    private Button shiftButton;
    private SpeechRecognizer recognizer;
    private boolean listening;
    private boolean sensitiveField;
    private boolean shifted;
    private boolean retriedBasicRecognizerIntent;
    private final SpeechTranscriptBuffer transcriptBuffer = new SpeechTranscriptBuffer();
    private int typingLayout;
    private EditorInfo currentEditorInfo;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Input method service created");
    }

    @Override
    public View onCreateInputView() {
        Log.i(TAG, "Creating input view");
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18, 18, 18));
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(354)));

        autofillStrip = new LinearLayout(this);
        autofillStrip.setOrientation(LinearLayout.HORIZONTAL);
        autofillStrip.setPadding(dp(4), dp(4), dp(4), dp(4));
        autofillStrip.setBackgroundColor(Color.rgb(18, 18, 18));
        autofillStrip.setVisibility(View.GONE);
        root.addView(autofillStrip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        speechPanel = new LinearLayout(this);
        speechPanel.setOrientation(LinearLayout.HORIZONTAL);
        speechPanel.setPadding(dp(4), dp(4), dp(4), dp(4));

        speechButton = new TextView(this);
        speechButton.setGravity(Gravity.CENTER);
        speechButton.setTextColor(Color.BLACK);
        speechButton.setTextSize(30);
        speechButton.setTypeface(Typeface.DEFAULT_BOLD);
        speechButton.setPadding(dp(20), dp(20), dp(20), dp(20));
        speechButton.setFocusable(true);
        speechButton.setClickable(true);
        speechButton.setLongClickable(true);
        speechButton.setOnClickListener(v -> toggleListening());
        speechButton.setOnLongClickListener(v -> {
            stopListening();
            vibrate(80);
            showTypingMode();
            return true;
        });
        LinearLayout.LayoutParams speechParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        speechParams.setMargins(0, 0, dp(8), 0);
        speechPanel.addView(speechButton, speechParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        Button submitButton = actionButton("SUBMIT", Color.rgb(0, 100, 70), v -> submit());
        Button clearButton = actionButton("CLEAR", Color.rgb(160, 30, 30), v -> clearAllText());
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        actionParams.setMargins(0, 0, 0, dp(5));
        actions.addView(submitButton, actionParams);
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        clearParams.setMargins(0, dp(5), 0, 0);
        actions.addView(clearButton, clearParams);
        speechPanel.addView(actions, new LinearLayout.LayoutParams(
                dp(132), ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(speechPanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(300)));

        typingPanel = createTypingPanel();
        root.addView(typingPanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(300)));
        showSpeechMode();
        return root;
    }

    @Override
    public InlineSuggestionsRequest onCreateInlineSuggestionsRequest(Bundle uiExtras) {
        Size minSize = new Size(dp(120), dp(42));
        Size maxSize = new Size(dp(260), dp(50));
        InlinePresentationSpec spec =
                new InlinePresentationSpec.Builder(minSize, maxSize).build();
        return new InlineSuggestionsRequest.Builder(Collections.singletonList(spec))
                .setMaxSuggestionCount(3)
                .build();
    }

    @Override
    public boolean onInlineSuggestionsResponse(InlineSuggestionsResponse response) {
        if (autofillStrip == null) {
            return false;
        }
        autofillStrip.removeAllViews();
        List<InlineSuggestion> suggestions = response == null
                ? Collections.emptyList()
                : response.getInlineSuggestions();
        if (suggestions.isEmpty()) {
            autofillStrip.setVisibility(View.GONE);
            return false;
        }
        autofillStrip.setVisibility(View.VISIBLE);
        int count = Math.min(3, suggestions.size());
        for (int i = 0; i < count; i++) {
            InlineSuggestion suggestion = suggestions.get(i);
            suggestion.inflate(this, new Size(dp(220), dp(48)), getMainExecutor(),
                    view -> addInlineSuggestionView(view));
        }
        return true;
    }

    private void addInlineSuggestionView(InlineContentView view) {
        if (autofillStrip == null || view == null) {
            return;
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        autofillStrip.addView(view, params);
    }

    private LinearLayout createTypingPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(4), dp(4), dp(4), dp(4));
        panel.setBackgroundColor(Color.rgb(18, 18, 18));
        return panel;
    }

    private void renderTypingPanel() {
        if (typingPanel == null) {
            return;
        }
        typingPanel.removeAllViews();
        if (typingLayout == 0) {
            renderLetterLayout();
        } else if (typingLayout == 1) {
            renderNumberLayout();
        } else {
            renderSymbolLayout();
        }
    }

    private void renderLetterLayout() {
        addCharacterRow("qwertyuiop", true);
        addCharacterRow("asdfghjkl", true);
        LinearLayout third = row();
        shiftButton = key("SHIFT", 1.4f, v -> toggleShift());
        third.addView(shiftButton);
        addCharacterKeys(third, "zxcvbnm", true);
        third.addView(key("DEL", 1.4f, v -> deleteText()));
        typingPanel.addView(third, rowParams());
        addTypingBottomRow("123", v -> setTypingLayout(1), ".");
        refreshLetterKeys();
    }

    private void renderNumberLayout() {
        addCharacterRow("1234567890", false);
        addCharacterRow("@#$%&-+()", false);
        LinearLayout third = row();
        addCharacterKeys(third, "*/\"':;!?", false);
        third.addView(key("DEL", 1.4f, v -> deleteText()));
        typingPanel.addView(third, rowParams());
        addTypingBottomRow("#+=", v -> setTypingLayout(2), "_");
    }

    private void renderSymbolLayout() {
        addCharacterRow("[]{}<>^=+", false);
        addCharacterRow("_\\|~" + (char) 96, false);
        LinearLayout third = row();
        addCharacterKeys(third, ".,?!@#$%", false);
        third.addView(key("DEL", 1.4f, v -> deleteText()));
        typingPanel.addView(third, rowParams());
        addTypingBottomRow("123", v -> setTypingLayout(1), "\\");
    }

    private void addTypingBottomRow(
            String modeLabel, View.OnClickListener modeAction, String punctuation) {
        LinearLayout bottom = row();
        bottom.addView(key("SPEAK", 1.5f, v -> showSpeechMode()));
        bottom.addView(key("ABC", 1f, v -> setTypingLayout(0)));
        bottom.addView(key(modeLabel, 1f, modeAction));
        bottom.addView(key("SPACE", 3f, v -> commit(" ")));
        bottom.addView(key(punctuation, 0.8f, v -> commit(punctuation)));
        bottom.addView(key("SUBMIT", 1.5f, v -> submit()));
        typingPanel.addView(bottom, rowParams());
    }

    private void addCharacterRow(String characters, boolean letters) {
        LinearLayout characterRow = row();
        addCharacterKeys(characterRow, characters, letters);
        typingPanel.addView(characterRow, rowParams());
    }

    private void addCharacterKeys(
            LinearLayout characterRow, String characters, boolean letters) {
        for (int i = 0; i < characters.length(); i++) {
            String value = String.valueOf(characters.charAt(i));
            Button button = key(value, 1f, v -> {
                String committed = ((Button) v).getText().toString();
                commit(committed);
                if (shifted) {
                    shifted = false;
                    refreshLetterKeys();
                }
            });
            if (letters) {
                button.setTag("letter");
            }
            characterRow.addView(button);
        }
    }

    private void setTypingLayout(int layout) {
        typingLayout = layout;
        shifted = false;
        renderTypingPanel();
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        params.setMargins(0, dp(2), 0, dp(2));
        return params;
    }

    private View spacer(float weight) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, weight));
        return spacer;
    }

    private Button actionButton(
            String label, int color, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(18);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setIncludeFontPadding(false);
        button.setPadding(dp(4), dp(4), dp(4), dp(4));
        button.setBackground(background(color));
        button.setOnClickListener(listener);
        return button;
    }

    private Button key(String label, float weight, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(label.length() == 1 ? 24 : 14);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(0, 0, 0, 0);
        button.setBackground(background(KEY_COLOR));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, weight);
        params.setMargins(dp(2), 0, dp(2), 0);
        button.setLayoutParams(params);
        button.setOnClickListener(listener);
        return button;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        currentEditorInfo = attribute;
        sensitiveField = isSensitive(attribute);
        Log.i(TAG, "Starting input: package=" + attribute.packageName
                + ", inputType=" + attribute.inputType
                + ", sensitive=" + sensitiveField);
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        currentEditorInfo = info;
        sensitiveField = isSensitive(info);
        getSharedPreferences("diagnostics", MODE_PRIVATE)
                .edit()
                .putLong("last_input_view_start", System.currentTimeMillis())
                .apply();
        Log.i(TAG, "Showing input view: package=" + info.packageName
                + ", restarting=" + restarting
                + ", sensitive=" + sensitiveField);
        if (sensitiveField) {
            showTypingMode();
        } else {
            showSpeechMode();
        }
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        stopListening();
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onDestroy() {
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        super.onDestroy();
    }

    private void toggleListening() {
        if (listening) {
            stopListening();
        } else {
            startListening();
        }
    }

    private void startListening() {
        if (sensitiveField) {
            showTypingMode();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            showError("MICROPHONE ACCESS NEEDED\n\nOPEN SPEECHBOARD APP TO GRANT IT");
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showError("SPEECH RECOGNITION IS NOT AVAILABLE");
            return;
        }
        if (recognizer == null) {
            try {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this);
                recognizer.setRecognitionListener(this);
            } catch (RuntimeException error) {
                Log.e(TAG, "Could not create speech recognizer", error);
                recognizer = null;
                showError("COULD NOT START SPEECH RECOGNITION");
                return;
            }
        }

        transcriptBuffer.reset();
        retriedBasicRecognizerIntent = false;
        listening = true;
        vibrate(45);
        showListening("LISTENING...\n\nTAP TO STOP");
        try {
            recognizer.startListening(createRecognizerIntent(true));
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not start speech recognition", error);
            listening = false;
            recognizer.destroy();
            recognizer = null;
            showError("COULD NOT START SPEECH RECOGNITION");
        }
    }

    private Intent createRecognizerIntent(boolean advanced) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                2800);
        intent.putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                1800);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        if (!advanced) {
            return intent;
        }
        intent.putExtra(SpeechRecognitionExtras.EXTRA_MASK_OFFENSIVE_WORDS, false);
        intent.putExtra(SpeechRecognitionExtras.EXTRA_ENABLE_FORMATTING,
                SpeechRecognitionExtras.FORMATTING_OPTIMIZE_QUALITY);
        intent.putExtra(SpeechRecognitionExtras.EXTRA_ENABLE_BIASING_DEVICE_CONTEXT, true);
        ArrayList<String> biasPhrases = getBiasPhrases();
        if (!biasPhrases.isEmpty()) {
            intent.putStringArrayListExtra(
                    SpeechRecognitionExtras.EXTRA_BIASING_STRINGS, biasPhrases);
        }
        return intent;
    }

    private ArrayList<String> getBiasPhrases() {
        String raw = getSharedPreferences(SPEECH_PREFS, MODE_PRIVATE)
                .getString(BIAS_PHRASES_KEY, "");
        return SpeechRecognitionExtras.parseBiasPhrases(raw);
    }

    private void stopListening() {
        if (recognizer != null && listening) {
            try {
                recognizer.stopListening();
                if (speechButton != null && speechButton.getVisibility() == View.VISIBLE) {
                    speechButton.setText("PROCESSING...");
                }
            } catch (RuntimeException error) {
                Log.e(TAG, "Could not stop speech recognition", error);
                recognizer.destroy();
                recognizer = null;
                commitBufferedTranscript();
            }
        }
        listening = false;
    }

    private void showSpeechMode() {
        stopListening();
        if (typingPanel != null) {
            typingPanel.setVisibility(View.GONE);
        }
        if (speechPanel != null) {
            speechPanel.setVisibility(View.VISIBLE);
        }
        showReady();
    }

    private void showTypingMode() {
        stopListening();
        shifted = false;
        if (speechPanel != null) {
            speechPanel.setVisibility(View.GONE);
        }
        if (typingPanel != null) {
            typingPanel.setVisibility(View.VISIBLE);
            renderTypingPanel();
        }
    }

    private void toggleShift() {
        shifted = !shifted;
        refreshLetterKeys();
    }

    private void refreshLetterKeys() {
        if (typingPanel == null) {
            return;
        }
        for (int i = 0; i < typingPanel.getChildCount(); i++) {
            View rowView = typingPanel.getChildAt(i);
            if (!(rowView instanceof LinearLayout)) {
                continue;
            }
            LinearLayout row = (LinearLayout) rowView;
            for (int j = 0; j < row.getChildCount(); j++) {
                View key = row.getChildAt(j);
                if (key instanceof Button && "letter".equals(key.getTag())) {
                    Button button = (Button) key;
                    String value = button.getText().toString();
                    button.setText(shifted ? value.toUpperCase(Locale.getDefault())
                            : value.toLowerCase(Locale.getDefault()));
                }
            }
        }
        if (shiftButton != null) {
            shiftButton.setText(shifted ? "SHIFT ON" : "SHIFT");
        }
    }

    private void commit(String text) {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            connection.commitText(text, 1);
            vibrate(20);
        }
    }

    private void deleteText() {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            KeyboardTextActions.deleteBackward(new InputConnectionTextConnection(connection));
            vibrate(20);
        }
    }

    private void clearAllText() {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            KeyboardTextActions.clearAll(new InputConnectionTextConnection(connection));
            vibrate(35);
        }
    }

    private static final class InputConnectionTextConnection implements KeyboardTextConnection {
        private final InputConnection connection;

        InputConnectionTextConnection(InputConnection connection) {
            this.connection = connection;
        }

        @Override
        public boolean beginBatchEdit() {
            return connection.beginBatchEdit();
        }

        @Override
        public boolean endBatchEdit() {
            return connection.endBatchEdit();
        }

        @Override
        public boolean performSelectAll() {
            return connection.performContextMenuAction(android.R.id.selectAll);
        }

        @Override
        public boolean commitText(String text) {
            return connection.commitText(text, 1);
        }

        @Override
        public CharSequence getTextBeforeCursor(int maxChars) {
            return connection.getTextBeforeCursor(maxChars, 0);
        }

        @Override
        public CharSequence getTextAfterCursor(int maxChars) {
            return connection.getTextAfterCursor(maxChars, 0);
        }

        @Override
        public boolean deleteSurroundingText(int beforeLength, int afterLength) {
            return connection.deleteSurroundingText(beforeLength, afterLength);
        }
    }

    private void submit() {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return;
        }

        boolean chatGptComposer = currentEditorInfo != null
                && CHATGPT_PACKAGE.equals(currentEditorInfo.packageName);
        if (chatGptComposer) {
            Log.i(TAG, "Submitting ChatGPT composer with IME_ACTION_SEND");
            connection.performEditorAction(EditorInfo.IME_ACTION_SEND);
            vibrate(20);
            return;
        }

        int action = EditorInfo.IME_ACTION_UNSPECIFIED;
        if (currentEditorInfo != null) {
            if (currentEditorInfo.actionId != 0) {
                action = currentEditorInfo.actionId;
            } else {
                action = currentEditorInfo.imeOptions & EditorInfo.IME_MASK_ACTION;
            }
        }
        boolean submitted = action != EditorInfo.IME_ACTION_UNSPECIFIED
                && action != EditorInfo.IME_ACTION_NONE
                && connection.performEditorAction(action);
        if (!submitted) {
            connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
        }
        vibrate(20);
    }

    private boolean isSensitive(EditorInfo info) {
        if (info == null) {
            return false;
        }
        int inputClass = info.inputType & InputType.TYPE_MASK_CLASS;
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        if (inputClass == InputType.TYPE_CLASS_NUMBER) {
            return variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        }
        if (inputClass != InputType.TYPE_CLASS_TEXT) {
            return false;
        }
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
    }

    private boolean commitBestResult(Bundle results) {
        ArrayList<String> matches =
                results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            transcriptBuffer.onFinal(matches.get(0));
        }
        return commitBufferedTranscript();
    }

    private boolean commitBufferedTranscript() {
        if (!transcriptBuffer.hasText()) {
            return false;
        }
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return false;
        }
        connection.commitText(transcriptBuffer.commitText(), 1);
        return true;
    }

    private void showReady() {
        if (speechButton == null) {
            return;
        }
        listening = false;
        speechButton.setText("TAP ANYWHERE\nTO SPEAK\n\nLONG PRESS FOR\nTYPING KEYBOARD");
        speechButton.setTextColor(Color.BLACK);
        speechButton.setBackground(background(YELLOW));
        speechButton.setContentDescription(
                "Speech button. Double tap to dictate. Long press for typing keyboard.");
    }

    private void showListening(String message) {
        if (speechButton == null) {
            return;
        }
        speechButton.setText(message);
        speechButton.setTextColor(Color.WHITE);
        speechButton.setBackground(background(RED));
        speechButton.setContentDescription("Listening. Double tap to stop.");
        speechButton.announceForAccessibility("Listening");
    }

    private void showError(String message) {
        listening = false;
        if (speechButton == null) {
            return;
        }
        speechButton.setText(message + "\n\nTAP TO TRY AGAIN");
        speechButton.setTextColor(Color.WHITE);
        speechButton.setBackground(background(RED));
        speechButton.announceForAccessibility(message);
    }

    private GradientDrawable background(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setStroke(dp(2), Color.BLACK);
        drawable.setCornerRadius(dp(6));
        return drawable;
    }

    private void vibrate(long milliseconds) {
        Vibrator vibrator = getSystemService(Vibrator.class);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(
                    milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
        showListening("SPEAK NOW\n\nTAP TO STOP");
    }

    @Override
    public void onBeginningOfSpeech() {
        showListening("HEARING YOU...\n\nTAP TO STOP");
    }

    @Override
    public void onRmsChanged(float rmsdB) {
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
    }

    @Override
    public void onEndOfSpeech() {
        if (speechButton != null) {
            speechButton.setText("PROCESSING...");
        }
    }

    @Override
    public void onError(int error) {
        listening = false;
        if (isLanguageError(error) && retryWithBasicRecognizerIntent()) {
            return;
        }
        if ((error == SpeechRecognizer.ERROR_NO_MATCH
                || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                && commitBufferedTranscript()) {
            vibrate(45);
            showSpeechMode();
        } else if (error == SpeechRecognizer.ERROR_NO_MATCH
                || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            showError("I DID NOT CATCH THAT");
        } else if (isLanguageError(error)) {
            showError("SPEECH LANGUAGE NOT AVAILABLE");
        } else {
            showError("SPEECH ERROR " + error);
        }
    }

    private boolean isLanguageError(int error) {
        return error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
                || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE;
    }

    private boolean retryWithBasicRecognizerIntent() {
        if (retriedBasicRecognizerIntent || recognizer == null) {
            return false;
        }
        retriedBasicRecognizerIntent = true;
        listening = true;
        showListening("RETRYING BASIC SPEECH...\n\nTAP TO STOP");
        try {
            recognizer.startListening(createRecognizerIntent(false));
            return true;
        } catch (RuntimeException retryError) {
            Log.e(TAG, "Could not retry speech recognition", retryError);
            listening = false;
            recognizer.destroy();
            recognizer = null;
            return false;
        }
    }

    @Override
    public void onResults(Bundle results) {
        listening = false;
        commitBestResult(results);
        vibrate(45);
        showSpeechMode();
        if (speechButton != null) {
            speechButton.announceForAccessibility("Text entered");
        }
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        ArrayList<String> matches =
                partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            transcriptBuffer.onPartial(matches.get(0));
            if (speechButton != null) {
                speechButton.setText("HEARING:\n" + transcriptBuffer.currentText()
                        + "\n\nTAP TO STOP");
            }
        }
    }

    @Override
    public void onEvent(int eventType, Bundle params) {
    }
}
