package org.example.speechboard;

import java.util.ArrayList;
import java.util.List;

final class SpeechTranscriptBuffer {
    private final List<String> committedSegments = new ArrayList<>();
    private String partialSegment;
    private String lastCommitted;

    void reset() {
        committedSegments.clear();
        partialSegment = null;
        lastCommitted = null;
    }

    void onPartial(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return;
        }
        if (partialSegment == null || isRevisionOfPartial(normalized)) {
            partialSegment = normalized;
        } else {
            commitPartial();
            partialSegment = normalized;
        }
    }

    void onFinal(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            commitPartial();
            return;
        }
        if (partialSegment != null && isSameText(normalized, partialSegment)) {
            partialSegment = normalized;
            commitPartial();
        } else {
            commitSegment(normalized);
            partialSegment = null;
        }
    }

    boolean hasText() {
        return !currentText().isEmpty();
    }

    String commitText() {
        commitPartial();
        String text = currentText();
        reset();
        return text;
    }

    String currentText() {
        List<String> all = new ArrayList<>(committedSegments);
        if (partialSegment != null && !partialSegment.isEmpty()) {
            all.add(partialSegment);
        }
        return joinSegments(all);
    }

    private void commitPartial() {
        if (partialSegment != null && !partialSegment.isEmpty()) {
            commitSegment(partialSegment);
            partialSegment = null;
        }
    }

    private void commitSegment(String segment) {
        if (segment.isEmpty() || isSameText(segment, lastCommitted)) {
            return;
        }
        committedSegments.add(segment);
        lastCommitted = segment;
    }

    private boolean isRevisionOfPartial(String text) {
        if (partialSegment == null) {
            return true;
        }
        String lowerText = text.toLowerCase();
        String lowerPartial = partialSegment.toLowerCase();
        return lowerText.startsWith(lowerPartial) || lowerPartial.startsWith(lowerText);
    }

    private static boolean isSameText(String first, String second) {
        if (first == null || second == null) {
            return false;
        }
        return normalize(first).equalsIgnoreCase(normalize(second));
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ");
    }

    private static String joinSegments(List<String> segments) {
        StringBuilder builder = new StringBuilder();
        for (String segment : segments) {
            String normalized = normalize(segment);
            if (normalized.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(normalized);
        }
        return builder.toString();
    }
}
