package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The interface style id, and the tile corner radius each style asks for. */
public class UiStyleTest {

    @Test
    public void knownStylesAnyCase() {
        assertEquals(UiStyle.LIQUID_GLASS, UiStyle.normalize("liquid_glass"));
        assertEquals(UiStyle.LIQUID_GLASS, UiStyle.normalize(" Liquid_Glass "));
        assertEquals(UiStyle.XPERIA, UiStyle.normalize("XPERIA"));
    }

    @Test
    public void anythingElseIsTheDefault() {
        assertEquals(UiStyle.DEFAULT, UiStyle.normalize(null));
        assertEquals(UiStyle.DEFAULT, UiStyle.normalize(""));
        assertEquals(UiStyle.DEFAULT, UiStyle.normalize("default"));
        assertEquals(UiStyle.DEFAULT, UiStyle.normalize("material"));
    }

    @Test
    public void defaultKeepsTheStockCorners() {
        assertEquals(1f, UiStyle.radiusScaleFor(UiStyle.DEFAULT), 0f);
        assertEquals(1f, UiStyle.radiusScaleFor("nonsense"), 0f);
    }

    @Test
    public void liquidGlassIsRounderAndXperiaIsSquarer() {
        assertTrue(UiStyle.radiusScaleFor(UiStyle.LIQUID_GLASS) > 1f);
        assertTrue(UiStyle.radiusScaleFor(UiStyle.XPERIA) < 1f);
    }

    @Test
    public void everyListedStyleIsKnown() {
        for (String id : UiStyle.ALL) assertEquals(id, UiStyle.normalize(id));
    }
}
