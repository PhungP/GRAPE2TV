package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Five rounded bars like a sound wave, at the top right of the now-playing card. They move while the music
 * plays and rest when it is paused. The animator runs only while the view is on screen and playing.
 */
public class EqualizerView extends View {
    private static final float[] BASE = {0.38f, 0.72f, 1f, 0.62f, 0.42f};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;
    private float phase;
    private boolean playing;

    public EqualizerView(Context context) {
        this(context, null);
    }

    public EqualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xFFF2F2F2);
        halo.setStyle(Paint.Style.FILL);
        halo.setColor(0x33FFFFFF);
        setFocusable(true);
        setClickable(true);
    }

    void setPlaying(boolean value) {
        if (playing == value) return;
        playing = value;
        syncAnimator();
        invalidate();
    }

    private void syncAnimator() {
        boolean run = playing && isAttachedToWindow();
        if (run && animator == null) {
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(1100);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a -> {
                phase = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        } else if (!run && animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        syncAnimator();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        if (isFocused()) canvas.drawCircle(w / 2f, h / 2f, Math.min(w, h) / 2f, halo);
        float bar = Math.max(2f, w / 11f);
        float gap = (w * 0.62f - bar * BASE.length) / (BASE.length - 1);
        float left = w * 0.19f + bar / 2f;
        float maxH = h * 0.62f;
        paint.setStrokeWidth(bar);
        for (int i = 0; i < BASE.length; i++) {
            float f = BASE[i];
            if (playing) f *= 0.62f + 0.38f * (float) Math.sin(2 * Math.PI * (phase + i * 0.21f));
            float half = Math.max(bar / 2f, maxH * f / 2f);
            float x = left + i * (bar + gap);
            canvas.drawLine(x, h / 2f - half, x, h / 2f + half, paint);
        }
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        invalidate();
    }
}
