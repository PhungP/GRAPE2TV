package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * The interface style chosen in Launcher settings: the stock look, Apple's "Liquid Glass" or the flat,
 * rounded-square look of Sony's Xperia home.
 *
 * A style is a small preset of the same keys a theme's config.json carries ({@link #overlay}), plus a few
 * things config keys cannot say: the pill shapes, the panel shape and the tile corner radius. The choice
 * sits in the launcher's own preferences, so it survives theme pushes and is reset with "Follow theme".
 */
final class UiStyle {
    static final String DEFAULT = "default";
    static final String LIQUID_GLASS = "liquid_glass";
    static final String XPERIA = "xperia";
    /** SharedPreferences key (Config.SETTINGS_NAME). */
    static final String KEY = "ui_style";

    static final String[] ALL = {DEFAULT, LIQUID_GLASS, XPERIA};

    /** Tile corner radius multiplier for the style in use; set once per Home build in MainActivity. */
    private static float radiusScale = 1f;

    private UiStyle() {
    }

    /** A known style id, or the default for anything else. */
    static String normalize(String value) {
        if (value == null) return DEFAULT;
        String v = value.trim();
        if (LIQUID_GLASS.equalsIgnoreCase(v)) return LIQUID_GLASS;
        return XPERIA.equalsIgnoreCase(v) ? XPERIA : DEFAULT;
    }

    static String chosen(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        return normalize(prefs.getString(KEY, DEFAULT));
    }

    static float radiusScaleFor(String style) {
        switch (normalize(style)) {
            case LIQUID_GLASS: return 1.6f;
            case XPERIA: return 0.62f;
            default: return 1f;
        }
    }

    static void use(String style) {
        radiusScale = radiusScaleFor(style);
    }

    static float radiusScale() {
        return radiusScale;
    }

    /**
     * The theme's JSON with the style's keys laid over it. Keys that decide which apps appear and where
     * (dock_apps, columns, wallpaper ...) are never touched: a style changes the look, not the content.
     * The default style returns the theme as it is, which may be null (no theme pushed).
     */
    static JSONObject overlay(JSONObject theme, String style) {
        String s = normalize(style);
        if (DEFAULT.equals(s)) return theme;
        JSONObject out;
        try {
            out = theme == null ? new JSONObject() : new JSONObject(theme.toString());
            if (LIQUID_GLASS.equals(s)) {
                out.put("glass_style", Config.GLASS_LIQUID);
                out.put("background_color", "#0A0F1F");
                out.put("sky_intensity", 60);
                out.put("ambient_glow", true);
                out.put("app_glow", true);
                out.put("glow_style", "horizon");
                out.put("dock_glass", true);
                out.put("dock_even_padding", true);
                out.put("header_white", true);
                out.put("fade_rows", false);
                out.put("focus_overshoot", false);
            } else {
                out.put("glass_style", Config.GLASS_XPERIA);
                out.put("background_color", "#000000");
                out.put("sky_intensity", 0);
                out.put("ambient_glow", false);
                out.put("app_glow", false);
                out.put("glow_style", "radial");
                out.put("dock_glass", false);
                out.put("dock_even_padding", true);
                out.put("header_white", true);
                out.put("fade_rows", false);
                out.put("focus_overshoot", false);
            }
        } catch (JSONException e) {
            return theme;
        }
        return out;
    }

    /** Pill background for the style, or null to keep pill_bg.xml. */
    static StateListDrawable pill(String style, float density) {
        switch (normalize(style)) {
            case LIQUID_GLASS: {
                float r = 21 * density;
                StateListDrawable list = new StateListDrawable();
                list.addState(new int[]{android.R.attr.state_focused},
                        shape(new int[]{0xFFFFFFFF, 0xEBFFFFFF}, 0xFFFFFFFF, 1f * density, r));
                list.addState(new int[]{android.R.attr.state_activated},
                        shape(new int[]{0x596EC1FF, 0x596EC1FF}, 0xFF6EC1FF, 1.5f * density, r));
                list.addState(new int[]{},
                        shape(new int[]{0x42FFFFFF, 0x14FFFFFF}, 0x73FFFFFF, 1.2f * density, r));
                return list;
            }
            case XPERIA: {
                float r = 12 * density;
                StateListDrawable list = new StateListDrawable();
                list.addState(new int[]{android.R.attr.state_focused},
                        shape(new int[]{0xFFFFFFFF, 0xFFFFFFFF}, 0, 0, r));
                list.addState(new int[]{android.R.attr.state_activated},
                        shape(new int[]{0x4D2D9CDB, 0x4D2D9CDB}, 0xFF2D9CDB, 1.5f * density, r));
                list.addState(new int[]{},
                        shape(new int[]{0x26FFFFFF, 0x26FFFFFF}, 0, 0, r));
                return list;
            }
            default:
                return null;
        }
    }

    /** Background for the launcher's own floating panels (now playing); never null. */
    static GradientDrawable panel(String style, float density) {
        switch (normalize(style)) {
            case XPERIA:
                return shape(new int[]{0xF2141414, 0xF2141414}, 0x33FFFFFF, density, 14 * density);
            case LIQUID_GLASS:
                return shape(new int[]{0x6BFFFFFF, 0x24FFFFFF}, 0x99FFFFFF, 1.4f * density, 30 * density);
            default:
                return shape(new int[]{0xF21C1C1E, 0xF21C1C1E}, 0x24FFFFFF, density, 18 * density);
        }
    }

    /** Accent for the progress bar and the play button's ring in the panels. */
    static int accent(String style) {
        switch (normalize(style)) {
            case XPERIA: return 0xFF2D9CDB;
            case LIQUID_GLASS: return 0xFFFFFFFF;
            default: return 0xFF6EC1FF;
        }
    }

    private static GradientDrawable shape(int[] colors, int stroke, float strokePx, float radiusPx) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors);
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radiusPx);
        if (strokePx > 0) d.setStroke(Math.max(1, Math.round(strokePx)), stroke);
        return d;
    }
}
