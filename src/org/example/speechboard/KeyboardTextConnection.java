package org.example.speechboard;

interface KeyboardTextConnection {
    boolean beginBatchEdit();

    boolean endBatchEdit();

    boolean performSelectAll();

    boolean commitText(String text);

    CharSequence getTextBeforeCursor(int maxChars);

    CharSequence getTextAfterCursor(int maxChars);

    boolean deleteSurroundingText(int beforeLength, int afterLength);
}
