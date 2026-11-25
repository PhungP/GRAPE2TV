package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;

/**
 * The now-playing progress: a light track, the played part brighter and a dark glass knob, as in the reference
 * card. It is read-only to touch; with the remote, Left and Right on the focused bar skip 10 seconds back or forward.
 */
public class KnobSeekView extends View {
    interface SeekListener {
        void onSeekBy(long deltaMs);
    }

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint played = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private float progress;
    private SeekListener listener;

    public KnobSeekView(Context context) {
        this(context, null);
    }

    public KnobSeekView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        track.setColor(0x40FFFFFF);
        played.setColor(0xFFEDEDED);
        knobRim.setStyle(Paint.Style.STROKE);
        knobRim.setStrokeWidth(1.6f * density);
        knobRim.setColor(0xCCFFFFFF);
        setFocusable(true);
    }

    void setSeekListener(SeekListener l) {
        listener = l;
    }

    void setProgress(float value) {
        float p = Math.max(0f, Math.min(1f, value));
        if (p != progress) {
            progress = p;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float th = 6f * density;
        float r = (isFocused() ? 13f : 11f) * density;
        float left = r, right = w - r;
        float cy = h / 2f;
        rect.set(left, cy - th / 2f, right, cy + th / 2f);
        canvas.drawRoundRect(rect, th / 2f, th / 2f, track);
        float x = left + (right - left) * progress;
        rect.set(left, cy - th / 2f, Math.max(left + th, x), cy + th / 2f);
        canvas.drawRoundRect(rect, th / 2f, th / 2f, played);
        knob.setShader(new RadialGradient(x - r * 0.25f, cy - r * 0.3f, r * 1.3f,
                new int[]{0xFF6A6A6A, 0xFF262626, 0xFF111111}, new float[]{0f, 0.65f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, cy, r, knob);
        canvas.drawCircle(x, cy, r - knobRim.getStrokeWidth() / 2f, knobRim);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        invalidate();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (listener != null && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            listener.onSeekBy(-10_000);
            return true;
        }
        if (listener != null && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            listener.onSeekBy(10_000);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
