package com.gathin.tvhome;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The local weather for Home's header, from Open-Meteo (no account or key).
 *
 * <p>This is the only network code in the launcher. It runs only while Home is on screen: {@link #start} in
 * onResume, {@link #stop} in onPause, one request at most every 30 minutes (5 after a failure) on a short-lived
 * thread, and the last reading is kept in the launcher's preferences so the header is filled at once.
 *
 * <p>Where "local" is, in order: the city in config.xml's weather_city (or the theme's "weather_city"); the
 * TV's last known location when the location permission is granted; otherwise the place the TV's public IP
 * address points to (ipwho.is). Set weather_city to avoid the IP lookup. The place is kept for a day.
 */
final class WeatherFeed {
    interface Listener {
        /** On the main thread. */
        void onWeather(Weather weather);
    }

    private static final String TAG = "TvHomeWeather";
    private static final String PREFS = "weather_cache";
    private static final long REFRESH_MS = 30L * 60 * 1000;
    private static final long RETRY_MS = 5L * 60 * 1000;
    private static final long PLACE_KEEP_MS = 24L * 60 * 60 * 1000;
    private static final int TIMEOUT_MS = 8000;

    private final Context app;
    private final Handler main;
    private final Listener listener;
    private final String city;
    private final String language;
    private final AtomicBoolean busy = new AtomicBoolean();
    private final SharedPreferences prefs;
    private boolean running;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            fetch();
        }
    };

    WeatherFeed(Context context, Handler main, String city, Listener listener) {
        this.app = context.getApplicationContext();
        this.main = main;
        this.city = city == null ? "" : city.trim();
        this.listener = listener;
        this.language = Locale.getDefault().getLanguage();
        this.prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Shows the kept reading now, and refreshes it when it is older than 30 minutes. */
    void start() {
        running = true;
        Weather cached = cached();
        if (cached != null) listener.onWeather(cached);
        long age = cached == null ? Long.MAX_VALUE : System.currentTimeMillis() - cached.time;
        main.removeCallbacks(tick);
        if (age >= REFRESH_MS || age < 0) fetch();
        else main.postDelayed(tick, REFRESH_MS - age);
    }

    void stop() {
        running = false;
        main.removeCallbacks(tick);
    }

    private void fetch() {
        if (!busy.compareAndSet(false, true)) return;
        Thread t = new Thread(() -> {
            Weather result = null;
            try {
                result = load();
            } catch (Exception e) {
                Log.w(TAG, "weather: " + e.getClass().getSimpleName() + " " + e.getMessage());
            }
            final Weather w = result;
            busy.set(false);
            main.post(() -> {
                if (w != null) {
                    store(w);
                    if (running) listener.onWeather(w);
                }
                if (running) {
                    main.removeCallbacks(tick);
                    main.postDelayed(tick, w != null ? REFRESH_MS : RETRY_MS);
                }
            });
        }, "tvhome-weather");
        t.setDaemon(true);
        t.start();
    }

    // ---- network (background thread) -------------------------------------------------------------

    private Weather load() throws Exception {
        double[] where = new double[2];
        String name = place(where);
        if (name == null) return null;
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + where[0] + "&longitude=" + where[1]
                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m"
                + "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto";
        JSONObject root = new JSONObject(get(url));
        JSONObject cur = root.getJSONObject("current");
        JSONObject daily = root.optJSONObject("daily");
        double min = cur.getDouble("temperature_2m"), max = min;
        if (daily != null) {
            JSONArray lo = daily.optJSONArray("temperature_2m_min");
            JSONArray hi = daily.optJSONArray("temperature_2m_max");
            if (lo != null && lo.length() > 0) min = lo.optDouble(0, min);
            if (hi != null && hi.length() > 0) max = hi.optDouble(0, max);
        }
        return new Weather(cur.getDouble("temperature_2m"),
                cur.optDouble("apparent_temperature", cur.getDouble("temperature_2m")),
                cur.optInt("relative_humidity_2m", -1), cur.optDouble("wind_speed_10m", 0),
                cur.optInt("weather_code", 3), cur.optInt("is_day", 1) == 1, min, max, name.isEmpty() ? "" : name,
                System.currentTimeMillis());
    }

    /**
     * Latitude and longitude into {@code out}; returns the place name ("" when unnamed), or null when no place
     * could be worked out.
     */
    private String place(double[] out) throws Exception {
        long now = System.currentTimeMillis();
        String keyed = prefs.getString("place_key", "");
        String key = city.isEmpty() ? "auto" : "city:" + city.toLowerCase(Locale.ROOT);
        if (key.equals(keyed) && now - prefs.getLong("place_time", 0) < PLACE_KEEP_MS && prefs.contains("place_lat")) {
            out[0] = Double.longBitsToDouble(prefs.getLong("place_lat", 0));
            out[1] = Double.longBitsToDouble(prefs.getLong("place_lon", 0));
            return prefs.getString("place_name", "");
        }
        String name;
        if (!city.isEmpty()) {
            JSONObject geo = new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name="
                    + URLEncoder.encode(city, "UTF-8") + "&count=1&format=json&language=" + language));
            JSONArray results = geo.optJSONArray("results");
            if (results == null || results.length() == 0) return null;
            JSONObject first = results.getJSONObject(0);
            out[0] = first.getDouble("latitude");
            out[1] = first.getDouble("longitude");
            name = first.optString("name", city);
        } else if (lastKnown(out)) {
            name = "";
        } else {
            JSONObject ip = new JSONObject(get("https://ipwho.is/?fields=success,latitude,longitude,city"));
            if (!ip.optBoolean("success", false)) return null;
            out[0] = ip.getDouble("latitude");
            out[1] = ip.getDouble("longitude");
            name = ip.optString("city", "");
        }
        prefs.edit().putString("place_key", key).putLong("place_time", now)
                .putLong("place_lat", Double.doubleToLongBits(out[0]))
                .putLong("place_lon", Double.doubleToLongBits(out[1]))
                .putString("place_name", name).apply();
        return name;
    }

    private boolean lastKnown(double[] out) {
        if (app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        try {
            LocationManager lm = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return false;
            Location best = null;
            for (String provider : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            if (best == null) return false;
            out[0] = best.getLatitude();
            out[1] = best.getLongitude();
            return true;
        } catch (SecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    private static String get(String address) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        try {
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "tvhome-launcher");
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > 256 * 1024) throw new IOException("response too large");
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            c.disconnect();
        }
    }

    // ---- the kept reading ------------------------------------------------------------------------

    private void store(Weather w) {
        prefs.edit().putLong("t", Double.doubleToLongBits(w.tempC)).putLong("f", Double.doubleToLongBits(w.feelsC))
                .putInt("h", w.humidity).putLong("w", Double.doubleToLongBits(w.windKmh)).putInt("c", w.code)
                .putBoolean("d", w.day).putLong("lo", Double.doubleToLongBits(w.minC))
                .putLong("hi", Double.doubleToLongBits(w.maxC)).putString("p", w.place).putLong("time", w.time)
                .apply();
    }

    private Weather cached() {
        long time = prefs.getLong("time", 0);
        if (!Weather.fresh(time, System.currentTimeMillis()) || !prefs.contains("t")) return null;
        // A reading kept for another city must not show under this one.
        String key = city.isEmpty() ? "auto" : "city:" + city.toLowerCase(Locale.ROOT);
        if (!key.equals(prefs.getString("place_key", key))) return null;
        return new Weather(d("t"), d("f"), prefs.getInt("h", -1), d("w"), prefs.getInt("c", 3), prefs.getBoolean("d", true),
                d("lo"), d("hi"), prefs.getString("p", ""), time);
    }

    private double d(String key) {
        return Double.longBitsToDouble(prefs.getLong(key, 0));
    }
}
