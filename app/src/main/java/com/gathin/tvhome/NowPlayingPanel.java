package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * Quick controls for the app that is playing, in a Liquid Glass card over a blurred snapshot of Home: artwork,
 * title and artist, a sound-wave button that opens the app, the progress with a knob (Left and Right on it skip
 * 10 seconds) and previous / play-pause / next. D-pad moves between them, OK presses, Back closes. While open
 * it follows {@link NowPlaying}'s callbacks and ticks the progress once a second, and only then.
 */
final class NowPlayingPanel {
    private final Activity activity;
    private final NowPlaying source;
    private final Handler handler;
    private final Dialog dialog;
    private final View card;
    private final ImageView art;
    private final TextView app, title, artist, elapsed, total;
    private final KnobSeekView seek;
    private final EqualizerView wave;
    private final ImageView prev, play, next;
    private final float density;
    private boolean showing;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!showing) return;
            updateProgress();
            handler.postDelayed(this, 1000);
        }
    };

    NowPlayingPanel(Activity activity, NowPlaying source, Handler handler, String style) {
        this.activity = activity;
        this.source = source;
        this.handler = handler;
        this.density = activity.getResources().getDisplayMetrics().density;
        dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View root = activity.getLayoutInflater().inflate(R.layout.dialog_now_playing, null);
        dialog.setContentView(root);
        card = root.findViewById(R.id.npCard);
        float radius = (UiStyle.XPERIA.equals(UiStyle.normalize(style)) ? 18 : 30) * density;
        card.setBackground(new LiquidGlassDrawable(radius, density));
        int screenW = activity.getResources().getDisplayMetrics().widthPixels;
        ViewGroup.LayoutParams lp = card.getLayoutParams();
        lp.width = Math.min(Math.round(620 * density), Math.round(screenW * 0.92f));
        card.setLayoutParams(lp);
        art = root.findViewById(R.id.npArt);
        app = root.findViewById(R.id.npApp);
        title = root.findViewById(R.id.npTitle);
        artist = root.findViewById(R.id.npArtist);
        elapsed = root.findViewById(R.id.npElapsed);
        total = root.findViewById(R.id.npTotal);
        seek = root.findViewById(R.id.npSeek);
        wave = root.findViewById(R.id.npOpen);
        prev = root.findViewById(R.id.npPrev);
        play = root.findViewById(R.id.npPlay);
        next = root.findViewById(R.id.npNext);
        title.setSelected(true);
        final float artRadius = 18 * density;
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), artRadius);
            }
        });
        art.setClipToOutline(true);

        prev.setOnClickListener(v -> source.previous());
        next.setOnClickListener(v -> source.next());
        play.setOnClickListener(v -> source.playPause());
        wave.setOnClickListener(v -> openApp());
        seek.setSeekListener(delta -> {
            source.seekBy(delta);
            handler.postDelayed(this::updateProgress, 250);
        });

        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
        dialog.setOnDismissListener(d -> {
            showing = false;
            wave.setPlaying(false);
            handler.removeCallbacks(tick);
        });
    }

    void show() {
        if (!source.active()) return;
        // The window behind the card is the blurred Home, captured now while Home is still the one on screen.
        View root = dialog.findViewById(R.id.npRoot);
        if (root != null) root.setBackground(BlurBackdrop.drawable(activity.getResources(), BlurBackdrop.snapshot(activity)));
        bind();
        showing = true;
        dialog.show();
        play.requestFocus();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    void dismiss() {
        if (dialog.isShowing()) dialog.dismiss();
    }

    boolean isShowing() {
        return dialog.isShowing();
    }

    /** Called when the session, its metadata or its state changed while the panel is open. */
    void bind() {
        if (!source.active()) {
            dismiss();
            return;
        }
        String t = source.title();
        title.setText(t.isEmpty() ? activity.getString(R.string.np_unknown_title) : t);
        artist.setText(source.artist());
        artist.setVisibility(source.artist().isEmpty() ? View.GONE : View.VISIBLE);
        app.setText(appLabel());
        if (source.art() != null) {
            art.setImageBitmap(source.art());
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setPadding(0, 0, 0, 0);
            art.setBackground(null);
        } else {
            int pad = Math.round(24 * density);
            art.setImageResource(R.drawable.ic_music);
            art.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            art.setPadding(pad, pad, pad, pad);
            GradientDrawable ph = new GradientDrawable();
            ph.setColor(0x26FFFFFF);
            art.setBackground(ph);
        }
        play.setImageResource(source.playing() ? R.drawable.ic_pause : R.drawable.ic_play);
        wave.setPlaying(source.playing());
        prev.setAlpha(source.canSkipPrevious() ? 1f : 0.4f);
        next.setAlpha(source.canSkipNext() ? 1f : 0.4f);
        updateProgress();
    }

    private void updateProgress() {
        long d = source.durationMs();
        long p = source.positionMs();
        elapsed.setText(formatTime(p));
        if (d <= 0) {
            total.setText("");
            seek.setProgress(0f);
            return;
        }
        total.setText(formatTime(d));
        seek.setProgress(p / (float) d);
    }

    /** "0:48", "2:32", "1:04:09". */
    static String formatTime(long ms) {
        long s = Math.max(0, ms) / 1000;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec)
                : String.format(Locale.ROOT, "%d:%02d", m, sec);
    }

    private CharSequence appLabel() {
        try {
            PackageManager pm = activity.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(source.packageName(), 0));
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return source.packageName();
        }
    }

    /** Opens the player's own screen: its session activity when it set one, else its launcher entry. */
    private void openApp() {
        try {
            if (source.controller() != null && source.controller().getSessionActivity() != null) {
                source.controller().getSessionActivity().send();
                dismiss();
                return;
            }
        } catch (Exception ignored) {
            // fall through to the launcher entry
        }
        PackageManager pm = activity.getPackageManager();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(source.packageName());
        if (launch == null) launch = pm.getLaunchIntentForPackage(source.packageName());
        if (launch == null) {
            Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show();
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            activity.startActivity(launch);
            dismiss();
        } catch (RuntimeException e) {
            Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }
}
