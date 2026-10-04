package com.griefprevention.protection;

/**
 * What a player uses by right-clicking a block, in the groups Paper's interaction rules tell apart.
 * {@link BlockUseSettings#requiredTrust} turns a group into the trust a claim asks for.
 */
public enum BlockUseKind
{
    /**
     * Inventories, and the blocks Paper guards like them: cake, cauldrons, anvils, beacons, bells,
     * jukeboxes, respawn anchors, pumpkins, berries, decorated pots. Off with {@code PreventTheft}.
     */
    CONTAINER,
    /** Doors that open by hand. */
    DOOR,
    /** Trapdoors that open by hand. */
    TRAPDOOR,
    FENCE_GATE,
    BED,
    /** Buttons and levers. */
    SWITCH,
    /** Reading a lectern's book. */
    LECTERN,
    /** Taking a lectern's book, or putting one on: container trust, whatever {@code PreventTheft} says. */
    LECTERN_BOOK,
    /**
     * Redstone and decorations a click changes: note blocks, repeaters, comparators, daylight
     * detectors, redstone wire, flower pots, candles, dragon eggs and copper golem statues.
     */
    BUILD,
    /** Crafting tables, enchanting tables and everything else Paper leaves free to use. */
    UNPROTECTED
}
