package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.provider.Settings;

import java.util.List;

/**
 * Follows the media session of whatever app is playing in the background, so Home can show what it is and
 * offer previous / play-pause / next.
 *
 * <p>Android only lets an app list other apps' media sessions when it holds notification access, so this reuses
 * the launcher's existing notification listener, {@link GuardRevival}, as the key. Reading sessions is all it
 * does: no notification is read. Without the access {@link #hasAccess} is false and nothing is shown.
 * Enable it with {@code cmd notification allow_listener com.grape2tv.home/com.gathin.tvhome.GuardRevival}, or in the TV's
 * notification access settings. {@link #start} and {@link #stop} follow onResume and onPause, so nothing
 * is registered while another app is in front.
 */
final class NowPlaying {
    /** What the header needs to draw; all fields are for the session picked by {@link #pick}. */
    interface Listener {
        /** On the main thread, whenever the session, its metadata or its playback state changed. */
        void onNowPlaying(NowPlaying source);
    }

    private final Context context;
    private final Handler handler;
    private final Listener listener;
    private final ComponentName key;
    private MediaSessionManager manager;
    private MediaController controller;
    private boolean started;
    private boolean access;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = this::adopt;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { listener.onNowPlaying(NowPlaying.this); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { listener.onNowPlaying(NowPlaying.this); }
        @Override public void onSessionDestroyed() { refresh(); }
    };

    NowPlaying(Context context, Handler handler, Listener listener) {
        this.context = context;
        this.handler = handler;
        this.listener = listener;
        this.key = new ComponentName(context, GuardRevival.class);
    }

    /** SharedPreferences key (Config.SETTINGS_NAME) of the on-TV switch; config.xml's show_now_playing is its default. */
    static final String KEY = "show_now_playing";

    static boolean enabled(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY, ctx.getResources().getBoolean(R.bool.show_now_playing));
    }

    /** Notification access granted to this launcher's listener (what getActiveSessions needs). */
    static boolean hasAccess(Context context) {
        String flat = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        if (flat == null || flat.isEmpty()) return false;
        String mine = new ComponentName(context, GuardRevival.class).flattenToString();
        for (String part : flat.split(":")) {
            if (mine.equals(part)) return true;
        }
        return false;
    }

    void start() {
        if (started) return;
        started = true;
        manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        refresh();
    }

    void stop() {
        if (!started) return;
        started = false;
        detach();
        if (manager != null) {
            try {
                manager.removeOnActiveSessionsChangedListener(sessionsChanged);
            } catch (RuntimeException ignored) {
            }
        }
    }

    /** Looks again, for example after the owner granted the access in settings. */
    void refresh() {
        if (!started || manager == null) return;
        access = hasAccess(context);
        if (!access) {
            detach();
            listener.onNowPlaying(this);
            return;
        }
        try {
            manager.addOnActiveSessionsChangedListener(sessionsChanged, key, handler);
            adopt(manager.getActiveSessions(key));
        } catch (SecurityException e) {
            access = false;
            detach();
            listener.onNowPlaying(this);
        }
    }

    private void adopt(List<MediaController> sessions) {
        MediaController best = pick(sessions);
        if (best != null && controller != null && best.getSessionToken().equals(controller.getSessionToken())) {
            listener.onNowPlaying(this);
            return;
        }
        detach();
        controller = best;
        if (controller != null) controller.registerCallback(callback, handler);
        listener.onNowPlaying(this);
    }

    /** The session that is playing, else the most recent one that has a title; null when there is nothing to show. */
    static MediaController pick(List<MediaController> sessions) {
        if (sessions == null) return null;
        MediaController fallback = null;
        for (MediaController c : sessions) {
            if (c == null) continue;
            PlaybackState s = c.getPlaybackState();
            if (s != null && s.getState() == PlaybackState.STATE_PLAYING) return c;
            if (fallback == null && s != null && isLive(s.getState()) && c.getMetadata() != null) fallback = c;
        }
        return fallback;
    }

    private static boolean isLive(int state) {
        return state == PlaybackState.STATE_PAUSED || state == PlaybackState.STATE_BUFFERING
                || state == PlaybackState.STATE_PLAYING;
    }

    private void detach() {
        if (controller != null) {
            try {
                controller.unregisterCallback(callback);
            } catch (RuntimeException ignored) {
            }
            controller = null;
        }
    }

    // ---- what Home reads ------------------------------------------------------------------------

    boolean access() { return access; }

    boolean active() { return controller != null; }

    boolean playing() {
        PlaybackState s = controller == null ? null : controller.getPlaybackState();
        return s != null && s.getState() == PlaybackState.STATE_PLAYING;
    }

    String packageName() { return controller == null ? "" : controller.getPackageName(); }

    String title() { return text(MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE); }

    String artist() {
        return text(MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
    }

    private String text(String... keys) {
        MediaMetadata m = controller == null ? null : controller.getMetadata();
        if (m == null) return "";
        for (String k : keys) {
            CharSequence v = m.getText(k);
            if (v != null && v.toString().trim().length() > 0) return v.toString().trim();
        }
        return "";
    }

    Bitmap art() {
        MediaMetadata m = controller == null ? null : controller.getMetadata();
        if (m == null) return null;
        Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        return b;
    }

    long durationMs() {
        MediaMetadata m = controller == null ? null : controller.getMetadata();
        return m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
    }

    /** The playback position now, moved on by the time since the state was reported while it plays. */
    long positionMs() {
        PlaybackState s = controller == null ? null : controller.getPlaybackState();
        if (s == null || s.getPosition() < 0) return 0;
        long p = s.getPosition();
        if (s.getState() == PlaybackState.STATE_PLAYING) {
            long since = android.os.SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime();
            p += (long) (since * s.getPlaybackSpeed());
        }
        long d = durationMs();
        return d > 0 ? Math.max(0, Math.min(p, d)) : Math.max(0, p);
    }

    boolean canSkipNext() { return can(PlaybackState.ACTION_SKIP_TO_NEXT); }

    boolean canSkipPrevious() { return can(PlaybackState.ACTION_SKIP_TO_PREVIOUS); }

    private boolean can(long action) {
        PlaybackState s = controller == null ? null : controller.getPlaybackState();
        return s != null && (s.getActions() & action) != 0;
    }

    // ---- controls -------------------------------------------------------------------------------

    void playPause() {
        if (controller == null) return;
        MediaController.TransportControls t = controller.getTransportControls();
        if (playing()) t.pause();
        else t.play();
    }

    void next() {
        if (controller != null) controller.getTransportControls().skipToNext();
    }

    void previous() {
        if (controller != null) controller.getTransportControls().skipToPrevious();
    }

    MediaController controller() { return controller; }
}
