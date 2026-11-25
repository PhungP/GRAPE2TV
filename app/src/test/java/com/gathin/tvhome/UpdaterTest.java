package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Version comparison for "Check for updates" and the time text of the now-playing card. */
public class UpdaterTest {

    @Test
    public void tagsLoseTheirV() {
        assertEquals("1.0.0", Updater.stripV("v1.0.0"));
        assertEquals("1.0.0", Updater.stripV("V1.0.0"));
        assertEquals("1.0.0", Updater.stripV(" 1.0.0 "));
        assertEquals("", Updater.stripV(null));
    }

    @Test
    public void newerVersionsAreNewer() {
        assertTrue(Updater.isNewer("1.1.0", "1.0.0"));
        assertTrue(Updater.isNewer("v1.0.1", "1.0.0"));
        assertTrue(Updater.isNewer("2.0", "1.9.9"));
        assertTrue(Updater.isNewer("1.10.0", "1.9.0"));   // numbers, not text
    }

    @Test
    public void sameOrOlderIsNotNewer() {
        assertFalse(Updater.isNewer("1.0.0", "1.0.0"));
        assertFalse(Updater.isNewer("v1.0", "1.0.0"));    // a missing part counts as 0
        assertFalse(Updater.isNewer("1.0.0", "1.0.1"));
        assertFalse(Updater.isNewer("", "1.0.0"));
    }

    @Test
    public void timeText() {
        assertEquals("0:48", NowPlayingPanel.formatTime(48_000));
        assertEquals("2:32", NowPlayingPanel.formatTime(152_000));
        assertEquals("1:04:09", NowPlayingPanel.formatTime(3_849_000));
        assertEquals("0:00", NowPlayingPanel.formatTime(-5));
    }
}
