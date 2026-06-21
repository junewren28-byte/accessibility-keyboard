package org.example.speechboard;

public final class SpeechTranscriptBufferTest {
    public static void main(String[] args) {
        partialRevisionsStayOnePhrase();
        pauseStartsNewSentence();
        finalAfterMultipleSegmentsCommitsAllText();
        timeoutCommitsLatestPartial();
        duplicateFinalDoesNotDuplicateText();
        resetAfterCommit();
        SpeechRecognitionExtrasTest.run();
        KeyboardTextActionsTest.run();
        SpeechBiasPhrasesTest.run();
    }

    private static void partialRevisionsStayOnePhrase() {
        SpeechTranscriptBuffer buffer = new SpeechTranscriptBuffer();
        buffer.onPartial("how");
        buffer.onPartial("how are");
        buffer.onPartial("how are you");
        assertEquals("how are you", buffer.currentText(), "partial revisions");
        assertEquals("how are you", buffer.commitText(), "partial commit");
    }

    private static void pauseStartsNewSentence() {
        SpeechTranscriptBuffer buffer = new SpeechTranscriptBuffer();
        buffer.onPartial("how are you");
        buffer.onPartial("I am testing speech board");
        assertEquals("how are you I am testing speech board", buffer.currentText(),
                "new segment after pause");
    }

    private static void finalAfterMultipleSegmentsCommitsAllText() {
        SpeechTranscriptBuffer buffer = new SpeechTranscriptBuffer();
        buffer.onPartial("first sentence");
        buffer.onPartial("second sentence");
        buffer.onFinal("second sentence");
        assertEquals("first sentence second sentence", buffer.commitText(),
                "final after multiple segments");
    }

    private static void timeoutCommitsLatestPartial() {
        SpeechTranscriptBuffer buffer = new SpeechTranscriptBuffer();
        buffer.onPartial("latest partial text");
        assertEquals("latest partial text", buffer.commitText(), "timeout partial fallback");
    }

    private static void duplicateFinalDoesNotDuplicateText() {
        SpeechTranscriptBuffer buffer = new SpeechTranscriptBuffer();
        buffer.onPartial("hello world");
        buffer.onFinal("hello world");
        buffer.onFinal("hello world");
        assertEquals("hello world", buffer.commitText(), "duplicate final");
    }

    private static void resetAfterCommit() {
        SpeechTranscriptBuffer buffer = new SpeechTranscriptBuffer();
        buffer.onPartial("one");
        assertEquals("one", buffer.commitText(), "first commit");
        buffer.onPartial("two");
        assertEquals("two", buffer.commitText(), "second commit after reset");
    }

    private static void assertEquals(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected <" + expected
                    + "> but was <" + actual + ">");
        }
    }
}
