package com.gathin.tvhome;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The pure parts of the header weather: WMO codes, units, rounding and how long a reading is kept. */
public class WeatherTest {

    @Test
    public void wmoCodesMapToCategories() {
        assertEquals(Weather.CLEAR, Weather.category(0));
        assertEquals(Weather.CLEAR, Weather.category(1));
        assertEquals(Weather.PARTLY, Weather.category(2));
        assertEquals(Weather.CLOUDY, Weather.category(3));
        assertEquals(Weather.FOG, Weather.category(45));
        assertEquals(Weather.FOG, Weather.category(48));
        assertEquals(Weather.DRIZZLE, Weather.category(53));
        assertEquals(Weather.RAIN, Weather.category(63));
        assertEquals(Weather.RAIN, Weather.category(81));
        assertEquals(Weather.SNOW, Weather.category(73));
        assertEquals(Weather.SNOW, Weather.category(86));
        assertEquals(Weather.STORM, Weather.category(95));
        assertEquals(Weather.STORM, Weather.category(99));
    }

    @Test
    public void unknownCodeIsCloudy() {
        assertEquals(Weather.CLOUDY, Weather.category(-1));
        assertEquals(Weather.CLOUDY, Weather.category(1234));
    }

    @Test
    public void temperatureRoundsAndConverts() {
        assertEquals("27\u00B0", Weather.formatTemp(27.4, false));
        assertEquals("28\u00B0", Weather.formatTemp(27.5, false));
        assertEquals("-3\u00B0", Weather.formatTemp(-3.2, false));
        assertEquals("0\u00B0", Weather.formatTemp(-0.2, false));
        assertEquals("32\u00B0", Weather.formatTemp(0, true));
        assertEquals("81\u00B0", Weather.formatTemp(27.2, true));
    }

    @Test
    public void fahrenheitOnlyWhereTheRegionUsesIt() {
        assertTrue(Weather.useFahrenheit(Locale.US));
        assertFalse(Weather.useFahrenheit(new Locale("vi", "VN")));
        assertFalse(Weather.useFahrenheit(Locale.FRANCE));
        assertFalse(Weather.useFahrenheit(null));
    }

    @Test
    public void aReadingExpiresAfterThreeHours() {
        long now = 10_000_000_000L;
        assertTrue(Weather.fresh(now - 60_000, now));
        assertTrue(Weather.fresh(now - Weather.MAX_AGE_MS, now));
        assertFalse(Weather.fresh(now - Weather.MAX_AGE_MS - 1, now));
        assertFalse(Weather.fresh(0, now));
        assertFalse(Weather.fresh(now + 1, now));   // a clock that moved back is not "fresh"
    }
}
