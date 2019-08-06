package com.baiwang.imagezoom.graphics;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import java.io.InputStream;

/**
 * Fast bitmap drawable. Does not support states. it only
 * support alpha and colormatrix
 *
 * @author alessandro
 */
public class FastBitmapDrawable extends Drawable implements IBitmapDrawable {

    private Bitmap oriBitmap;

    private final Paint mPaint;
    protected PorterDuffXfermode srcInDuffXfermode = new PorterDuffXfermode(Mode.SRC_IN);
    private final Rect mRect;
    private final int mIntrinsicWidth;
    private final int mIntrinsicHeight;


    public FastBitmapDrawable(Bitmap b) {
        oriBitmap = b;
        if (null != oriBitmap) {
            mIntrinsicWidth = oriBitmap.getWidth();
            mIntrinsicHeight = oriBitmap.getHeight();
        } else {
            mIntrinsicWidth = 0;
            mIntrinsicHeight = 0;
        }
        mPaint = new Paint();
        mPaint.setDither(true);
        mPaint.setAntiAlias(true);
        mPaint.setFilterBitmap(true);


        mRect = new Rect(0, 0, mIntrinsicWidth, mIntrinsicHeight);
        RectF mRectF = new RectF(mRect);
    }


    public void setBitmap(Bitmap bitmap) {
        //mBitmap = bitmap;
        oriBitmap = bitmap;
    }

    public FastBitmapDrawable(InputStream is) {
        this(BitmapFactory.decodeStream(is));
    }

    @Override
    public void draw(Canvas canvas) {
//        int sc = canvas.saveLayer(0, 0, mIntrinsicWidth, mIntrinsicHeight, null,Canvas.ALL_SAVE_FLAG);      
//		canvas.drawRoundRect(mRectF, mxRadius, myRadius, mPaint); 
//		mPaint.setXfermode(srcInDuffXfermode);
        try {
            canvas.drawBitmap(oriBitmap, mRect, mRect, mPaint);
        } catch (Exception e) {
//
        }
//		mPaint.setXfermode(null);
//	    canvas.restoreToCount(sc);
    }

    public void setRoundRadius(float xRadius, float yRadius) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mPaint.setColorFilter(cf);
    }

    @Override
    public int getIntrinsicWidth() {
        return mIntrinsicWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return mIntrinsicHeight;
    }

    @Override
    public int getMinimumWidth() {
        return mIntrinsicWidth;
    }

    @Override
    public int getMinimumHeight() {
        return mIntrinsicHeight;
    }

    public void setAntiAlias(boolean value) {
        mPaint.setAntiAlias(value);
        invalidateSelf();
    }

    @Override
    public Bitmap getBitmap() {
        return oriBitmap;
    }

    public Paint getPaint() {
        return mPaint;
    }
}