package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * A translucent glass slab: soft sheen fill, bright top specular, crisp rim and a faint outer glow.
 * One instance floats over the grid and is moved with spring animations between tiles.
 */
public class GlassView extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specular = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private float radius;
    /** Glow colour as 0xRRGGBB: white normally, a festival's accent (diya gold) while a festive skin is on. */
    private int glowRgb = 0xFFFFFF;
    /** Glow strength: the white glow stays faint, a coloured one needs more alpha to read as warm light. */
    private int glowAlpha = 0x30;
    /** glass_style rim or halo: no sheen and no fill, only the crisp rim (and, for halo, a lighter outer glow). */
    private boolean flat;
    private boolean halo;
    /** glass_style liquid (Apple's Liquid Glass look): a thicker, light-bending rim, an inner edge and a bright sheen. */
    private boolean liquid;
    /** glass_style xperia: a flat rounded outline with a faint white fill, no glow and no sheen. */
    private boolean xperia;
    private final Paint liquidRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flatFill = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        radius = 20f * density;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.5f * density);
        rim.setColor(0xCCFFFFFF);
        liquidRim.setStyle(Paint.Style.STROKE);
        liquidRim.setStrokeWidth(2f * density);
        innerRim.setStyle(Paint.Style.STROKE);
        innerRim.setStrokeWidth(1f * density);
        innerRim.setColor(0x38FFFFFF);
        flatFill.setStyle(Paint.Style.FILL);
        flatFill.setColor(0x1FFFFFFF);
        glow.setStyle(Paint.Style.STROKE);
        // Cache the slab as a texture. The spring only changes translation, so the GPU can
        // move it without replaying clipPath and the glow on every frame. That path is
        // software-rendered on the MT5895 and is what makes the highlight crawl.
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    /** Tints the rim and the outer glow; 0 restores the plain white glass. */
    void setAccent(int argb) {
        if (argb == 0) {
            glowRgb = 0xFFFFFF;
            glowAlpha = 0x30;
            rim.setColor(0xCCFFFFFF);
        } else {
            glowRgb = argb & 0xFFFFFF;
            glowAlpha = 0x70;
            // The rim is the accent lifted towards white, so the edge still reads as glass, not paint.
            int r = (((glowRgb >> 16) & 0xFF) + 255 * 2) / 3, g = (((glowRgb >> 8) & 0xFF) + 255 * 2) / 3,
                    b = ((glowRgb & 0xFF) + 255 * 2) / 3;
            rim.setColor(0xE6000000 | (r << 16) | (g << 8) | b);
        }
        invalidate();
    }

    /** One of Config's GLASS_* styles. */
    void setStyle(String style) {
        liquid = Config.GLASS_LIQUID.equals(style);
        xperia = Config.GLASS_XPERIA.equals(style);
        flat = Config.GLASS_RIM.equals(style) || Config.GLASS_HALO.equals(style) || xperia;
        halo = Config.GLASS_HALO.equals(style);
        rim.setStrokeWidth((flat ? 2f : 1.5f) * density);
        if (xperia) rim.setColor(0xF2FFFFFF);
        if (getWidth() > 0 && getHeight() > 0) onSizeChanged(getWidth(), getHeight(), 0, 0);
        invalidate();
    }

    void setRadius(float px) {
        radius = px;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fill.setShader(new LinearGradient(0, 0, 0, h, 0x3DFFFFFF, 0x12FFFFFF, Shader.TileMode.CLAMP));
        specular.setShader(new LinearGradient(0, 0, 0, h * 0.45f, liquid ? 0x8CFFFFFF : 0x66FFFFFF, 0x00FFFFFF,
                Shader.TileMode.CLAMP));
        if (liquid) {
            fill.setShader(new LinearGradient(0, 0, w, h, 0x59FFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP));
            // Bright where the light comes in (top left), dim through the middle, a second bright edge at the bottom right.
            liquidRim.setShader(new LinearGradient(0, 0, w, h, new int[]{0xFFFFFFFF, 0x4DFFFFFF, 0x99FFFFFF},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float inset = 6f * density;   // room for the glow
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);

        // Outer glow: thin rings across the inset, each a little fainter, so it fades out smoothly. Three wide
        // strokes left visible bands once the glow took an app's colour at a stronger alpha. The hardware layer
        // caches the result, so the rings cost one draw, not one per frame of the spring.
        if (!flat || halo) {
            int rings = 12;
            float step = inset / rings;
            int alpha = flat ? glowAlpha / 2 : glowAlpha;
            glow.setStrokeWidth(step + 0.5f);
            for (int i = 0; i < rings; i++) {
                float o = step * (i + 0.5f);
                float t = 1f - (float) i / rings;
                glow.setColor(Math.round(alpha * t) << 24 | glowRgb);
                canvas.drawRoundRect(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o, radius + o, radius + o, glow);
            }
        }
        if (flat) {
            if (xperia) canvas.drawRoundRect(rect, radius, radius, flatFill);
            canvas.drawRoundRect(rect, radius, radius, rim);
            return;
        }

        canvas.drawRoundRect(rect, radius, radius, fill);

        // Sheen across the top 45%: clipRect stays on the GPU (clipPath did not on the MT5895) and
        // the round rect under it keeps the top corners rounded with a flat bottom edge.
        canvas.save();
        canvas.clipRect(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.45f);
        canvas.drawRoundRect(rect, radius, radius, specular);
        canvas.restore();

        if (liquid) {
            canvas.drawRoundRect(rect, radius, radius, liquidRim);
            float in = 3f * density;
            canvas.drawRoundRect(rect.left + in, rect.top + in, rect.right - in, rect.bottom - in,
                    Math.max(0f, radius - in), Math.max(0f, radius - in), innerRim);
            return;
        }
        canvas.drawRoundRect(rect, radius, radius, rim);
    }
}
