package org.example.speechboard;

import java.util.ArrayList;

final class SpeechRecognitionExtrasTest {
    private SpeechRecognitionExtrasTest() {
    }

    static void run() {
        recognitionExtraKeysMatchAndroidContracts();
        biasPhrasesKeepUserVocabulary();
    }

    private static void recognitionExtraKeysMatchAndroidContracts() {
        assertEquals("android.speech.extra.BIASING_STRINGS",
                SpeechRecognitionExtras.EXTRA_BIASING_STRINGS, "biasing strings key");
        assertEquals("android.speech.extra.ENABLE_BIASING_DEVICE_CONTEXT",
                SpeechRecognitionExtras.EXTRA_ENABLE_BIASING_DEVICE_CONTEXT,
                "device context bias key");
        assertEquals("android.speech.extra.ENABLE_FORMATTING",
                SpeechRecognitionExtras.EXTRA_ENABLE_FORMATTING, "formatting key");
        assertEquals("android.speech.extra.MASK_OFFENSIVE_WORDS",
                SpeechRecognitionExtras.EXTRA_MASK_OFFENSIVE_WORDS, "offensive masking key");
        assertEquals("quality", SpeechRecognitionExtras.FORMATTING_OPTIMIZE_QUALITY,
                "formatting quality value");
    }

    private static void biasPhrasesKeepUserVocabulary() {
        ArrayList<String> phrases = SpeechRecognitionExtras.parseBiasPhrases(
                "  my own voice profile  \n\nChatGPT\ncustom medical term\n");
        assertEquals(3, phrases.size(), "bias phrase count");
        assertEquals("my own voice profile", phrases.get(0), "first bias phrase");
        assertEquals("ChatGPT", phrases.get(1), "second bias phrase");
        assertEquals("custom medical term", phrases.get(2), "third bias phrase");
    }

    private static void assertEquals(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected <" + expected
                    + "> but was <" + actual + ">");
        }
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected <" + expected
                    + "> but was <" + actual + ">");
        }
    }
}
