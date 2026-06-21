package org.example.speechboard;

final class KeyboardTextActionsTest {
    private KeyboardTextActionsTest() {
    }

    static void run() {
        deleteBackwardDeletesOneCharacterBeforeCursor();
        clearAllUsesSelectAllCommitWhenAvailable();
        clearAllFallsBackToSurroundingTextDelete();
        clearAllEndsBatchWhenCommitFails();
    }

    private static void deleteBackwardDeletesOneCharacterBeforeCursor() {
        FakeConnection connection = new FakeConnection("hello", "", true, true);
        KeyboardTextActions.deleteBackward(connection);
        assertEquals(1, connection.deleteBefore, "delete before count");
        assertEquals(0, connection.deleteAfter, "delete after count");
    }

    private static void clearAllUsesSelectAllCommitWhenAvailable() {
        FakeConnection connection = new FakeConnection("hello ", "world", true, true);
        KeyboardTextActions.clearAll(connection);
        assertEquals(1, connection.beginBatchCount, "begin batch count");
        assertEquals(1, connection.selectAllCount, "select all count");
        assertEquals(1, connection.commitEmptyCount, "commit empty count");
        assertEquals(0, connection.deleteCount, "fallback delete count");
        assertEquals(1, connection.endBatchCount, "end batch count");
    }

    private static void clearAllFallsBackToSurroundingTextDelete() {
        FakeConnection connection = new FakeConnection("hello ", "world", false, false);
        KeyboardTextActions.clearAll(connection);
        assertEquals(6, connection.deleteBefore, "fallback delete before count");
        assertEquals(5, connection.deleteAfter, "fallback delete after count");
        assertEquals(1, connection.endBatchCount, "fallback end batch count");
    }

    private static void clearAllEndsBatchWhenCommitFails() {
        FakeConnection connection = new FakeConnection("abc", "def", true, false);
        KeyboardTextActions.clearAll(connection);
        assertEquals(3, connection.deleteBefore, "commit failure delete before count");
        assertEquals(3, connection.deleteAfter, "commit failure delete after count");
        assertEquals(1, connection.endBatchCount, "commit failure end batch count");
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected <" + expected
                    + "> but was <" + actual + ">");
        }
    }

    private static final class FakeConnection implements KeyboardTextConnection {
        private final String before;
        private final String after;
        private final boolean selectAllResult;
        private final boolean commitResult;
        private int beginBatchCount;
        private int endBatchCount;
        private int selectAllCount;
        private int commitEmptyCount;
        private int deleteCount;
        private int deleteBefore;
        private int deleteAfter;

        FakeConnection(
                String before, String after, boolean selectAllResult, boolean commitResult) {
            this.before = before;
            this.after = after;
            this.selectAllResult = selectAllResult;
            this.commitResult = commitResult;
        }

        @Override
        public boolean beginBatchEdit() {
            beginBatchCount++;
            return true;
        }

        @Override
        public boolean endBatchEdit() {
            endBatchCount++;
            return true;
        }

        @Override
        public boolean performSelectAll() {
            selectAllCount++;
            return selectAllResult;
        }

        @Override
        public boolean commitText(String text) {
            if ("".equals(text)) {
                commitEmptyCount++;
            }
            return commitResult;
        }

        @Override
        public CharSequence getTextBeforeCursor(int maxChars) {
            return before;
        }

        @Override
        public CharSequence getTextAfterCursor(int maxChars) {
            return after;
        }

        @Override
        public boolean deleteSurroundingText(int beforeLength, int afterLength) {
            deleteCount++;
            deleteBefore = beforeLength;
            deleteAfter = afterLength;
            return true;
        }
    }
}
