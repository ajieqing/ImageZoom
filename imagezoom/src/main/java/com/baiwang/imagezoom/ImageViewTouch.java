package com.baiwang.imagezoom;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.OnScaleGestureListener;
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener;
import android.view.ViewConfiguration;

public class ImageViewTouch extends ImageViewTouchBase {
    static final float SCROLL_DELTA_THRESHOLD = 1.0f;
    private Boolean IsDoubleModel;
    protected int mDoubleTapDirection;
    protected boolean mDoubleTapEnabled;
    private OnImageViewTouchDoubleTapListener mDoubleTapListener;
    protected GestureDetector mGestureDetector;
    protected OnGestureListener mGestureListener;
    protected ScaleGestureDetector mScaleDetector;
    protected boolean mScaleEnabled;
    protected float mScaleFactor;
    protected OnScaleGestureListener mScaleListener;
    protected boolean mScrollEnabled;
    private OnImageViewTouchSingleTapListener mSingleTapListener;
    protected int mTouchSlop;

    public class GestureListener extends SimpleOnGestureListener {
        public boolean onSingleTapConfirmed(MotionEvent motionEvent) {
            if (ImageViewTouch.this.mSingleTapListener != null) {
                ImageViewTouch.this.mSingleTapListener.onSingleTapConfirmed();
            }
            return ImageViewTouch.this.onSingleTapConfirmed();
        }

        public boolean onDoubleTap(MotionEvent motionEvent) {
            String stringBuilder = "onDoubleTap. double tap enabled? " +
                    ImageViewTouch.this.mDoubleTapEnabled;
            Log.i(ImageViewTouchBase.LOG_TAG, stringBuilder);
            if (ImageViewTouch.this.mDoubleTapEnabled) {
                ImageViewTouch.this.mUserScaled = true;
                ImageViewTouch.this.zoomTo(Math.min(ImageViewTouch.this.getMaxScale(), Math.max(ImageViewTouch.this.onDoubleTapPost(ImageViewTouch.this.getScale(), ImageViewTouch.this.getMaxScale()), ImageViewTouch.this.getMinScale())), motionEvent.getX(), motionEvent.getY(), 200.0f);
                ImageViewTouch.this.invalidate();
            }
            if (ImageViewTouch.this.mDoubleTapListener != null) {
                ImageViewTouch.this.mDoubleTapListener.onDoubleTap();
            }
            return super.onDoubleTap(motionEvent);
        }

        public void onLongPress(MotionEvent motionEvent) {
            if (ImageViewTouch.this.isLongClickable() && ImageViewTouch.this.mScaleDetector != null) {
                try {
                    if (!ImageViewTouch.this.mScaleDetector.isInProgress()) {
                        ImageViewTouch.this.setPressed(true);
                        ImageViewTouch.this.performLongClick();
                    }
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
        }


        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                float distanceX, float distanceY) {
            if (!mScrollEnabled)
                return false;

            if (e1 == null || e2 == null)
                return false;
            if (e1.getPointerCount() > 1 || e2.getPointerCount() > 1)
                return false;
            if (mScaleDetector.isInProgress())
                return false;
            scrollBy(-distanceX, -distanceY);
            invalidate();
            return super.onScroll(e1, e2, distanceX, distanceY);
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                               float velocityY) {
            if (!mScrollEnabled)
                return false;

            try {

                if (e1 == null || e2 == null) return false;
                if (e1.getPointerCount() > 1 || e2.getPointerCount() > 1)
                    return false;
                if (mScaleDetector.isInProgress())
                    return false;
            } catch (Exception e) {
                return false;
            }
            if (e1 == null || e2 == null) return false;
            if (e1.getPointerCount() > 1 || e2.getPointerCount() > 1)
                return false;
            if (mScaleDetector.isInProgress())
                return false;

            float diffX = e2.getX() - e1.getX();
            float diffY = e2.getY() - e1.getY();

            if (Math.abs(velocityX) > 800 || Math.abs(velocityY) > 800) {
                scrollBy(diffX / 2, diffY / 2, 300);
                invalidate();
            }
            return super.onFling(e1, e2, velocityX, velocityY);
        }

        public boolean onSingleTapUp(MotionEvent motionEvent) {
            return ImageViewTouch.this.onSingleTapUp();
        }

        public boolean onDown(MotionEvent motionEvent) {
            return ImageViewTouch.this.onDown();
        }
    }

    public interface OnImageViewTouchDoubleTapListener {
        void onDoubleTap();
    }

    public interface OnImageViewTouchSingleTapListener {
        boolean onSingleTapConfirmed();
    }

    public class ScaleListener extends SimpleOnScaleGestureListener {
        protected boolean mScaled = false;

        public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
            float currentSpan = scaleGestureDetector.getCurrentSpan() - scaleGestureDetector.getPreviousSpan();
            float scale = ImageViewTouch.this.getScale() * scaleGestureDetector.getScaleFactor();
            if (ImageViewTouch.this.mScaleEnabled) {
                if (this.mScaled && currentSpan != 0.0f) {
                    ImageViewTouch.this.mUserScaled = true;
                    currentSpan = Math.min(ImageViewTouch.this.getMaxScale(), Math.max(scale, ImageViewTouch.this.getMinScale() - 0.1f));
                    if (currentSpan > ImageViewTouch.this.getMaxScale()) {
                        currentSpan = ImageViewTouch.this.getMaxScale();
                    }
                    ImageViewTouch.this.postScale(currentSpan / ImageViewTouch.this.getScale(), scaleGestureDetector.getFocusX(), scaleGestureDetector.getFocusY());
                    String stringBuilder = " targetScale " +
                            currentSpan;
                    Log.i("MyData", stringBuilder);
                    ImageViewTouch.this.mDoubleTapDirection = 1;
                    ImageViewTouch.this.invalidate();
                    return true;
                } else if (!this.mScaled) {
                    this.mScaled = true;
                }
            }
            return true;
        }

        public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
            ImageViewTouch.this.IsDoubleModel = Boolean.TRUE;
            return true;
        }
    }

    public boolean onDown() {
        return true;
    }

    public boolean onFling() {
        return false;
    }

    public boolean onSingleTapConfirmed() {
        return true;
    }

    public boolean onSingleTapUp() {
        return true;
    }

    public ImageViewTouch(Context context) {
        super(context);
        this.mDoubleTapEnabled = true;
        this.mScaleEnabled = true;
        this.mScrollEnabled = true;
        this.IsDoubleModel = Boolean.FALSE;
    }

    public ImageViewTouch(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public ImageViewTouch(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        this.mDoubleTapEnabled = true;
        this.mScaleEnabled = true;
        this.mScrollEnabled = true;
        this.IsDoubleModel = Boolean.FALSE;
    }

    protected void init(Context context, AttributeSet attributeSet, int i) {
        super.init(context, attributeSet, i);
        this.mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        this.mGestureListener = getGestureListener();
        this.mScaleListener = getScaleListener();
        this.mScaleDetector = new ScaleGestureDetector(getContext(), this.mScaleListener);
        this.mGestureDetector = new GestureDetector(getContext(), this.mGestureListener, null, true);
        this.mDoubleTapDirection = 1;
    }

    public void setDoubleTapListener(OnImageViewTouchDoubleTapListener onImageViewTouchDoubleTapListener) {
        this.mDoubleTapListener = onImageViewTouchDoubleTapListener;
    }

    public void setSingleTapListener(OnImageViewTouchSingleTapListener onImageViewTouchSingleTapListener) {
        this.mSingleTapListener = onImageViewTouchSingleTapListener;
    }

    public void setDoubleTapEnabled(boolean z) {
        this.mDoubleTapEnabled = z;
    }

    public void setScaleEnabled(boolean z) {
        this.mScaleEnabled = z;
    }

    public void setScrollEnabled(boolean z) {
        this.mScrollEnabled = z;
    }

    public boolean getDoubleTapEnabled() {
        return this.mDoubleTapEnabled;
    }

    protected OnGestureListener getGestureListener() {
        return new GestureListener();
    }

    protected OnScaleGestureListener getScaleListener() {
        return new ScaleListener();
    }

    protected void _setImageDrawable(Drawable drawable, Matrix matrix, float f, float f2) {
        super._setImageDrawable(drawable, matrix, f, f2);
        this.mScaleFactor = getMaxScale() / 3.0f;
    }

    public boolean onTouchEvent(MotionEvent motionEvent) {
        this.mScaleDetector.onTouchEvent(motionEvent);
        if (!this.mScaleDetector.isInProgress()) {
            this.mGestureDetector.onTouchEvent(motionEvent);
        }
        if ((motionEvent.getAction() & 255) != 1) {
            return true;
        }
        this.IsDoubleModel = Boolean.FALSE;
        return onUp();
    }

    protected void onZoomAnimationCompleted(float f) {
        if (f < getMinScale()) {
            zoomTo(getMinScale(), 50.0f);
        }
    }

    protected float onDoubleTapPost(float f, float f2) {
        if (this.mDoubleTapDirection != 1) {
            this.mDoubleTapDirection = 1;
            return SCROLL_DELTA_THRESHOLD;
        } else if ((this.mScaleFactor * 2.0f) + f <= f2) {
            return f + this.mScaleFactor;
        } else {
            this.mDoubleTapDirection = -1;
            return f2;
        }
    }

    public boolean onScroll(float f, float f2) {
        this.mUserScaled = true;
        scrollBy(-f, -f2);
        invalidate();
        return true;
    }

    public boolean onUp() {
        if (getScale() < getMinScale()) {
            zoomTo(getMinScale(), 50.0f);
        }
        return true;
    }

    public boolean canScroll(int i) {
        RectF bitmapRect = getBitmapRect();
        updateRect(bitmapRect, this.mScrollRect);
        Rect rect = new Rect();
        getGlobalVisibleRect(rect);
        boolean z = false;
        if (bitmapRect == null) {
            return false;
        }
        if (bitmapRect.right < ((float) rect.right) || i >= 0) {
            if (((double) Math.abs(bitmapRect.left - this.mScrollRect.left)) > 1.0d) {
                z = true;
            }
            return z;
        }
        if (Math.abs(bitmapRect.right - ((float) rect.right)) > SCROLL_DELTA_THRESHOLD) {
            z = true;
        }
        return z;
    }
}
