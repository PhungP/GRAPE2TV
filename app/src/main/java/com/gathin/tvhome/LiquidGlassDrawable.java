package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * A panel in the "Liquid Glass" look, with no blur of its own (the blurred picture behind it comes from
 * {@link BlurBackdrop}): a faint dark base so text stays readable, a translucent white fill that fades
 * diagonally, a bright sheen along the top, a light-bending rim (bright at the top left, dim through the
 * middle, bright again at the bottom right) and a thin inner edge.
 */
final class LiquidGlassDrawable extends Drawable {
    private final float radius;
    private final float rimPx;
    private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    LiquidGlassDrawable(float radiusPx, float density) {
        radius = radiusPx;
        rimPx = 2f * density;
        base.setColor(0x47000000);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(rimPx);
        inner.setStyle(Paint.Style.STROKE);
        inner.setStrokeWidth(Math.max(1f, density));
        inner.setColor(0x26FFFFFF);
    }

    @Override
    protected void onBoundsChange(Rect b) {
        float w = b.width(), h = b.height();
        if (w <= 0 || h <= 0) return;
        fill.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom, 0x42FFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP));
        sheen.setShader(new LinearGradient(0, b.top, 0, b.top + h * 0.42f, 0x38FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom, new int[]{0xF2FFFFFF, 0x38FFFFFF, 0xA6FFFFFF},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;
        rect.set(b);
        canvas.drawRoundRect(rect, radius, radius, base);
        canvas.drawRoundRect(rect, radius, radius, fill);
        canvas.drawRoundRect(rect, radius, radius, sheen);
        float half = rimPx / 2f;
        rect.set(b.left + half, b.top + half, b.right - half, b.bottom - half);
        canvas.drawRoundRect(rect, radius - half, radius - half, rim);
        float in = rimPx + 1.5f;
        rect.set(b.left + in, b.top + in, b.right - in, b.bottom - in);
        canvas.drawRoundRect(rect, Math.max(0f, radius - in), Math.max(0f, radius - in), inner);
    }

    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter filter) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
