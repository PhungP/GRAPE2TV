package com.gathin.tvhome;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LightingColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * A blurred snapshot of what is on screen, painted behind a panel that opens over Home (Launcher settings, the
 * now-playing panel). It is made the same way on every TV: Home is drawn once into a bitmap an eighth the
 * size, blurred with a few box passes (a few milliseconds at that size) and stretched back over the screen
 * as the panel's background. No window blur or GPU effect is needed, so it also works on the weak SoCs
 * that cannot do one.
 */
final class BlurBackdrop {
    private static final int SCALE = 8;
    private static final int RADIUS = 3;
    private static final int PASSES = 3;
    private static Bitmap pending;

    private BlurBackdrop() {
    }

    /** Snapshots the activity's current window and keeps it for the next {@link #take}. */
    static void capture(Activity activity) {
        pending = snapshot(activity);
    }

    /** The snapshot kept by {@link #capture}, once; null when there is none. */
    static Bitmap take() {
        Bitmap b = pending;
        pending = null;
        return b;
    }

    static Bitmap snapshot(Activity activity) {
        try {
            View decor = activity.getWindow().getDecorView();
            int w = decor.getWidth(), h = decor.getHeight();
            if (w <= 0 || h <= 0) return null;
            int sw = Math.max(1, w / SCALE), sh = Math.max(1, h / SCALE);
            Bitmap b = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.scale(sw / (float) w, sh / (float) h);
            decor.draw(c);
            blur(b);
            return b;
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    /** The snapshot as a darkened full-window background. */
    static Drawable drawable(Resources res, Bitmap blurred) {
        if (blurred == null) return new android.graphics.drawable.ColorDrawable(0xCC0A0D12);
        BitmapDrawable d = new BitmapDrawable(res, blurred);
        d.setFilterBitmap(true);
        d.setColorFilter(new LightingColorFilter(0xFFA8A8A8, 0));
        return d;
    }

    private static void blur(Bitmap bmp) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] a = new int[w * h];
        int[] b = new int[w * h];
        bmp.getPixels(a, 0, w, 0, 0, w, h);
        for (int i = 0; i < PASSES; i++) {
            boxHorizontal(a, b, w, h, RADIUS);
            boxVertical(b, a, w, h, RADIUS);
        }
        bmp.setPixels(a, 0, w, 0, 0, w, h);
    }

    private static int clamp(int v, int hi) {
        return v < 0 ? 0 : Math.min(v, hi);
    }

    private static void boxHorizontal(int[] src, int[] dst, int w, int h, int r) {
        int div = 2 * r + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            int sa = 0, sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int p = src[row + clamp(i, w - 1)];
                sa += p >>> 24; sr += (p >> 16) & 255; sg += (p >> 8) & 255; sb += p & 255;
            }
            for (int x = 0; x < w; x++) {
                dst[row + x] = ((sa / div) << 24) | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int add = src[row + clamp(x + r + 1, w - 1)];
                int sub = src[row + clamp(x - r, w - 1)];
                sa += (add >>> 24) - (sub >>> 24);
                sr += ((add >> 16) & 255) - ((sub >> 16) & 255);
                sg += ((add >> 8) & 255) - ((sub >> 8) & 255);
                sb += (add & 255) - (sub & 255);
            }
        }
    }

    private static void boxVertical(int[] src, int[] dst, int w, int h, int r) {
        int div = 2 * r + 1;
        for (int x = 0; x < w; x++) {
            int sa = 0, sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int p = src[clamp(i, h - 1) * w + x];
                sa += p >>> 24; sr += (p >> 16) & 255; sg += (p >> 8) & 255; sb += p & 255;
            }
            for (int y = 0; y < h; y++) {
                dst[y * w + x] = ((sa / div) << 24) | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int add = src[clamp(y + r + 1, h - 1) * w + x];
                int sub = src[clamp(y - r, h - 1) * w + x];
                sa += (add >>> 24) - (sub >>> 24);
                sr += ((add >> 16) & 255) - ((sub >> 16) & 255);
                sg += ((add >> 8) & 255) - ((sub >> 8) & 255);
                sb += (add & 255) - (sub & 255);
            }
        }
    }
}
