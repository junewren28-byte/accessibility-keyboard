package org.example.speechboard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class SpeechBiasPhrases {
    private SpeechBiasPhrases() {
    }

    static MergeResult merge(String manual, Collection<String> dictionary, Collection<String> contacts) {
        List<String> merged = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        addLines(merged, seen, manual);
        int dictionaryAdded = addPhrases(merged, seen, dictionary);
        int contactsAdded = addPhrases(merged, seen, contacts);
        return new MergeResult(join(merged), dictionaryAdded, contactsAdded);
    }

    static List<String> splitLines(String raw) {
        List<String> phrases = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return phrases;
        }
        String[] lines = raw.split("\\r?\\n");
        for (String line : lines) {
            String phrase = normalize(line);
            if (!phrase.isEmpty()) {
                phrases.add(phrase);
            }
        }
        return phrases;
    }

    private static void addLines(List<String> merged, Set<String> seen, String raw) {
        addPhrases(merged, seen, splitLines(raw));
    }

    private static int addPhrases(List<String> merged, Set<String> seen, Collection<String> phrases) {
        if (phrases == null || phrases.isEmpty()) {
            return 0;
        }
        int added = 0;
        for (String phrase : phrases) {
            String normalized = normalize(phrase);
            if (normalized.isEmpty()) {
                continue;
            }
            String key = normalized.toLowerCase(Locale.US);
            if (seen.add(key)) {
                merged.add(normalized);
                added++;
            }
        }
        return added;
    }

    private static String normalize(String phrase) {
        return phrase == null ? "" : phrase.trim().replaceAll("\\s+", " ");
    }

    private static String join(List<String> phrases) {
        StringBuilder builder = new StringBuilder();
        for (String phrase : phrases) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(phrase);
        }
        return builder.toString();
    }

    static final class MergeResult {
        final String text;
        final int dictionaryAdded;
        final int contactsAdded;

        MergeResult(String text, int dictionaryAdded, int contactsAdded) {
            this.text = text;
            this.dictionaryAdded = dictionaryAdded;
            this.contactsAdded = contactsAdded;
        }
    }
}
