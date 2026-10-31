package com.termux.view;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Freeze the live terminal as a bitmap and pinch-zoom / pan it like a photo.
 * Content scroll stays locked until scale returns to 1 (pinch-out or double-tap).
 * Same path for Grok / Claude / Codex / shell — no WINCH, no font resize.
 */
final class ViewportZoom {
    static final float MIN = 1f;
    static final float MAX = 4.5f;
    static final float ACTIVE = 1.04f;
    static final float SNAP_RESET = 1.08f;

    private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private Bitmap mBitmap;
    private float mScale = 1f;
    private float mTx;
    private float mTy;
    private int mViewW;
    private int mViewH;
    private boolean mActive;
    /** True once the user has actually pinched past a real zoom. */
    private boolean mHasZoomed;

    boolean isActive() {
        return mActive && mBitmap != null && !mBitmap.isRecycled();
    }

    boolean isHolding() {
        return isActive();
    }

    boolean begin(Bitmap snap, int viewW, int viewH) {
        if (snap == null || snap.isRecycled() || viewW < 8 || viewH < 8) return false;
        recycle();
        mBitmap = snap;
        mViewW = viewW;
        mViewH = viewH;
        mScale = 1f;
        mTx = 0f;
        mTy = 0f;
        mActive = true;
        mHasZoomed = false;
        return true;
    }

    void applyScale(float focusX, float focusY, float factor) {
        if (!mActive || mBitmap == null || mScale <= 0f) return;
        // Xiaomi sends many 1.01–1.03 ticks. Old code reset whenever
        // scale <= 1.04, so the first tick killed the freeze (no visible zoom).
        float next = clamp(mScale * factor, MIN, MAX);
        float used = next / mScale;
        if (used <= 0f || Float.isNaN(used)) return;
        mScale = next;
        mTx = focusX - (focusX - mTx) * used;
        mTy = focusY - (focusY - mTy) * used;
        if (mScale >= 1.10f) mHasZoomed = true;
        clampPan();
        if (mHasZoomed && mScale <= 1.03f) {
            reset();
        }
    }

    void pan(float dx, float dy) {
        if (!isActive()) return;
        mTx += dx;
        mTy += dy;
        clampPan();
    }

    /** True if we snapped back to 1:1 (caller should unlock content scroll). */
    boolean endPinch() {
        if (!mActive) return true;
        if (!mHasZoomed || mScale <= SNAP_RESET) {
            reset();
            return true;
        }
        clampPan();
        return false;
    }

    void reset() {
        recycle();
        mScale = 1f;
        mTx = 0f;
        mTy = 0f;
        mActive = false;
        mHasZoomed = false;
    }

    void draw(Canvas canvas) {
        if (!isHolding()) return;
        canvas.save();
        canvas.translate(mTx, mTy);
        canvas.scale(mScale, mScale);
        canvas.drawBitmap(mBitmap, 0f, 0f, mPaint);
        canvas.restore();
    }

    private void clampPan() {
        if (mBitmap == null) return;
        float sw = mViewW * mScale;
        float sh = mViewH * mScale;
        float minTx = Math.min(0f, mViewW - sw);
        float minTy = Math.min(0f, mViewH - sh);
        mTx = clamp(mTx, minTx, 0f);
        mTy = clamp(mTy, minTy, 0f);
    }

    private void recycle() {
        if (mBitmap != null && !mBitmap.isRecycled()) {
            mBitmap.recycle();
        }
        mBitmap = null;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
