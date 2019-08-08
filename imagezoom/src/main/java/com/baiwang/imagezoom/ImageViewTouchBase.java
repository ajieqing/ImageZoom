package com.baiwang.imagezoom;


import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

import com.baiwang.imagezoom.easing.Cubic;
import com.baiwang.imagezoom.easing.Easing;
import com.baiwang.imagezoom.graphics.FastBitmapDrawable;
import com.baiwang.imagezoom.utils.IDisposable;

/**
 * Base View to manage image zoom/scrool/pinch operations
 *
 * @author alessandro
 */
public abstract class ImageViewTouchBase extends ImageView implements IDisposable {


    //static final String TAG = "ImageViewTouchBase";

    interface OnDrawableChangeListener {

        /**
         * BS_Callback invoked when a new drawable has been
         * assigned to the view
         *
         * @param drawable
         */
        void onDrawableChanged(Drawable drawable);
    }

    interface OnLayoutChangeListener {
        /**
         * BS_Callback invoked when the layout bounds changed
         *
         * @param changed
         * @param left
         * @param top
         * @param right
         * @param bottom
         */
        void onLayoutChanged(boolean changed, int left, int top, int right, int bottom);
    }

    /**
     * Use this to change the {@link ImageViewTouchBase#setDisplayType(DisplayType)} of
     * this View
     *
     * @author alessandro
     */
    public enum DisplayType {
        /**
         * Image is not scaled by default
         */
        NONE,
        /**
         * Image will be always presented using this view's bounds
         */
        FIT_TO_SCREEN,
        /**
         * Image will be scaled only if bigger than the bounds of this view
         */
        FIT_IF_BIGGER,

        FILL_TO_SCREEN
    }


    static final String LOG_TAG = "ImageViewTouchBase";
    static final boolean LOG_ENABLED = false;

    private static final float ZOOM_INVALID = -1f;

    //add more attitude
    protected final PointF mStart = new PointF();
    protected final PointF mMid = new PointF();
    private static final int NONE = 0;
    protected static final int DRAG = 1;
    protected static final int JUMP = 2;
    protected static final int ZOOM = 3;
    protected int mode = NONE;
    protected float oldDist;
    protected float oldDegree;

    private final Easing mEasing = new Cubic();
    private final Matrix mBaseMatrix = new Matrix();
    Matrix mSuppMatrix = new Matrix();
    private Matrix mNextMatrix;
    private final Handler mHandler = new Handler();
    private Runnable mLayoutRunnable = null;
    protected boolean mUserScaled = false;

    private float mMaxZoom = ZOOM_INVALID;
    private float mMinZoom = ZOOM_INVALID;

    // true when min and max zoom are explicitly defined
    private boolean mMaxZoomDefined;
    private boolean mMinZoomDefined;

    private final Matrix mDisplayMatrix = new Matrix();
    private final float[] mMatrixValues = new float[9];

    int mThisWidth = -1;
    int mThisHeight = -1;
    private final PointF mCenter = new PointF();

    private DisplayType mScaleType = DisplayType.NONE;
    private boolean mScaleTypeChanged;
    private boolean mBitmapChanged;

    final protected int DEFAULT_ANIMATION_DURATION = 200;

    private final RectF mBitmapRect = new RectF();
    private final RectF mCenterRect = new RectF();
    protected final RectF mScrollRect = new RectF();

    private OnDrawableChangeListener mDrawableChangeListener;
    private OnLayoutChangeListener mOnLayoutChangeListener;

    private float mEldScale;

    public ImageViewTouchBase(Context context) {
        this(context, null);
    }

    public ImageViewTouchBase(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ImageViewTouchBase(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle);
    }

    public void setOnDrawableChangedListener(OnDrawableChangeListener listener) {
        mDrawableChangeListener = listener;
    }

    public void setOnLayoutChangeListener(OnLayoutChangeListener listener) {
        mOnLayoutChangeListener = listener;
    }

    protected void init(Context context, AttributeSet attrs, int defStyle) {
        setScaleType(ScaleType.MATRIX);
    }

    @Override
    public void setScaleType(ScaleType scaleType) {
        if (scaleType == ScaleType.MATRIX) {
            super.setScaleType(scaleType);
        } else {
            Log.w(LOG_TAG, "Unsupported scaletype. Only MATRIX can be used");
        }
    }

    public void resetDisplayMatrix() {
        mSuppMatrix.reset();
    }

    private final PaintFlagsDrawFilter paintFlagsDrawFilter = new PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    @Override
    public void onDraw(Canvas canvas) {
        canvas.setDrawFilter(paintFlagsDrawFilter);
        super.onDraw(canvas);
    }

    /**
     * Clear the current drawable
     */
    private void clear() {
        setImageBitmap(null);
    }

    /**
     * Change the display type
     */
    public void setDisplayType(DisplayType type) {
        if (type != mScaleType) {
            if (LOG_ENABLED) {
                Log.i(LOG_TAG, "setDisplayType: " + type);
            }
            mUserScaled = false;
            mScaleType = type;
            mScaleTypeChanged = true;
            requestLayout();
        }
    }

    public DisplayType getDisplayType() {
        return mScaleType;
    }

    protected void setMinScale(float value) {
        if (LOG_ENABLED) {
            Log.d(LOG_TAG, "setMinZoom: " + value);
        }

        mMinZoom = value;
    }

    protected void setMaxScale(float value) {
        if (LOG_ENABLED) {
            Log.d(LOG_TAG, "setMaxZoom: " + value);
        }
        mMaxZoom = value;
    }


    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {

        if (LOG_ENABLED) {
            Log.e(LOG_TAG, "onLayout: " + changed + ", bitmapChanged: " + mBitmapChanged + ", scaleChanged: " + mScaleTypeChanged);
        }

        super.onLayout(changed, left, top, right, bottom);

        int deltaX = 0;
        int deltaY = 0;

        if (changed) {
            int oldw = mThisWidth;
            int oldh = mThisHeight;

            mThisWidth = right - left;
            mThisHeight = bottom - top;

            deltaX = mThisWidth - oldw;
            deltaY = mThisHeight - oldh;

            // update center point
            mCenter.x = mThisWidth / 2f;
            mCenter.y = mThisHeight / 2f;
        }

        Runnable r = mLayoutRunnable;

        if (r != null) {
            mLayoutRunnable = null;
            r.run();
        }

        final Drawable drawable = getDrawable();

        if (drawable != null) {

            if (changed || mScaleTypeChanged || mBitmapChanged) {

                float scale = 1;

                // retrieve the old values
                float old_default_scale = getDefaultScale(mScaleType);
                float old_matrix_scale = getScale(mBaseMatrix);
                float old_scale = getScale();
                float old_min_scale = Math.min(1f, 1f / old_matrix_scale);

                getProperBaseMatrix(drawable, mBaseMatrix);

                float new_matrix_scale = getScale(mBaseMatrix);

                if (LOG_ENABLED) {
                    Log.d(LOG_TAG, "old matrix scale: " + old_matrix_scale);
                    Log.d(LOG_TAG, "new matrix scale: " + new_matrix_scale);
                    Log.d(LOG_TAG, "old min scale: " + old_min_scale);
                    Log.d(LOG_TAG, "old scale: " + old_scale);
                }

                // 1. bitmap changed or scaletype changed
                if (mBitmapChanged || mScaleTypeChanged) {

                    if (LOG_ENABLED) {
                        Log.d(LOG_TAG, "display type: " + mScaleType);
                        Log.d(LOG_TAG, "newMatrix: " + mNextMatrix);
                    }

                    if (mNextMatrix != null) {
                        mSuppMatrix.set(mNextMatrix);
                        mNextMatrix = null;
                        scale = getScale();
                    } else {
                        mSuppMatrix.reset();
                        scale = getDefaultScale(mScaleType);
                    }

                    setImageMatrix(getImageViewMatrix());

                    if (scale != getScale()) {
                        zoomTo(scale);
                    }

                } else if (changed) {

                    // 2. layout size changed

                    if (!mMinZoomDefined) mMinZoom = ZOOM_INVALID;
                    if (!mMaxZoomDefined) mMaxZoom = ZOOM_INVALID;

                    setImageMatrix(getImageViewMatrix());
                    postTranslate(-deltaX, -deltaY);


                    if (!mUserScaled) {
                        scale = getDefaultScale(mScaleType);
                        zoomTo(scale);
                    } else {
                        if (Math.abs(old_scale - old_min_scale) > 0.001) {
                            scale = (old_matrix_scale / new_matrix_scale) * old_scale;
                        }
                        zoomTo(scale);
                    }

                    if (LOG_ENABLED) {
                        Log.d(LOG_TAG, "old min scale: " + old_default_scale);
                        Log.d(LOG_TAG, "old scale: " + old_scale);
                        Log.d(LOG_TAG, "new scale: " + scale);
                    }


                }

                mUserScaled = false;

                if (scale > getMaxScale() || scale < getMinScale()) {
                    // if current scale if outside the min/max bounds
                    // then restore the correct scale
                    zoomTo(scale);
                }

                center();

                if (mBitmapChanged) onDrawableChanged(drawable);
                if (changed || mBitmapChanged || mScaleTypeChanged)
                    onLayoutChanged(left, top, right, bottom);

                if (mScaleTypeChanged) mScaleTypeChanged = false;
                if (mBitmapChanged) mBitmapChanged = false;

                if (LOG_ENABLED) {
                    Log.d(LOG_TAG, "new scale: " + getScale());
                }
            }
        } else {
            // drawable is null
            if (mBitmapChanged) onDrawableChanged(drawable);
            if (changed || mBitmapChanged || mScaleTypeChanged)
                onLayoutChanged(left, top, right, bottom);

            if (mBitmapChanged) mBitmapChanged = false;
            if (mScaleTypeChanged) mScaleTypeChanged = false;

        }

        this.printMatrix(this.mSuppMatrix);
    }

    /**
     * Restore the original display
     */
    public void resetDisplay() {
        mBitmapChanged = true;
        requestLayout();
    }

    protected void resetMatrix() {
        if (LOG_ENABLED) {
            Log.i(LOG_TAG, "resetMatrix");
        }
        mSuppMatrix = new Matrix();

        float scale = getDefaultScale(mScaleType);
        setImageMatrix(getImageViewMatrix());

        if (LOG_ENABLED) {
            Log.d(LOG_TAG, "default scale: " + scale + ", scale: " + getScale());
        }

        if (scale != getScale()) {
            zoomTo(scale);
        }

        postInvalidate();
    }

    private float getDefaultScale(DisplayType type) {
        if (type == DisplayType.FIT_TO_SCREEN) {
            // always fit to screen
            return 1f;
        } else if (type == DisplayType.FIT_IF_BIGGER) {
            // normal scale if smaller, fit to screen otherwise
            return Math.min(1f, 1f / getScale(mBaseMatrix));
        } else if (type == DisplayType.FILL_TO_SCREEN) {
            // normal scale if smaller, fit to screen otherwise
            float scale = (float) getDrawable().getIntrinsicWidth() / getDrawable().getIntrinsicHeight();
            if (scale < 1f)
                scale = 1f / scale;

            return scale;
        } else {
            // no scale
            return 1f / getScale(mBaseMatrix);
        }
    }

    @Override
    public void setImageResource(int resId) {
        setImageDrawable(getContext().getResources().getDrawable(resId));
    }

    /**
     * {@inheritDoc} Set the new image to display and reset the internal matrix.
     *
     * @param bitmap the {@link Bitmap} to display
     * @see {@link ImageView#setImageBitmap(Bitmap)}
     */
    @Override
    public void setImageBitmap(Bitmap bitmap) {
        setImageBitmap(bitmap, null);
    }


    public void setImageBitmapWithStatKeep(Bitmap bitmap) {
        if (bitmap == null) {
            //super.setImageBitmap(null);
            super.setImageDrawable(null);
        } else {
            Drawable d = new FastBitmapDrawable(bitmap);
            super.setImageDrawable(d);
            //Matrix current = getImageMatrix();
            //super.setImageMatrix( current );
        }


//		boolean needUpdate = false;
//		
//		if ( matrix == null && !current.isIdentity() || matrix != null && !current.equals( matrix ) ) {
//			needUpdate = true;
//		}
        //onImageMatrixChanged();

        //mBitmapChanged = true;
        //requestLayout();
    }
//	public void setImageBitmap2(Bitmap bitmap){
//		setImageBitmap( bitmap, this.getImageViewMatrix(), ZOOM_INVALID, ZOOM_INVALID );
//		mBitmapChanged = true;
//		requestLayout();
//	}

    /**
     * @param bitmap
     * @param matrix
     * @see #setImageDrawable(Drawable, Matrix, float, float)
     */
    private void setImageBitmap(final Bitmap bitmap, Matrix matrix) {
        if (bitmap != null)
            setImageDrawable(new FastBitmapDrawable(bitmap), matrix, ImageViewTouchBase.ZOOM_INVALID, ImageViewTouchBase.ZOOM_INVALID);
        else
            setImageDrawable(null, matrix, ImageViewTouchBase.ZOOM_INVALID, ImageViewTouchBase.ZOOM_INVALID);
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        setImageDrawable(drawable, null, ZOOM_INVALID, ZOOM_INVALID);
    }

    /**
     * Note: if the scaleType is FitToScreen then min_zoom must be <= 1 and max_zoom must be >= 1
     *
     * @param drawable       the new drawable
     * @param initial_matrix the optional initial display matrix
     * @param min_zoom       the optional minimum scale, pass {@link #ZOOM_INVALID} to use the default min_zoom
     * @param max_zoom       the optional maximum scale, pass {@link #ZOOM_INVALID} to use the default max_zoom
     */
    private void setImageDrawable(final Drawable drawable, final Matrix initial_matrix, final float min_zoom, final float max_zoom) {

        final int viewWidth = getWidth();

        if (viewWidth <= 0) {
            mLayoutRunnable = () -> setImageDrawable(drawable, initial_matrix, min_zoom, max_zoom);
            return;
        }
        _setImageDrawable(drawable, initial_matrix, min_zoom, max_zoom);
    }

    protected void _setImageDrawable(final Drawable drawable, final Matrix initial_matrix, float min_zoom, float max_zoom) {

        if (LOG_ENABLED) {
            Log.i(LOG_TAG, "_setImageDrawable");
        }

        if (drawable != null) {

            if (LOG_ENABLED) {
                Log.d(LOG_TAG, "size: " + drawable.getIntrinsicWidth() + "x" + drawable.getIntrinsicHeight());
            }
            super.setImageDrawable(drawable);
        } else {
            mBaseMatrix.reset();
            super.setImageDrawable(null);
        }

        if (initial_matrix != null) {
            mNextMatrix = new Matrix(initial_matrix);
        }

        mBitmapChanged = true;
        requestLayout();
    }

    /**
     * Fired as soon as a new Bitmap has been set
     *
     * @param drawable
     */
    private void onDrawableChanged(final Drawable drawable) {
        if (LOG_ENABLED) {
            Log.i(LOG_TAG, "onDrawableChanged");
        }
        fireOnDrawableChangeListener(drawable);
    }

    private void fireOnLayoutChangeListener(int left, int top, int right, int bottom) {
        if (null != mOnLayoutChangeListener) {
            mOnLayoutChangeListener.onLayoutChanged(true, left, top, right, bottom);
        }
    }

    private void fireOnDrawableChangeListener(Drawable drawable) {
        if (null != mDrawableChangeListener) {
            mDrawableChangeListener.onDrawableChanged(drawable);
        }
    }

    /**
     * Called just after {@link #onLayout(boolean, int, int, int, int)}
     * if the view's bounds has changed or a new Drawable has been set
     * or the {@link DisplayType} has been modified
     *
     * @param left
     * @param top
     * @param right
     * @param bottom
     */
    protected void onLayoutChanged(int left, int top, int right, int bottom) {
        if (LOG_ENABLED) {
            Log.i(LOG_TAG, "onLayoutChanged");
        }
        fireOnLayoutChangeListener(left, top, right, bottom);
    }

    private float computeMaxZoom() {
        final Drawable drawable = getDrawable();

        if (drawable == null) {
            return 1F;
        }

        float fw = (float) drawable.getIntrinsicWidth() / (float) mThisWidth;
        float fh = (float) drawable.getIntrinsicHeight() / (float) mThisHeight;
        float scale = Math.max(fw, fh) * 8;

        if (LOG_ENABLED) {
            Log.i(LOG_TAG, "computeMaxZoom: " + scale);
        }
        return scale;
    }

    private float computeMinZoom() {
        final Drawable drawable = getDrawable();

        if (drawable == null) {
            return 1F;
        }

        float scale = getScale(mBaseMatrix);
        scale = Math.min(1f, 1f / scale);

        if (LOG_ENABLED) {
            Log.i(LOG_TAG, "computeMinZoom: " + scale);
        }

        return scale;
    }

    /**
     * Returns the current maximum allowed image scale
     *
     * @return
     */
    protected float getMaxScale() {
        if (mMaxZoom == ZOOM_INVALID) {
            mMaxZoom = computeMaxZoom();
        }


        return mMaxZoom;
    }

    /**
     * Returns the current minimum allowed image scale
     *
     * @return
     */
    protected float getMinScale() {
        if (mMinZoom == ZOOM_INVALID) {
            mMinZoom = computeMinZoom();
        }
        return mMinZoom;
    }

    /**
     * Returns the current view matrix
     *
     * @return
     */
    public Matrix getImageViewMatrix() {
        return getImageViewMatrix(mSuppMatrix);
    }

    private Matrix getImageViewMatrix(Matrix supportMatrix) {
        mDisplayMatrix.set(mBaseMatrix);
        mDisplayMatrix.postConcat(supportMatrix);
        return mDisplayMatrix;
    }

    @Override
    public void setImageMatrix(Matrix matrix) {

        Matrix current = getImageMatrix();
        boolean needUpdate = false;

        if (matrix == null && !current.isIdentity() || matrix != null && !current.equals(matrix)) {
            needUpdate = true;
        }

        super.setImageMatrix(matrix);

        if (needUpdate) onImageMatrixChanged();
    }

    /**
     * Called just after a new Matrix has been assigned.
     *
     * @see {@link #setImageMatrix(Matrix)}
     */
    private void onImageMatrixChanged() {
    }

    /**
     * Returns the current image display matrix.<br />
     * This matrix can be used in the next call to the {@link #setImageDrawable(Drawable, Matrix, float, float)} to restore the same
     * view state of the previous {@link Bitmap}.<br />
     * Example:
     *
     * <pre>
     * Matrix currentMatrix = mImageView.getDisplayMatrix();
     * mImageView.setImageBitmap( newBitmap, currentMatrix, ZOOM_INVALID, ZOOM_INVALID );
     * </pre>
     *
     * @return the current support matrix
     */
    public Matrix getDisplayMatrix() {
        return new Matrix(mSuppMatrix);
    }

    /**
     * Setup the base matrix so that the image is centered and scaled properly.
     *
     * @param drawable
     * @param matrix
     */
    private void getProperBaseMatrix(Drawable drawable, Matrix matrix) {
        float viewWidth = mThisWidth;
        float viewHeight = mThisHeight;

        if (LOG_ENABLED) {
            Log.d(LOG_TAG, "getProperBaseMatrix. view: " + viewWidth + "x" + viewHeight);
        }

        float w = drawable.getIntrinsicWidth();
        float h = drawable.getIntrinsicHeight();
        float widthScale, heightScale;
        matrix.reset();

        if (w > viewWidth || h > viewHeight) {
            widthScale = viewWidth / w;
            heightScale = viewHeight / h;
            float scale = Math.min(widthScale, heightScale);
            matrix.postScale(scale, scale);

            float tw = (viewWidth - w * scale) / 2.0f;
            float th = (viewHeight - h * scale) / 2.0f;
            matrix.postTranslate(tw, th);

        } else {
            widthScale = viewWidth / w;
            heightScale = viewHeight / h;
            float scale = Math.min(widthScale, heightScale);
            matrix.postScale(scale, scale);

            float tw = (viewWidth - w * scale) / 2.0f;
            float th = (viewHeight - h * scale) / 2.0f;
            matrix.postTranslate(tw, th);
        }

        if (LOG_ENABLED) {
            printMatrix(matrix);
        }
    }

    /**
     * Setup the base matrix so that the image is centered and scaled properly.
     *
     * @param bitmap
     * @param matrix
     */
    protected void getProperBaseMatrix2(Drawable bitmap, Matrix matrix) {

        float viewWidth = mThisWidth;
        float viewHeight = mThisHeight;

        float w = bitmap.getIntrinsicWidth();
        float h = bitmap.getIntrinsicHeight();

        matrix.reset();

        float widthScale = viewWidth / w;
        float heightScale = viewHeight / h;

        float scale = Math.min(widthScale, heightScale);

        matrix.postScale(scale, scale);
        matrix.postTranslate((viewWidth - w * scale) / 2.0f, (viewHeight - h * scale) / 2.0f);
    }

    private float getValue(Matrix matrix, int whichValue) {
        matrix.getValues(mMatrixValues);
        return mMatrixValues[whichValue];
    }

    void printMatrix(Matrix matrix) {
        float scalex = getValue(matrix, Matrix.MSCALE_X);
        float scaley = getValue(matrix, Matrix.MSCALE_Y);
        float tx = getValue(matrix, Matrix.MTRANS_X);
        float ty = getValue(matrix, Matrix.MTRANS_Y);
        Log.d(LOG_TAG, "matrix: { x: " + tx + ", y: " + ty + ", scalex: " + scalex + ", scaley: " + scaley + " }");
    }

    public RectF getBitmapRect() {
        return getBitmapRect(mSuppMatrix);
    }

    private RectF getBitmapRect(Matrix supportMatrix) {
        final Drawable drawable = getDrawable();

        if (drawable == null) return null;
        Matrix m = getImageViewMatrix(supportMatrix);
        mBitmapRect.set(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        m.mapRect(mBitmapRect);
        return mBitmapRect;
    }

    private float getScale(Matrix matrix) {
        return getValue(matrix, Matrix.MSCALE_X);
    }

    @SuppressLint("Override")
    public float getRotation() {
        return 0;
    }

    /**
     * Returns the current image scale
     *
     * @return
     */
    public float getScale() {
        float scale = getScale(mSuppMatrix);
        if (scale != 0) {
            mEldScale = scale;
        }

        Log.v(LOG_TAG, String.valueOf(mEldScale));
        return mEldScale;

        //return getScale( mSuppMatrix );
    }

    public float getBaseScale() {
        return getScale(mBaseMatrix);
    }

    private void center() {
        final Drawable drawable = getDrawable();
        if (drawable == null) return;

        RectF rect = getCenter(mSuppMatrix);

        if (rect.left != 0 || rect.top != 0) {

            if (LOG_ENABLED) {
                Log.i(LOG_TAG, "center");
            }
            postTranslate(rect.left, rect.top);
        }
    }

    private RectF getCenter(Matrix supportMatrix) {
        final Drawable drawable = getDrawable();

        if (drawable == null) return new RectF(0, 0, 0, 0);

        mCenterRect.set(0, 0, 0, 0);
        RectF rect = getBitmapRect(supportMatrix);
        float height = rect.height();
        float width = rect.width();
        float deltaX = 0, deltaY = 0;
        if (true) {
            int viewHeight = mThisHeight;
            if (height < viewHeight) {
                deltaY = (viewHeight - height) / 2 - rect.top;
            } else if (rect.top > 0) {
                deltaY = -rect.top;
            } else if (rect.bottom < viewHeight) {
                deltaY = mThisHeight - rect.bottom;
            }
        }
        if (true) {
            int viewWidth = mThisWidth;
            if (width < viewWidth) {
                deltaX = (viewWidth - width) / 2 - rect.left;
            } else if (rect.left > 0) {
                deltaX = -rect.left;
            } else if (rect.right < viewWidth) {
                deltaX = viewWidth - rect.right;
            }
        }
        mCenterRect.set(deltaX, deltaY, 0, 0);
        return mCenterRect;
    }

    protected void postTranslate(float deltaX, float deltaY) {
        if (deltaX != 0 || deltaY != 0) {
            if (LOG_ENABLED) {
                Log.i(LOG_TAG, "postTranslate: " + deltaX + "x" + deltaY);
            }
            mSuppMatrix.postTranslate(deltaX, deltaY);
            setImageMatrix(getImageViewMatrix());
        }


    }

    protected void postScale(float scale, float centerX, float centerY) {
        if (LOG_ENABLED) {
            Log.i(LOG_TAG, "postScale: " + scale + ", center: " + centerX + "x" + centerY);
        }
        mSuppMatrix.postScale(scale, scale, centerX, centerY);
        setImageMatrix(getImageViewMatrix());
    }

    public void postScale(float scale) {
        mSuppMatrix.postScale(scale, scale, this.getWidth() / 2, this.getHeight() / 2);
        setImageMatrix(getImageViewMatrix());
    }

    //add rotation
    protected void postRotation(float rotation, float width, float height) {
        mSuppMatrix.postRotate(rotation, width, height);
        setImageMatrix(getImageViewMatrix());
    }

    public void postRotation(float rotation) {
        mEldScale = this.getScale();
        mSuppMatrix.postRotate(rotation, this.getWidth() / 2, this.getHeight() / 2);
        setImageMatrix(getImageViewMatrix());
    }

    //add radius
    public void postRoundRadius(float xRadius, float yRadius) {
        FastBitmapDrawable fbd = (FastBitmapDrawable) this.getDrawable();
        fbd.setRoundRadius(xRadius, yRadius);
        invalidate();
    }

    public void Reversal(float rotation) {
        mEldScale = this.getScale();
        mSuppMatrix.postScale(1, -1, this.getWidth() / 2, this.getHeight() / 2);
        mSuppMatrix.postRotate(rotation, this.getWidth() / 2, this.getHeight() / 2);
        setImageMatrix(getImageViewMatrix());
    }


    public Bitmap getImageBitmap() {
        FastBitmapDrawable fbd = (FastBitmapDrawable) this.getDrawable();
        if (fbd == null) return null;
        return fbd.getBitmap();
    }


    private PointF getCenter() {
        return mCenter;
    }

    private void zoomTo(float scale) {
        if (LOG_ENABLED) {
            Log.i(LOG_TAG, "zoomTo: " + scale);
        }

//		if ( scale > getMaxScale() ) scale = getMaxScale();
//		if ( scale < getMinScale() ) scale = getMinScale();

        if (LOG_ENABLED) {
            Log.d(LOG_TAG, "sanitized scale: " + scale);
        }


        PointF center = getCenter();
        zoomTo(scale, center.x, center.y);
    }

    /**
     * Scale to the target scale
     *
     * @param scale      the target zoom
     * @param durationMs the animation duration
     */
    public void zoomTo(float scale, float durationMs) {
        PointF center = getCenter();
        zoomTo(scale, center.x, center.y, durationMs);
    }

    private void zoomTo(float scale, float centerX, float centerY) {
        //delete maxscale judge
        //if ( scale > getMaxScale() ) scale = getMaxScale();

        float oldScale = getScale();
        float deltaScale = scale / oldScale;
        postScale(deltaScale, centerX, centerY);
        onZoom();
        center();
    }

    private void onZoom() {
    }

    private void onZoomAnimationCompleted() {
    }

    /**
     * Scrolls the view by the x and y amount
     *
     * @param x
     * @param y
     */
    protected void scrollBy(float x, float y) {
        panBy(x, y);
    }

    private void panBy(double dx, double dy) {
        RectF rect = getBitmapRect();
        mScrollRect.set((float) dx, (float) dy, 0, 0);
        updateRect(rect, mScrollRect);
        postTranslate(mScrollRect.left, mScrollRect.top);
        center();
    }

    protected void updateRect(RectF bitmapRect, RectF scrollRect) {
        if (bitmapRect == null) return;

        if (bitmapRect.top >= 0 && bitmapRect.bottom <= mThisHeight) scrollRect.top = 0;
        if (bitmapRect.left >= 0 && bitmapRect.right <= mThisWidth) scrollRect.left = 0;
        if (bitmapRect.top + scrollRect.top >= 0 && bitmapRect.bottom > mThisHeight)
            scrollRect.top = (int) (0 - bitmapRect.top);
        if (bitmapRect.bottom + scrollRect.top <= (mThisHeight) && bitmapRect.top < 0)
            scrollRect.top = (int) ((mThisHeight) - bitmapRect.bottom);
        if (bitmapRect.left + scrollRect.left >= 0) scrollRect.left = (int) (0 - bitmapRect.left);
        if (bitmapRect.right + scrollRect.left <= (mThisWidth))
            scrollRect.left = (int) ((mThisWidth) - bitmapRect.right);
    }

    protected void scrollBy(float distanceX, float distanceY, final double durationMs) {
        final double dx = distanceX;
        final double dy = distanceY;
        final long startTime = System.currentTimeMillis();
        mHandler.post(new Runnable() {

            double old_x = 0;
            double old_y = 0;

            @Override
            public void run() {
                long now = System.currentTimeMillis();
                double currentMs = Math.min(durationMs, now - startTime);
                double x = mEasing.easeOut(currentMs, 0, dx, durationMs);
                double y = mEasing.easeOut(currentMs, 0, dy, durationMs);
                panBy((x - old_x), (y - old_y));
                old_x = x;
                old_y = y;
                if (currentMs < durationMs) {
                    mHandler.post(this);
                } else {
                    RectF centerRect = getCenter(mSuppMatrix);
                    if (centerRect.left != 0 || centerRect.top != 0)
                        scrollBy(centerRect.left, centerRect.top);
                }
            }
        });
    }

    protected void zoomTo(float scale, float centerX, float centerY, final float durationMs) {
        if (scale > getMaxScale()) scale = getMaxScale();

        final long startTime = System.currentTimeMillis();
        final float oldScale = getScale();

        final float deltaScale = scale - oldScale;

        Matrix m = new Matrix(mSuppMatrix);
        m.postScale(scale, scale, centerX, centerY);
        RectF rect = getCenter(m);

        final float destX = centerX + rect.left * scale;
        final float destY = centerY + rect.top * scale;

        mHandler.post(new Runnable() {

            @Override
            public void run() {
                long now = System.currentTimeMillis();
                float currentMs = Math.min(durationMs, now - startTime);
                float newScale = (float) mEasing.easeInOut(currentMs, 0, deltaScale, durationMs);
                zoomTo(oldScale + newScale, destX, destY);
                if (currentMs < durationMs) {
                    mHandler.post(this);
                } else {
                    onZoomAnimationCompleted();
                    center();
                }
            }
        });
    }

    @Override
    public void dispose() {
        clear();
    }

}
