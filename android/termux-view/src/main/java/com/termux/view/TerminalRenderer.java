package com.termux.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalRow;
import com.termux.terminal.TextStyle;
import com.termux.terminal.WcWidth;

/**
 * Renderer of a {@link TerminalEmulator} into a {@link Canvas}.
 * <p>
 * Dual-width grid (Termux / xterm model) with safe handling of <b>proportional</b>
 * proportional / CJK-fallback faces:
 * <ul>
 *   <li>Cell width must fit wide Latin (M/W) <em>and</em> half of a CJK ideograph —
 *       otherwise {@code M} is clipped and can look like {@code N}.</li>
 *   <li>Only <b>compress</b> (scale &lt; 1) when a run is wider than its cells.
 *       Never stretch narrow runs (that was 汉字压扁).</li>
 *   <li>Mono fonts (JetBrains etc.) measure equal → no scale, same as Termux stock.</li>
 * </ul>
 */
public final class TerminalRenderer {

    final int mTextSize;
    final Typeface mTypeface;
    private final Paint mTextPaint = new Paint();

    final float mFontWidth;
    final int mFontLineSpacing;
    private final int mFontAscent;
    final int mFontLineSpacingAndAscent;

    private final float[] asciiMeasures = new float[127];

    public TerminalRenderer(int textSize, Typeface typeface) {
        mTextSize = textSize;
        mTypeface = typeface;

        mTextPaint.setTypeface(typeface);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setSubpixelText(true);
        mTextPaint.setTextSize(textSize);

        // Line metrics — Termux-like slight padding, not inflated CJK bounds.
        mFontLineSpacing = (int) Math.ceil(mTextPaint.getFontSpacing() * 1.06f);
        mFontAscent = (int) Math.ceil(mTextPaint.ascent());
        mFontLineSpacingAndAscent = mFontLineSpacing + mFontAscent;

        // --- Cell width ---
        // Proportional CJK faces: M/W are often wider than X and wider than 汉/2.
        // Using only max(X, 汉/2) → M gets clipped → looks like N (user report).
        final float latinX = mTextPaint.measureText("X");
        final float latinM = mTextPaint.measureText("M");
        final float latinW = mTextPaint.measureText("W");
        final float latin = Math.max(latinX, Math.max(latinM, latinW));
        final float cjkHalf = mTextPaint.measureText("汉") / 2f;
        float cell = Math.max(latin, cjkHalf);
        if (cell < 1f) cell = textSize * 0.6f;
        mFontWidth = cell;

        StringBuilder sb = new StringBuilder(" ");
        for (int i = 0; i < asciiMeasures.length; i++) {
            sb.setCharAt(0, (char) i);
            asciiMeasures[i] = mTextPaint.measureText(sb, 0, 1);
        }
    }

    public final void render(TerminalEmulator mEmulator, Canvas canvas, int topRow,
                             int selectionY1, int selectionY2, int selectionX1, int selectionX2) {
        final boolean reverseVideo = mEmulator.isReverseVideo();
        final int endRow = topRow + mEmulator.mRows;
        final int columns = mEmulator.mColumns;
        final int cursorCol = mEmulator.getCursorCol();
        final int cursorRow = mEmulator.getCursorRow();
        final boolean cursorVisible = mEmulator.shouldCursorBeVisible();
        final TerminalBuffer screen = mEmulator.getScreen();
        final int[] palette = mEmulator.mColors.mCurrentColors;
        final int cursorShape = mEmulator.getCursorStyle();

        if (reverseVideo)
            canvas.drawColor(palette[TextStyle.COLOR_INDEX_FOREGROUND], PorterDuff.Mode.SRC);

        float heightOffset = mFontLineSpacingAndAscent;
        for (int row = topRow; row < endRow; row++) {
            heightOffset += mFontLineSpacing;

            final int cursorX = (row == cursorRow && cursorVisible) ? cursorCol : -1;
            int selx1 = -1, selx2 = -1;
            if (row >= selectionY1 && row <= selectionY2) {
                if (row == selectionY1) selx1 = selectionX1;
                selx2 = (row == selectionY2) ? selectionX2 : mEmulator.mColumns;
            }

            TerminalRow lineObject = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row));
            final char[] line = lineObject.mText;
            final int charsUsedInLine = lineObject.getSpaceUsed();

            long lastRunStyle = 0;
            boolean lastRunInsideCursor = false;
            boolean lastRunInsideSelection = false;
            int lastRunStartColumn = -1;
            int lastRunStartIndex = 0;
            boolean lastRunFontWidthMismatch = false;
            int currentCharIndex = 0;
            float measuredWidthForRun = 0.f;

            for (int column = 0; column < columns; ) {
                final char charAtIndex = line[currentCharIndex];
                final boolean charIsHighsurrogate = Character.isHighSurrogate(charAtIndex);
                final int charsForCodePoint = charIsHighsurrogate ? 2 : 1;
                final int codePoint = charIsHighsurrogate
                    ? Character.toCodePoint(charAtIndex, line[currentCharIndex + 1])
                    : charAtIndex;
                final int codePointWcWidth = WcWidth.width(codePoint);
                final boolean insideCursor =
                    (cursorX == column || (codePointWcWidth == 2 && cursorX == column + 1));
                final boolean insideSelection = column >= selx1 && column <= selx2;
                final long style = lineObject.getStyle(column);

                final float measuredCodePointWidth =
                    (codePoint < asciiMeasures.length)
                        ? asciiMeasures[codePoint]
                        : mTextPaint.measureText(line, currentCharIndex, charsForCodePoint);
                // Break run when glyph width (in cells) differs — so scale applies per glyph run.
                final boolean fontWidthMismatch =
                    Math.abs(measuredCodePointWidth / mFontWidth - codePointWcWidth) > 0.01;

                if (style != lastRunStyle || insideCursor != lastRunInsideCursor
                    || insideSelection != lastRunInsideSelection
                    || fontWidthMismatch || lastRunFontWidthMismatch) {
                    if (column != 0) {
                        final int columnWidthSinceLastRun = column - lastRunStartColumn;
                        final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
                        int cursorColor = lastRunInsideCursor
                            ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
                        boolean invertCursorTextColor = lastRunInsideCursor
                            && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
                        drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn,
                            columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun,
                            measuredWidthForRun, cursorColor, cursorShape, lastRunStyle,
                            reverseVideo || invertCursorTextColor || lastRunInsideSelection);
                    }
                    measuredWidthForRun = 0.f;
                    lastRunStyle = style;
                    lastRunInsideCursor = insideCursor;
                    lastRunInsideSelection = insideSelection;
                    lastRunStartColumn = column;
                    lastRunStartIndex = currentCharIndex;
                    lastRunFontWidthMismatch = fontWidthMismatch;
                }
                measuredWidthForRun += measuredCodePointWidth;
                column += codePointWcWidth;
                currentCharIndex += charsForCodePoint;
                while (currentCharIndex < charsUsedInLine
                    && WcWidth.width(line, currentCharIndex) <= 0) {
                    currentCharIndex += Character.isHighSurrogate(line[currentCharIndex]) ? 2 : 1;
                }
            }

            final int columnWidthSinceLastRun = columns - lastRunStartColumn;
            final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
            int cursorColor = lastRunInsideCursor
                ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
            boolean invertCursorTextColor = lastRunInsideCursor
                && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
            drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn,
                columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun,
                measuredWidthForRun, cursorColor, cursorShape, lastRunStyle,
                reverseVideo || invertCursorTextColor || lastRunInsideSelection);
        }
    }

    private void drawTextRun(Canvas canvas, char[] text, int[] palette, float y, int startColumn,
                             int runWidthColumns, int startCharIndex, int runWidthChars, float mes,
                             int cursor, int cursorStyle, long textStyle, boolean reverseVideo) {
        int foreColor = TextStyle.decodeForeColor(textStyle);
        final int effect = TextStyle.decodeEffect(textStyle);
        int backColor = TextStyle.decodeBackColor(textStyle);
        final boolean bold =
            (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;
        final boolean underline = (effect & TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0;
        final boolean italic = (effect & TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0;
        final boolean strikeThrough = (effect & TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH) != 0;
        final boolean dim = (effect & TextStyle.CHARACTER_ATTRIBUTE_DIM) != 0;

        if ((foreColor & 0xff000000) != 0xff000000) {
            if (bold && foreColor >= 0 && foreColor < 8) foreColor += 8;
            foreColor = palette[foreColor];
        }
        if ((backColor & 0xff000000) != 0xff000000) {
            backColor = palette[backColor];
        }

        final boolean reverseVideoHere =
            reverseVideo ^ (effect & TextStyle.CHARACTER_ATTRIBUTE_INVERSE) != 0;
        if (reverseVideoHere) {
            int tmp = foreColor;
            foreColor = backColor;
            backColor = tmp;
        }

        float left = startColumn * mFontWidth;
        float right = left + runWidthColumns * mFontWidth;

        // Measured run width in *cells* (Termux).
        final float mesCells = mes / mFontWidth;
        // Only compress when wider than the cell span. Never stretch (压扁).
        // Clipping wide M was turning it into something that looked like N.
        boolean savedMatrix = false;
        if (mesCells > runWidthColumns + 0.01f) {
            canvas.save();
            canvas.scale(runWidthColumns / mesCells, 1.f);
            left *= mesCells / runWidthColumns;
            right *= mesCells / runWidthColumns;
            savedMatrix = true;
        }

        if (backColor != palette[TextStyle.COLOR_INDEX_BACKGROUND]) {
            mTextPaint.setColor(backColor);
            canvas.drawRect(left, y - mFontLineSpacingAndAscent + mFontAscent, right, y, mTextPaint);
        }

        if (cursor != 0) {
            mTextPaint.setColor(cursor);
            float cursorHeight = mFontLineSpacingAndAscent - mFontAscent;
            float cRight = right;
            if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE) {
                cursorHeight /= 4.f;
            } else if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR) {
                cRight -= ((right - left) * 3) / 4.f;
            }
            canvas.drawRect(left, y - cursorHeight, cRight, y, mTextPaint);
        }

        if ((effect & TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE) == 0) {
            if (dim) {
                int red = (0xFF & (foreColor >> 16));
                int green = (0xFF & (foreColor >> 8));
                int blue = (0xFF & foreColor);
                red = red * 2 / 3;
                green = green * 2 / 3;
                blue = blue * 2 / 3;
                foreColor = 0xFF000000 + (red << 16) + (green << 8) + blue;
            }

            mTextPaint.setFakeBoldText(bold);
            mTextPaint.setUnderlineText(underline);
            mTextPaint.setTextSkewX(italic ? -0.35f : 0.f);
            mTextPaint.setStrikeThruText(strikeThrough);
            mTextPaint.setColor(foreColor);

            canvas.drawTextRun(
                text, startCharIndex, runWidthChars, startCharIndex, runWidthChars,
                left, y - mFontLineSpacingAndAscent, false, mTextPaint
            );
        }

        if (savedMatrix) canvas.restore();
    }

    public float getFontWidth() {
        return mFontWidth;
    }

    public int getFontLineSpacing() {
        return mFontLineSpacing;
    }
}
