package com.termux.terminal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TerminalBufferTranscriptTest {
    private static final int COLUMNS = 10;
    private static final int SCREEN_ROWS = 3;

    private static TerminalBuffer bufferWithScrolledLines(int totalRows, int scrolled) {
        TerminalBuffer buffer = new TerminalBuffer(COLUMNS, totalRows, SCREEN_ROWS);
        for (int i = 0; i < scrolled; i++) {
            buffer.scrollDownOneLine(0, SCREEN_ROWS, TextStyle.NORMAL);
        }
        return buffer;
    }

    private static String rowText(TerminalBuffer buffer, int row) {
        return buffer.getSelectedText(0, row, COLUMNS, row, false);
    }

    @Test
    public void replaceDropsOldLinesAndKeepsNewestNextToScreen() {
        // Wraps the ring buffer first, as a long-running session does.
        TerminalBuffer buffer = bufferWithScrolledLines(10, 25);
        buffer.setChar(0, 0, 'S', TextStyle.NORMAL);

        buffer.replaceTranscriptLines(new String[]{"old", "mid", "new"});

        assertEquals(3, buffer.getActiveTranscriptRows());
        assertEquals("old", rowText(buffer, -3));
        assertEquals("mid", rowText(buffer, -2));
        assertEquals("new", rowText(buffer, -1));
        assertEquals("S", rowText(buffer, 0));
    }

    @Test
    public void historyLargerThanCapacityKeepsNewestLines() {
        TerminalBuffer buffer = bufferWithScrolledLines(5, 0);

        buffer.replaceTranscriptLines(new String[]{"a", "b", "c", "d"});

        assertEquals(2, buffer.getActiveTranscriptRows());
        assertEquals("c", rowText(buffer, -2));
        assertEquals("d", rowText(buffer, -1));
    }

    @Test
    public void emptyHistoryClearsTranscript() {
        TerminalBuffer buffer = bufferWithScrolledLines(10, 4);

        buffer.replaceTranscriptLines(new String[0]);

        assertEquals(0, buffer.getActiveTranscriptRows());
    }
}
