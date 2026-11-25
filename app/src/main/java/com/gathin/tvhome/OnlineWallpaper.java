package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * A picture for Home's background from a free source that needs no account or key, picked in Launcher settings:
 * Bing's picture of the day, Wikimedia Commons' picture of the day, or a random photo from Lorem Picsum.
 * The picture is fetched at most once a day while Home is on screen, kept as one file in the app's private storage
 * and decoded to the screen's size. "Off" leaves the theme's own wallpaper or colour alone.
 */
final class OnlineWallpaper {
    static final String KEY = "wallpaper_source";
    static final String OFF = "off", BING = "bing", WIKIMEDIA = "wikimedia", PICSUM = "picsum";
    static final String[] ALL = {OFF, BING, WIKIMEDIA, PICSUM};

    private static final String META = "online_wallpaper";
    private static final long REFRESH_MS = 24L * 60 * 60 * 1000;
    private static final long MAX_BYTES = 14L * 1024 * 1024;
    private static final Pattern THUMB_WIDTH = Pattern.compile("/\\d+px-");

    private OnlineWallpaper() {
    }

    static String normalize(String value) {
        if (value != null) {
            for (String id : ALL) if (id.equalsIgnoreCase(value.trim())) return id;
        }
        return OFF;
    }

    static String chosen(Context context) {
        return normalize(context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getString(KEY, OFF));
    }

    static File file(Context context) {
        return new File(context.getFilesDir(), "online_wallpaper.jpg");
    }

    /** Makes the next {@link #refresh} fetch a new picture whatever its age. */
    static void invalidate(Context context) {
        context.getSharedPreferences(META, Context.MODE_PRIVATE).edit().remove("time").apply();
    }

    static boolean needsRefresh(Context context, String source) {
        if (OFF.equals(source) || !file(context).isFile()) return !OFF.equals(source);
        SharedPreferences meta = context.getSharedPreferences(META, Context.MODE_PRIVATE);
        long age = System.currentTimeMillis() - meta.getLong("time", 0);
        return !source.equals(meta.getString("source", "")) || age < 0 || age >= REFRESH_MS;
    }

    /** Fetches a new picture when the chosen source needs one; {@code done} runs on the main thread when it changed. */
    static void refresh(Context context, Handler main, Runnable done) {
        final Context app = context.getApplicationContext();
        final String source = chosen(app);
        if (!needsRefresh(app, source)) return;
        Thread t = new Thread(() -> {
            try {
                File dest = file(app);
                Net.download(imageUrl(source), dest, MAX_BYTES, null);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(dest.getPath(), o);
                if (o.outWidth <= 0) {
                    dest.delete();
                    return;
                }
                app.getSharedPreferences(META, Context.MODE_PRIVATE).edit()
                        .putString("source", source).putLong("time", System.currentTimeMillis()).apply();
                main.post(done);
            } catch (Exception e) {
                // Offline or the source changed: keep the picture already there, try again next time.
            }
        }, "tvhome-wallpaper");
        t.setDaemon(true);
        t.start();
    }

    /** Decodes the kept picture to about {@code maxW} x {@code maxH} on a background thread; null when there is none. */
    static void load(Context context, int maxW, int maxH, Handler main, Consumer<Bitmap> result) {
        final File f = file(context);
        Thread t = new Thread(() -> {
            Bitmap b = decode(f, maxW, maxH);
            main.post(() -> result.accept(b));
        }, "tvhome-wallpaper-decode");
        t.setDaemon(true);
        t.start();
    }

    static Bitmap decode(File f, int maxW, int maxH) {
        if (!f.isFile()) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            if (o.outWidth <= 0) return null;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= maxW && o.outHeight / (sample * 2) >= maxH) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    // ---- the three sources ---------------------------------------------------------------------

    private static String imageUrl(String source) throws Exception {
        switch (source) {
            case BING: {
                JSONObject root = new JSONObject(Net.getString(
                        "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=en-US", "application/json"));
                String path = root.getJSONArray("images").getJSONObject(0).getString("url");
                return path.startsWith("http") ? path : "https://www.bing.com" + path;
            }
            case WIKIMEDIA: {
                // The featured feed of a day carries the day's picture; today's may not be out yet, so try yesterday's too.
                LocalDate day = LocalDate.now(ZoneOffset.UTC);
                for (int back = 0; back < 3; back++) {
                    LocalDate d = day.minusDays(back);
                    try {
                        String url = wikimediaPicture(Net.getString(String.format(Locale.ROOT,
                                "https://api.wikimedia.org/feed/v1/wikipedia/en/featured/%04d/%02d/%02d",
                                d.getYear(), d.getMonthValue(), d.getDayOfMonth()), "application/json"));
                        if (url != null) return url;
                    } catch (IOException e) {
                        // try the day before
                    }
                }
                throw new IOException("no Wikimedia picture");
            }
            default:
                return "https://picsum.photos/1920/1080.jpg";
        }
    }

    private static String wikimediaPicture(String json) throws Exception {
        JSONObject image = new JSONObject(json).optJSONObject("image");
        if (image == null) return null;
        JSONObject thumb = image.optJSONObject("thumbnail");
        if (thumb != null && thumb.optString("source").startsWith("https://")) {
            // The feed gives a small thumbnail; the same URL with a wider size is the 1920 px rendition.
            return THUMB_WIDTH.matcher(thumb.getString("source")).replaceFirst("/1920px-");
        }
        return null;
    }
}
