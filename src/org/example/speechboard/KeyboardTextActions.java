package org.example.speechboard;

final class KeyboardTextActions {
    private static final int MAX_CLEAR_CHARS = 1000000;

    private KeyboardTextActions() {
    }

    static void deleteBackward(KeyboardTextConnection connection) {
        if (connection != null) {
            connection.deleteSurroundingText(1, 0);
        }
    }

    static void clearAll(KeyboardTextConnection connection) {
        if (connection == null) {
            return;
        }
        connection.beginBatchEdit();
        try {
            boolean selected = connection.performSelectAll();
            boolean cleared = selected && connection.commitText("");
            if (!cleared) {
                CharSequence before = connection.getTextBeforeCursor(MAX_CLEAR_CHARS);
                CharSequence after = connection.getTextAfterCursor(MAX_CLEAR_CHARS);
                int beforeLength = before == null ? 0 : before.length();
                int afterLength = after == null ? 0 : after.length();
                connection.deleteSurroundingText(beforeLength, afterLength);
            }
        } finally {
            connection.endBatchEdit();
        }
    }
}
