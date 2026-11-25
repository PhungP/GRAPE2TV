package com.gathin.tvhome;

import android.content.Context;

import java.util.Locale;

/** One weather reading for Home's header: plain data and the pure helpers around it (unit tested). */
final class Weather {
    /** SharedPreferences key (Config.SETTINGS_NAME) of the on-TV switch; config.xml's show_weather is its default. */
    static final String KEY = "show_weather";

    static boolean enabled(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY, ctx.getResources().getBoolean(R.bool.show_weather));
    }

    static final int CLEAR = 0, PARTLY = 1, CLOUDY = 2, FOG = 3, DRIZZLE = 4, RAIN = 5, SNOW = 6, STORM = 7;

    final double tempC;
    final double feelsC;
    final int humidity;
    final double windKmh;
    final int code;
    final boolean day;
    final double minC;
    final double maxC;
    /** The city, or "" when the place came from the TV's last known location and has no name. */
    final String place;
    /** System.currentTimeMillis() when the reading was taken. */
    final long time;

    Weather(double tempC, double feelsC, int humidity, double windKmh, int code, boolean day,
            double minC, double maxC, String place, long time) {
        this.tempC = tempC;
        this.feelsC = feelsC;
        this.humidity = humidity;
        this.windKmh = windKmh;
        this.code = code;
        this.day = day;
        this.minC = minC;
        this.maxC = maxC;
        this.place = place == null ? "" : place;
        this.time = time;
    }

    /** WMO weather interpretation code (Open-Meteo's weather_code) to one of the categories above. */
    static int category(int code) {
        if (code == 0 || code == 1) return CLEAR;
        if (code == 2) return PARTLY;
        if (code == 3) return CLOUDY;
        if (code == 45 || code == 48) return FOG;
        if (code >= 51 && code <= 57) return DRIZZLE;
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return RAIN;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return SNOW;
        if (code >= 95 && code <= 99) return STORM;
        return CLOUDY;
    }

    /** Fahrenheit only where the TV's region uses it (US, Liberia, Myanmar); Celsius everywhere else. */
    static boolean useFahrenheit(Locale locale) {
        String c = locale == null ? "" : locale.getCountry();
        return "US".equals(c) || "LR".equals(c) || "MM".equals(c);
    }

    /** "27°" or "81°", rounded, with no unit letter: the header is small and the unit follows the region. */
    static String formatTemp(double celsius, boolean fahrenheit) {
        double v = fahrenheit ? celsius * 9.0 / 5.0 + 32.0 : celsius;
        long n = Math.round(v);
        return n + "\u00B0";
    }

    /** A reading older than this is no longer shown: a stale temperature is worse than none. */
    static final long MAX_AGE_MS = 3L * 60 * 60 * 1000;

    static boolean fresh(long readingTime, long now) {
        return readingTime > 0 && now - readingTime >= 0 && now - readingTime <= MAX_AGE_MS;
    }
}
