package org.example.speechboard;

import java.util.Arrays;
import java.util.Collections;

final class SpeechBiasPhrasesTest {
    private SpeechBiasPhrasesTest() {
    }

    static void run() {
        manualDictionaryAndContactsMergeInOrder();
        duplicatePhrasesCollapseCaseInsensitively();
        blankAndWhitespaceOnlyPhrasesAreIgnored();
        nullSourcesAreAccepted();
    }

    private static void manualDictionaryAndContactsMergeInOrder() {
        SpeechBiasPhrases.MergeResult result = SpeechBiasPhrases.merge(
                "manual phrase\nChatGPT",
                Arrays.asList("dictionary word", "custom term"),
                Arrays.asList("Ada Lovelace", "Grace Hopper"));
        assertEquals("manual phrase\nChatGPT\ndictionary word\ncustom term\nAda Lovelace\nGrace Hopper",
                result.text, "merged order");
        assertEquals(2, result.dictionaryAdded, "dictionary added count");
        assertEquals(2, result.contactsAdded, "contacts added count");
    }

    private static void duplicatePhrasesCollapseCaseInsensitively() {
        SpeechBiasPhrases.MergeResult result = SpeechBiasPhrases.merge(
                "ChatGPT\nMy Voice",
                Arrays.asList("chatgpt", "dictionary only"),
                Arrays.asList("MY VOICE", "contact only"));
        assertEquals("ChatGPT\nMy Voice\ndictionary only\ncontact only", result.text,
                "case-insensitive dedupe");
        assertEquals(1, result.dictionaryAdded, "deduped dictionary count");
        assertEquals(1, result.contactsAdded, "deduped contacts count");
    }

    private static void blankAndWhitespaceOnlyPhrasesAreIgnored() {
        SpeechBiasPhrases.MergeResult result = SpeechBiasPhrases.merge(
                "  manual   phrase  \n\n",
                Arrays.asList("  ", "two   spaces"),
                Arrays.asList("", " Contact   Name "));
        assertEquals("manual phrase\ntwo spaces\nContact Name", result.text,
                "normalized blanks removed");
    }

    private static void nullSourcesAreAccepted() {
        SpeechBiasPhrases.MergeResult result = SpeechBiasPhrases.merge(
                null, null, Collections.singletonList("Contact Name"));
        assertEquals("Contact Name", result.text, "null source merge");
        assertEquals(0, result.dictionaryAdded, "null dictionary count");
        assertEquals(1, result.contactsAdded, "null contacts count");
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
