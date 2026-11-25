package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;

import java.io.File;
import java.util.Random;

/**
 * The screensaver: the day's online wallpaper (when one is chosen and has been fetched) drifting slowly, with a big
 * clock and date on top. The clock moves to another corner every minute so that no pixel stays lit for long on
 * an OLED or plasma panel. Pick it in the TV's Screen saver settings, or with
 * {@code adb shell settings put secure screensaver_components com.grape2tv.home/com.gathin.tvhome.GrapeDream}.
 */
public final class GrapeDream extends DreamService {
    private static final long MOVE_MS = 60_000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private FrameLayout root;
    private LinearLayout clockBox;
    private ImageView picture;
    private int corner = -1;

    private final Runnable move = new Runnable() {
        @Override
        public void run() {
            moveClock(true);
            handler.postDelayed(this, MOVE_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        picture = new ImageView(this);
        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        picture.setColorFilter(0x66000000);
        root.addView(picture, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        DisplayMetrics m = getResources().getDisplayMetrics();
        File f = OnlineWallpaper.file(this);
        if (!OnlineWallpaper.OFF.equals(OnlineWallpaper.chosen(this))) {
            Bitmap b = OnlineWallpaper.decode(f, m.widthPixels, m.heightPixels);
            if (b != null) picture.setImageBitmap(b);
        }

        float d = m.density;
        clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        clockBox.setPadding(Math.round(56 * d), Math.round(40 * d), Math.round(56 * d), Math.round(40 * d));
        Typeface light = font(R.font.outfit_light);
        Typeface regular = font(R.font.outfit_regular);

        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        time.setTextColor(Color.WHITE);
        time.setTextSize(96);
        time.setIncludeFontPadding(false);
        if (light != null) time.setTypeface(light);
        clockBox.addView(time);

        TextClock date = new TextClock(this);
        String pattern = getString(R.string.date_format_home);
        date.setFormat12Hour(pattern);
        date.setFormat24Hour(pattern);
        date.setTextColor(0xCCFFFFFF);
        date.setTextSize(20);
        date.setLetterSpacing(0.12f);
        if (regular != null) date.setTypeface(regular);
        clockBox.addView(date);

        root.addView(clockBox, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        setContentView(root);
        moveClock(false);
    }

    private Typeface font(int id) {
        try {
            return getResources().getFont(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        // A very slow zoom keeps the picture alive without redrawing anything else.
        picture.animate().scaleX(1.12f).scaleY(1.12f).setDuration(45_000L).start();
        handler.postDelayed(move, MOVE_MS);
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacks(move);
        if (picture != null) picture.animate().cancel();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }

    /** Puts the clock in a corner other than the one it is in. */
    private void moveClock(boolean fade) {
        int next;
        do {
            next = random.nextInt(4);
        } while (next == corner);
        corner = next;
        final int gravity = (next < 2 ? Gravity.TOP : Gravity.BOTTOM) | (next % 2 == 0 ? Gravity.START : Gravity.END);
        final Runnable apply = () -> {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) clockBox.getLayoutParams();
            lp.gravity = gravity;
            clockBox.setLayoutParams(lp);
        };
        if (!fade) {
            apply.run();
            return;
        }
        clockBox.animate().alpha(0f).setDuration(600).withEndAction(() -> {
            apply.run();
            clockBox.animate().alpha(1f).setDuration(600).start();
        }).start();
    }
}
