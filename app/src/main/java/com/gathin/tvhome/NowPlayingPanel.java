package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Quick controls for the app that is playing: artwork, title, artist, progress, previous / play-pause / next and
 * a button that opens the app. D-pad Left and Right move between the buttons, OK presses, Back closes.
 * While the panel is open it keeps itself up to date from {@link NowPlaying}'s callbacks and ticks the progress
 * bar once a second, and only then.
 */
final class NowPlayingPanel {
    private final Activity activity;
    private final NowPlaying source;
    private final Handler handler;
    private final String style;
    private final Dialog dialog;
    private final ImageView art;
    private final TextView app, title, artist;
    private final ProgressBar progress;
    private final ImageView prev, play, next, open;
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
        this.style = UiStyle.normalize(style);
        this.density = activity.getResources().getDisplayMetrics().density;
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View root = activity.getLayoutInflater().inflate(R.layout.dialog_now_playing, null);
        dialog.setContentView(root);
        root.setBackground(UiStyle.panel(this.style, density));
        art = root.findViewById(R.id.npArt);
        app = root.findViewById(R.id.npApp);
        title = root.findViewById(R.id.npTitle);
        artist = root.findViewById(R.id.npArtist);
        progress = root.findViewById(R.id.npProgress);
        prev = root.findViewById(R.id.npPrev);
        play = root.findViewById(R.id.npPlay);
        next = root.findViewById(R.id.npNext);
        open = root.findViewById(R.id.npOpen);
        title.setSelected(true);
        final float artRadius = (UiStyle.LIQUID_GLASS.equals(this.style) ? 22 : UiStyle.XPERIA.equals(this.style) ? 8 : 12) * density;
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), artRadius);
            }
        });
        art.setClipToOutline(true);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(UiStyle.accent(this.style)));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x33FFFFFF));

        prev.setOnClickListener(v -> source.previous());
        next.setOnClickListener(v -> source.next());
        play.setOnClickListener(v -> source.playPause());
        open.setOnClickListener(v -> openApp());

        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.CENTER);
            w.setDimAmount(0.45f);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            // Liquid Glass: blur what is behind the panel where the TV's GPU and firmware allow it. Skipped
            // everywhere else, where the translucent panel alone has to do.
            if (UiStyle.LIQUID_GLASS.equals(this.style) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    w.setBackgroundBlurRadius(Math.round(40 * density));
                } catch (RuntimeException ignored) {
                }
            }
        }
        dialog.setOnDismissListener(d -> {
            showing = false;
            handler.removeCallbacks(tick);
        });
    }

    void show() {
        if (!source.active()) return;
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
        title.setText(source.title().isEmpty() ? activity.getString(R.string.np_unknown_title) : source.title());
        artist.setText(source.artist());
        artist.setVisibility(source.artist().isEmpty() ? View.GONE : View.VISIBLE);
        app.setText(appLabel());
        if (source.art() != null) {
            art.setImageBitmap(source.art());
            art.setBackground(null);
        } else {
            art.setImageResource(R.drawable.ic_music);
            art.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            art.setPadding(Math.round(44 * density), Math.round(44 * density), Math.round(44 * density), Math.round(44 * density));
            GradientDrawable ph = new GradientDrawable();
            ph.setColor(0x26FFFFFF);
            art.setBackground(ph);
        }
        if (source.art() != null) {
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setPadding(0, 0, 0, 0);
        }
        play.setImageResource(source.playing() ? R.drawable.ic_pause : R.drawable.ic_play);
        prev.setAlpha(source.canSkipPrevious() ? 1f : 0.4f);
        next.setAlpha(source.canSkipNext() ? 1f : 0.4f);
        updateProgress();
    }

    private void updateProgress() {
        long d = source.durationMs();
        if (d <= 0) {
            progress.setVisibility(View.INVISIBLE);
            return;
        }
        progress.setVisibility(View.VISIBLE);
        progress.setProgress((int) (source.positionMs() * 1000L / d));
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
