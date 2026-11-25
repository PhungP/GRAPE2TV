package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * The month under the clock on Home: the month and year, the weekday letters and the days, today in a white
 * disc. Drawn by hand (no CalendarView or grid of TextViews) so it costs one small view; it redraws itself
 * at midnight. The week starts on the day the TV's region starts it (Monday in Vietnam).
 */
public class MonthCalendarView extends View {
    /** SharedPreferences key (Config.SETTINGS_NAME) of the on-TV switch; config.xml's show_calendar is its default. */
    static final String KEY = "show_calendar";

    static boolean enabled(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY, ctx.getResources().getBoolean(R.bool.show_calendar));
    }

    private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint head = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint day = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final float cellW, cellH, titleH;
    private final Runnable midnight = this::refresh;

    public MonthCalendarView(Context context) {
        this(context, null);
    }

    public MonthCalendarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        cellW = 27f * density;
        cellH = 16f * density;
        titleH = 20f * density;
        title.setTextSize(12f * density);
        title.setColor(0xE6FFFFFF);
        title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        head.setTextSize(9.5f * density);
        head.setColor(0x80FFFFFF);
        head.setTextAlign(Paint.Align.CENTER);
        day.setTextSize(11f * density);
        day.setTextAlign(Paint.Align.CENTER);
        disc.setColor(0xFFFFFFFF);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void refresh() {
        invalidate();
        scheduleMidnight();
    }

    private void scheduleMidnight() {
        removeCallbacks(midnight);
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 5);
        c.set(Calendar.MILLISECOND, 0);
        postDelayed(midnight, Math.max(1000L, c.getTimeInMillis() - System.currentTimeMillis()));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        scheduleMidnight();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(midnight);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(Math.round(cellW * 7), Math.round(titleH + cellH * 7 + 2 * density));
    }

    /** "T2".."CN" in Vietnamese; the first two letters of the weekday's short name elsewhere. */
    static String weekdayLabel(Locale locale, int calendarDay) {
        if ("vi".equals(locale.getLanguage())) {
            return calendarDay == Calendar.SUNDAY ? "CN" : "T" + (calendarDay);
        }
        Calendar c = Calendar.getInstance(locale);
        String s = c.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, locale);
        if (s == null || s.isEmpty()) s = "?";
        return s.length() > 2 ? s.substring(0, 2) : s;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Locale locale = Locale.getDefault();
        Calendar now = Calendar.getInstance(locale);
        int today = now.get(Calendar.DAY_OF_MONTH);
        Calendar first = (Calendar) now.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        int firstDow = Calendar.getInstance(locale).getFirstDayOfWeek();
        int offset = (first.get(Calendar.DAY_OF_WEEK) - firstDow + 7) % 7;
        int days = now.getActualMaximum(Calendar.DAY_OF_MONTH);

        String month = new SimpleDateFormat("LLLL yyyy", locale).format(now.getTime());
        if (!month.isEmpty()) month = month.substring(0, 1).toUpperCase(locale) + month.substring(1);
        canvas.drawText(month, 2f * density, titleH - 6f * density, title);

        for (int i = 0; i < 7; i++) {
            int dow = (firstDow - 1 + i) % 7 + 1;   // Calendar.SUNDAY (1) .. SATURDAY (7)
            head.setColor(dow == Calendar.SUNDAY ? 0xB3FF8A80 : 0x80FFFFFF);
            canvas.drawText(weekdayLabel(locale, dow), cellW * i + cellW / 2f, titleH + cellH - 4f * density, head);
        }

        Paint.FontMetrics fm = day.getFontMetrics();
        float baseline = -(fm.ascent + fm.descent) / 2f;
        for (int d = 1; d <= days; d++) {
            int slot = offset + d - 1;
            int col = slot % 7, row = slot / 7 + 1;
            float cx = cellW * col + cellW / 2f;
            float cy = titleH + cellH * (row + 0.5f) + 1f * density;
            int dow = (firstDow - 1 + col) % 7 + 1;
            if (d == today) {
                canvas.drawCircle(cx, cy, cellH * 0.5f, disc);
                day.setColor(0xFF111111);
                day.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                day.setColor(dow == Calendar.SUNDAY ? 0xB3FF8A80 : 0xD9FFFFFF);
                day.setTypeface(Typeface.DEFAULT);
            }
            canvas.drawText(Integer.toString(d), cx, cy + baseline, day);
        }
    }
}
