package org.example.speechboard;

import java.util.ArrayList;

final class SpeechRecognitionExtras {
    static final String EXTRA_BIASING_STRINGS = "android.speech.extra.BIASING_STRINGS";
    static final String EXTRA_ENABLE_BIASING_DEVICE_CONTEXT =
            "android.speech.extra.ENABLE_BIASING_DEVICE_CONTEXT";
    static final String EXTRA_ENABLE_FORMATTING = "android.speech.extra.ENABLE_FORMATTING";
    static final String EXTRA_MASK_OFFENSIVE_WORDS = "android.speech.extra.MASK_OFFENSIVE_WORDS";
    static final String FORMATTING_OPTIMIZE_QUALITY = "quality";

    private SpeechRecognitionExtras() {
    }

    static ArrayList<String> parseBiasPhrases(String raw) {
        ArrayList<String> phrases = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return phrases;
        }
        String[] lines = raw.split("\\r?\\n");
        for (String line : lines) {
            String phrase = line.trim();
            if (!phrase.isEmpty()) {
                phrases.add(phrase);
            }
        }
        return phrases;
    }
}
