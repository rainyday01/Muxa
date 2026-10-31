package com.termux.view;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;

/**
 * Holds the last complete frame over a grid resize until the remote side has
 * repainted, then swaps in one step.
 *
 * <p>Field history (PageCurlAnimator, 0.2.x–0.3.16): a fixed 160/720ms hold and
 * an 840ms fade. The remote redraw lands one round trip after WINCH, so short
 * holds exposed tmux's partial paints, and the long fade showed two layouts at
 * once. The old sheet was drawn top-aligned while tmux re-lays the pane out
 * from the bottom, so the two layers were also ~30 rows apart. That
 * double exposure is the half-screen "text jitter" (log 0.3.16, 66⇄36 rows).
 *
 * <p>Now:
 * <ul>
 *   <li>The sheet stays opaque until remote output has gone quiet after the
 *       resize (or a cap), so only finished frames are ever shown.</li>
 *   <li>The sheet is shifted by whole rows the way tmux moves content: keep
 *       the bottom when the cursor sits near the bottom (agent TUIs, a full
 *       shell), else keep the cursor row visible. When the new frame arrives,
 *       unchanged lines are already on the same pixel rows.</li>
 *   <li>The swap is a short fade, too brief to read as a second layout.</li>
 * </ul>
 */
final class ResizeCover implements Choreographer.FrameCallback {

    /** Clock of the last remote output; 0 if none yet. */
    interface RemoteClock {
        long lastRemoteOutputAt();

        /** Smoothed keystroke→echo latency in ms; 0 if unknown. */
        long rttEstimateMs();
    }

    interface Listener {
        void onCoverReleased(String reason, long heldMs);
    }

    /** Remote output must stay quiet this long before the new frame counts as painted. */
    private static final long QUIET_MS = 70L;
    /** Lower bound on the wait for the first post-WINCH output, before RTT is known. */
    private static final long MIN_WAIT_MS = 110L;
    private static final long DEFAULT_RTT_MS = 180L;
    /** Give up and show the live grid even if the remote keeps streaming. */
    private static final long MAX_HOLD_MS = 650L;
    /** Sheet up but the grid never applied (no layout pass): drop it. */
    private static final long UNARMED_MAX_MS = 900L;
    private static final long FADE_MS = 90L;

    private final View mHost;
    private final Paint mBitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint mFillPaint = new Paint();
    private final Choreographer mChoreographer = Choreographer.getInstance();

    private RemoteClock mClock;
    private Listener mListener;

    private Bitmap mFrom;
    private int mFillColor = 0xFF000000;
    private boolean mActive;
    private boolean mPosted;

    /** Grid of the sheet, used to shift it by whole rows at the new height. */
    private int mOldRows;
    private int mLineSpacing;
    private int mTopPad;
    private boolean mKeepBottom;
    private int mCursorRow;

    private long mStartedAt;
    /** Uptime when the new grid (WINCH) was applied; 0 until then. */
    private long mArmedAt;
    private long mFadeStartAt;

    ResizeCover(View host) {
        mHost = host;
    }

    void setRemoteClock(RemoteClock clock) {
        mClock = clock;
    }

    void setListener(Listener listener) {
        mListener = listener;
    }

    boolean isActive() {
        return mActive && mFrom != null && !mFrom.isRecycled();
    }

    /**
     * Cover the view with [from] (painted at the old size).
     *
     * @param oldRows      rows painted in [from]
     * @param cursorRow    cursor row on screen in [from], or -1 if off screen
     * @param lineSpacing  renderer line height (px)
     * @param topPad       renderer top padding before row 0 (px)
     */
    void start(Bitmap from, int oldRows, int cursorRow, int lineSpacing, int topPad) {
        if (from == null || from.isRecycled() || from.getWidth() < 8 || from.getHeight() < 8) {
            return;
        }
        if (isActive() && mFadeStartAt == 0L) {
            // A second resize while still covering: keep the older, complete frame.
            // An armed wait keeps running; arm() restarts it if the grid changes again.
            from.recycle();
            if (mArmedAt == 0L) mStartedAt = SystemClock.uptimeMillis();
            post();
            mHost.invalidate();
            return;
        }
        cancel();
        mFrom = from;
        mOldRows = Math.max(1, oldRows);
        mCursorRow = cursorRow;
        mLineSpacing = Math.max(1, lineSpacing);
        mTopPad = Math.max(0, topPad);
        // Agent TUIs and a full shell keep their input at the bottom; tmux
        // also pulls history in from the top when the pane grows.
        mKeepBottom = cursorRow < 0 || cursorRow >= mOldRows - 8;
        mFillColor = sampleFill(from);
        mActive = true;
        mStartedAt = SystemClock.uptimeMillis();
        mArmedAt = 0L;
        mFadeStartAt = 0L;
        post();
        mHost.invalidate();
    }

    /** The new grid was applied (WINCH sent): start waiting for the remote repaint. */
    void arm() {
        if (!isActive() || mFadeStartAt > 0L) return;
        mArmedAt = SystemClock.uptimeMillis();
        post();
    }

    /**
     * Grid did not change after all: nothing to wait for. Ignored while an
     * earlier resize is still waiting for its repaint.
     */
    void releaseNow(String reason) {
        if (!isActive() || mArmedAt > 0L) return;
        beginFade(reason, SystemClock.uptimeMillis());
    }

    void cancel() {
        if (mPosted) {
            mChoreographer.removeFrameCallback(this);
            mPosted = false;
        }
        recycle();
        mActive = false;
        mArmedAt = 0L;
        mFadeStartAt = 0L;
    }

    private void post() {
        if (mPosted) return;
        mPosted = true;
        mChoreographer.postFrameCallback(this);
    }

    private void recycle() {
        if (mFrom != null && !mFrom.isRecycled()) {
            mFrom.recycle();
        }
        mFrom = null;
    }

    private static int sampleFill(Bitmap bmp) {
        try {
            int y = Math.max(0, bmp.getHeight() - 1);
            int x = Math.min(4, bmp.getWidth() - 1);
            return bmp.getPixel(x, y) | 0xFF000000;
        } catch (Throwable ignored) {
            return 0xFF000000;
        }
    }

    private long waitForFirstOutputMs() {
        long rtt = mClock != null ? mClock.rttEstimateMs() : 0L;
        if (rtt <= 0L) rtt = DEFAULT_RTT_MS;
        return Math.max(MIN_WAIT_MS, Math.min(rtt + 40L, MAX_HOLD_MS - QUIET_MS));
    }

    private void beginFade(String reason, long now) {
        if (mFadeStartAt > 0L) return;
        mFadeStartAt = now;
        if (mListener != null) mListener.onCoverReleased(reason, now - mStartedAt);
        post();
        mHost.invalidate();
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        mPosted = false;
        if (!isActive()) return;
        final long now = SystemClock.uptimeMillis();

        if (mFadeStartAt > 0L) {
            if (now - mFadeStartAt >= FADE_MS) {
                mActive = false;
                recycle();
                mHost.invalidate();
                return;
            }
            mHost.invalidate();
            post();
            return;
        }

        if (mArmedAt == 0L) {
            if (now - mStartedAt >= UNARMED_MAX_MS) beginFade("unarmed", now);
            else post();
            return;
        }

        final long sinceArm = now - mArmedAt;
        final long lastOut = mClock != null ? mClock.lastRemoteOutputAt() : 0L;
        final boolean repainted = lastOut > mArmedAt;
        if (repainted && now - lastOut >= QUIET_MS && sinceArm >= QUIET_MS) {
            beginFade("quiet", now);
        } else if (!repainted && sinceArm >= waitForFirstOutputMs() + QUIET_MS) {
            // Nothing came back: the remote frame did not change.
            beginFade("no_output", now);
        } else if (sinceArm >= MAX_HOLD_MS) {
            beginFade("max", now);
        } else {
            post();
        }
    }

    void draw(Canvas canvas) {
        if (!isActive()) return;
        final int vw = canvas.getWidth();
        final int vh = canvas.getHeight();
        final int bh = mFrom.getHeight();

        int alpha = 255;
        if (mFadeStartAt > 0L) {
            float t = (SystemClock.uptimeMillis() - mFadeStartAt) / (float) FADE_MS;
            alpha = Math.round(255f * (1f - Math.min(1f, Math.max(0f, t))));
            if (alpha <= 0) return;
        }

        final int newRows = Math.max(1, (vh - mTopPad) / mLineSpacing);
        final int shiftRows;
        if (mKeepBottom) {
            shiftRows = newRows - mOldRows;
        } else {
            // tmux drops lines below the cursor first; keep the cursor row on screen.
            shiftRows = Math.min(0, newRows - 1 - mCursorRow);
        }
        final int y = shiftRows * mLineSpacing;

        mFillPaint.setColor(mFillColor);
        mFillPaint.setAlpha(alpha);
        if (y > 0) canvas.drawRect(0f, 0f, vw, y, mFillPaint);
        if (y + bh < vh) canvas.drawRect(0f, y + bh, vw, vh, mFillPaint);
        if (mFrom.getWidth() < vw) canvas.drawRect(mFrom.getWidth(), 0f, vw, vh, mFillPaint);

        mBitmapPaint.setAlpha(alpha);
        canvas.drawBitmap(mFrom, 0f, y, mBitmapPaint);
        mBitmapPaint.setAlpha(255);
    }
}
