package com.griefprevention.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ContentCompatTest {

    @Test
    void everyDyedCushionItemIsRecognised() {
        for (String colour : new String[] {"WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK",
                "GRAY", "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"}) {
            assertTrue(ContentCompat.isCushionItemName(colour + "_CUSHION"), colour);
        }
    }

    @Test
    void otherItemsAreNotCushions() {
        assertFalse(ContentCompat.isCushionItemName("CUSHION_SIT"));
        assertFalse(ContentCompat.isCushionItemName("WHITE_BED"));
        assertFalse(ContentCompat.isCushionItemName("WHITE_WOOL"));
    }

    @Test
    void onlyThePlacedCushionEntityIsRecognised() {
        assertTrue(ContentCompat.isCushionEntityName("CUSHION"));
        assertFalse(ContentCompat.isCushionEntityName("WHITE_CUSHION"));
        assertFalse(ContentCompat.isCushionEntityName("ARMOR_STAND"));
    }

    @Test
    void strawBedsAreRecognisedAndOrdinaryBedsAreNot() {
        assertTrue(ContentCompat.isStrawBedName("STRAW_BED"));
        assertFalse(ContentCompat.isStrawBedName("RED_BED"));
        assertFalse(ContentCompat.isStrawBedName("STRAW_BED_BLOCK"));
    }
}
