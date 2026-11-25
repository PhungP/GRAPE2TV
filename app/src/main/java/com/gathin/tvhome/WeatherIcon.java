package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * A small flat weather icon drawn with the canvas: a sun, a moon, clouds, rain, snow, fog or a bolt.
 * Drawn rather than taken from an emoji font because many TV firmwares ship none.
 */
final class WeatherIcon extends Drawable {
    private final int category;
    private final boolean day;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    WeatherIcon(int category, boolean day) {
        this.category = category;
        this.day = day;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height());
        float x = b.left + (b.width() - s) / 2f;
        float y = b.top + (b.height() - s) / 2f;
        canvas.save();
        canvas.translate(x, y);
        switch (category) {
            case Weather.CLEAR:
                if (day) sun(canvas, s * 0.5f, s * 0.5f, s * 0.22f, s);
                else moon(canvas, s * 0.5f, s * 0.5f, s * 0.26f);
                break;
            case Weather.PARTLY:
                if (day) sun(canvas, s * 0.36f, s * 0.36f, s * 0.15f, s * 0.7f);
                else moon(canvas, s * 0.36f, s * 0.36f, s * 0.17f);
                cloud(canvas, s, 0.12f, 0.40f, 0.78f, 0xFFE9EEF5);
                break;
            case Weather.FOG:
                cloud(canvas, s, 0.10f, 0.12f, 0.64f, 0xFFC9D2DE);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeWidth(s * 0.07f);
                paint.setColor(0xFFC9D2DE);
                canvas.drawLine(s * 0.2f, s * 0.78f, s * 0.8f, s * 0.78f, paint);
                canvas.drawLine(s * 0.3f, s * 0.92f, s * 0.7f, s * 0.92f, paint);
                break;
            case Weather.DRIZZLE:
            case Weather.RAIN:
                cloud(canvas, s, 0.10f, 0.10f, 0.64f, 0xFFB7C4D6);
                drops(canvas, s, category == Weather.RAIN ? 3 : 2);
                break;
            case Weather.SNOW:
                cloud(canvas, s, 0.10f, 0.10f, 0.64f, 0xFFD8E2EE);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFFFFFFF);
                canvas.drawCircle(s * 0.32f, s * 0.82f, s * 0.05f, paint);
                canvas.drawCircle(s * 0.52f, s * 0.92f, s * 0.05f, paint);
                canvas.drawCircle(s * 0.70f, s * 0.80f, s * 0.05f, paint);
                break;
            case Weather.STORM:
                cloud(canvas, s, 0.10f, 0.08f, 0.60f, 0xFF93A3B8);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFFFD54F);
                android.graphics.Path bolt = new android.graphics.Path();
                bolt.moveTo(s * 0.54f, s * 0.60f);
                bolt.lineTo(s * 0.38f, s * 0.84f);
                bolt.lineTo(s * 0.50f, s * 0.84f);
                bolt.lineTo(s * 0.44f, s * 1.00f);
                bolt.lineTo(s * 0.66f, s * 0.74f);
                bolt.lineTo(s * 0.53f, s * 0.74f);
                bolt.close();
                canvas.drawPath(bolt, paint);
                break;
            default:   // CLOUDY
                cloud(canvas, s, 0.06f, 0.26f, 0.88f, 0xFFE9EEF5);
                break;
        }
        canvas.restore();
    }

    private void sun(Canvas c, float cx, float cy, float r, float s) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFC83D);
        c.drawCircle(cx, cy, r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(s * 0.06f);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            float c1 = (float) Math.cos(a), s1 = (float) Math.sin(a);
            c.drawLine(cx + c1 * r * 1.45f, cy + s1 * r * 1.45f, cx + c1 * r * 1.85f, cy + s1 * r * 1.85f, paint);
        }
    }

    private void moon(Canvas c, float cx, float cy, float r) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFF1E9C8);
        c.drawCircle(cx, cy, r, paint);
        // A bite out of it, in the colour of the sky behind: the header is dark, so a dark disc reads as a crescent.
        paint.setColor(0xFF0B0F1A);
        c.drawCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.85f, paint);
    }

    /** A cloud of three circles and a base; left/top/width are fractions of the icon size. */
    private void cloud(Canvas c, float s, float left, float top, float width, int color) {
        float l = s * left, t = s * top, w = s * width;
        float h = w * 0.55f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        oval.set(l, t + h * 0.45f, l + w, t + h);
        c.drawRoundRect(oval, h * 0.27f, h * 0.27f, paint);
        c.drawCircle(l + w * 0.32f, t + h * 0.55f, h * 0.30f, paint);
        c.drawCircle(l + w * 0.60f, t + h * 0.40f, h * 0.38f, paint);
    }

    private void drops(Canvas c, float s, int n) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(s * 0.06f);
        paint.setColor(0xFF6EC1FF);
        float[] xs = {0.30f, 0.50f, 0.70f};
        for (int i = 0; i < n; i++) {
            float x = n == 2 ? (i == 0 ? 0.38f : 0.62f) : xs[i];
            c.drawLine(s * x, s * 0.74f, s * (x - 0.05f), s * 0.90f, paint);
        }
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
