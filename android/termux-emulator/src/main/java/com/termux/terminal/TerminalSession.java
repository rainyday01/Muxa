package com.termux.terminal;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Terminal session adapted from Termux for **remote** PTY (SSH/Mosh).
 * <p>
 * Upstream Termux couples this class to a local JNI PTY. m-tmux keeps the same
 * public API expected by {@code TerminalView}, but feeds bytes from a remote
 * stream and writes user input via {@link WriteListener}.
 * <p>
 * Copyright: Termux authors (GPLv3); remote-mode adaptations for m-tmux.
 */
public final class TerminalSession extends TerminalOutput {

    public interface WriteListener {
        void onWrite(byte[] data, int offset, int count);
    }

    public interface SizeListener {
        void onSize(int columns, int rows);
    }

    private static final int MSG_NEW_INPUT = 1;

    public final String mHandle = UUID.randomUUID().toString();

    TerminalEmulator mEmulator;

    final ByteQueue mProcessToTerminalIOQueue = new ByteQueue(64 * 1024);
    private final byte[] mUtf8InputBuffer = new byte[5];

    TerminalSessionClient mClient;

    /** >0 while "running" (remote attached). -1 finished. */
    int mShellPid = 0;
    int mShellExitStatus;

    public String mSessionName;

    final Handler mMainThreadHandler = new MainThreadHandler();

    private final Integer mTranscriptRows;
    private WriteListener mWriteListener;
    private SizeListener mSizeListener;

    /** Uptime of the last remote output fed into the emulator (0 = none yet). */
    private long mLastRemoteOutputAt = 0L;
    /** Uptime of the first unanswered user write; cleared by the next remote output. */
    private long mRttProbeAt = 0L;
    /** Smoothed keystroke→echo latency (ms); 0 until the first sample. */
    private long mRttEstimateMs = 0L;
    /** True while the emulator is parsing remote bytes (its replies are not user input). */
    private boolean mInEmulatorAppend = false;

    private static final String LOG_TAG = "TerminalSession";

    /** Remote session: no local subprocess. */
    public TerminalSession(TerminalSessionClient client, Integer transcriptRows) {
        this.mTranscriptRows = transcriptRows;
        this.mClient = client;
    }

    public void setWriteListener(WriteListener listener) {
        mWriteListener = listener;
    }

    public void setSizeListener(SizeListener listener) {
        mSizeListener = listener;
    }

    public void updateTerminalSessionClient(TerminalSessionClient client) {
        mClient = client;
        if (mEmulator != null) mEmulator.updateTerminalSessionClient(client);
    }

    /** Inform of new size; initialize or resize emulator. Also notifies remote WINCH. */
    public void updateSize(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        if (mEmulator == null) {
            initializeEmulator(columns, rows, cellWidthPixels, cellHeightPixels);
        } else {
            final boolean gridChanged =
                mEmulator.mColumns != columns || mEmulator.mRows != rows;
            mEmulator.resize(columns, rows, cellWidthPixels, cellHeightPixels);
            // Only WINCH when the character grid changes. Font-only pixel tweaks with the
            // same cols×rows used to re-fire WINCH → apps re-query DA2 → "41;320;0c" junk
            // in the prompt when the response is echoed or mishandled mid-input.
            if (gridChanged && mSizeListener != null) {
                mSizeListener.onSize(columns, rows);
            }
        }
    }

    public String getTitle() {
        return (mEmulator == null) ? null : mEmulator.getTitle();
    }

    public void initializeEmulator(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        mEmulator = new TerminalEmulator(this, columns, rows, cellWidthPixels, cellHeightPixels, mTranscriptRows, mClient);
        mShellPid = 1; // remote "running"
        if (mClient != null) mClient.setTerminalShellPid(this, mShellPid);
        if (mSizeListener != null) mSizeListener.onSize(columns, rows);
        // Drain bytes that arrived before the view had a size (install shell MOTD, etc.).
        // Without this, handleMessage bailed on mEmulator==null and left a black screen.
        mMainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT);
    }

    /** Feed remote PTY output into the emulator (thread-safe; processed on main). */
    public void appendOutput(byte[] data, int offset, int count) {
        if (count <= 0 || mShellPid <= 0) return;
        if (!mProcessToTerminalIOQueue.write(data, offset, count)) return;
        mMainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT);
    }

    public void appendOutput(byte[] data) {
        if (data == null) return;
        appendOutput(data, 0, data.length);
    }

    /** Write data to remote process (keyboard / special keys). */
    @Override
    public void write(byte[] data, int offset, int count) {
        if (mShellPid > 0 && mWriteListener != null && count > 0) {
            if (!mInEmulatorAppend && mRttProbeAt == 0L) mRttProbeAt = SystemClock.uptimeMillis();
            mWriteListener.onWrite(data, offset, count);
        }
    }

    /** True while the emulator answers a remote query (DA, DSR…): that write is not user input. */
    public boolean isWritingEmulatorReply() {
        return mInEmulatorAppend;
    }

    /** Uptime (ms) of the last remote output applied to the emulator; 0 if none. */
    public long getLastRemoteOutputAt() {
        return mLastRemoteOutputAt;
    }

    /** Smoothed round trip from a keystroke to the next remote output; 0 if unknown. */
    public long getRemoteRttEstimateMs() {
        return mRttEstimateMs;
    }

    private void noteRemoteOutput() {
        long now = SystemClock.uptimeMillis();
        mLastRemoteOutputAt = now;
        if (mRttProbeAt == 0L) return;
        long sample = now - mRttProbeAt;
        mRttProbeAt = 0L;
        if (sample < 2L || sample > 2_000L) return;
        mRttEstimateMs = mRttEstimateMs == 0L ? sample : (mRttEstimateMs * 3 + sample) / 4;
    }

    public void writeCodePoint(boolean prependEscape, int codePoint) {
        if (codePoint > 1114111 || (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
            throw new IllegalArgumentException("Invalid code point: " + codePoint);
        }
        int bufferPosition = 0;
        if (prependEscape) mUtf8InputBuffer[bufferPosition++] = 27;
        if (codePoint <= 0b1111111) {
            mUtf8InputBuffer[bufferPosition++] = (byte) codePoint;
        } else if (codePoint <= 0b11111111111) {
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11000000 | (codePoint >> 6));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        } else if (codePoint <= 0b1111111111111111) {
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11100000 | (codePoint >> 12));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        } else {
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11110000 | (codePoint >> 18));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 12) & 0b111111));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        }
        write(mUtf8InputBuffer, 0, bufferPosition);
    }

    public TerminalEmulator getEmulator() {
        return mEmulator;
    }

    protected void notifyScreenUpdate() {
        if (mClient != null) mClient.onTextChanged(this);
    }

    /** Force view refresh (e.g. after activity resume). */
    public void refresh() {
        notifyScreenUpdate();
    }

    /** Insert history lines above the live screen so local finger-scroll has content. */
    public void prependScrollback(String[] oldestFirst) {
        if (mEmulator == null || oldestFirst == null || oldestFirst.length == 0) return;
        mEmulator.prependScrollback(oldestFirst);
        notifyScreenUpdate();
    }

    public void reset() {
        if (mEmulator != null) {
            mEmulator.reset();
            notifyScreenUpdate();
        }
    }

    public void finishIfRunning() {
        if (isRunning()) {
            mShellPid = -1;
            mProcessToTerminalIOQueue.close();
            if (mClient != null) mClient.onSessionFinished(this);
        }
    }

    @Override
    public void titleChanged(String oldTitle, String newTitle) {
        if (mClient != null) mClient.onTitleChanged(this);
    }

    public synchronized boolean isRunning() {
        return mShellPid > 0;
    }

    public synchronized int getExitStatus() {
        return mShellExitStatus;
    }

    @Override
    public void onCopyTextToClipboard(String text) {
        if (mClient != null) mClient.onCopyTextToClipboard(this, text);
    }

    @Override
    public void onPasteTextFromClipboard() {
        if (mClient != null) mClient.onPasteTextFromClipboard(this);
    }

    @Override
    public void onBell() {
        if (mClient != null) mClient.onBell(this);
    }

    @Override
    public void onColorsChanged() {
        if (mClient != null) mClient.onColorsChanged(this);
    }

    public int getPid() {
        return mShellPid;
    }

    public String getCwd() {
        return null;
    }

    @SuppressLint("HandlerLeak")
    class MainThreadHandler extends Handler {
        final byte[] mReceiveBuffer = new byte[64 * 1024];

        MainThreadHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            if (mEmulator == null) return;
            // Read until queue empty so a burst before first layout is fully applied.
            for (;;) {
                int bytesRead = mProcessToTerminalIOQueue.read(mReceiveBuffer, false);
                if (bytesRead <= 0) break;
                noteRemoteOutput();
                mInEmulatorAppend = true;
                try {
                    mEmulator.append(mReceiveBuffer, bytesRead);
                } finally {
                    mInEmulatorAppend = false;
                }
                notifyScreenUpdate();
            }
        }
    }
}
