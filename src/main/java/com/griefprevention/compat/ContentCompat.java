package com.griefprevention.compat;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime-safe checks for content newer than the API GriefPrevention compiles against. Names are matched as strings so
 * the plugin still loads on servers whose {@link Material} and entity types don't have these constants.
 */
public final class ContentCompat {

    private static final String CUSHION_ITEM_TAG = "ITEMS_CUSHIONS";
    private static final String CUSHION_ENTITY = "CUSHION";
    private static final String STRAW_BED = "STRAW_BED";

    private ContentCompat() {
    }

    /**
     * Cushions (26.3+) are dyed sitting entities placed from an item, one per colour.
     */
    public static boolean isCushionItem(@Nullable Material material) {
        if (material == null) return false;
        return MaterialTagCompat.isTagged(CUSHION_ITEM_TAG, material) || isCushionItemName(material.name());
    }

    static boolean isCushionItemName(@NotNull String materialName) {
        return materialName.endsWith("_CUSHION");
    }

    /**
     * Whether an entity is a placed cushion. Cushions are entities, not blocks, so breaking and sitting on one are
     * entity actions.
     */
    public static boolean isCushionEntity(@Nullable Entity entity) {
        return entity != null && isCushionEntityName(entity.getType().name());
    }

    static boolean isCushionEntityName(@NotNull String entityTypeName) {
        return entityTypeName.equals(CUSHION_ENTITY);
    }

    /**
     * Straw beds (26.3+) are blocks that set a spawn point like a bed but break when slept in. They are not part of
     * the vanilla {@code BEDS} tag, so they need their own check.
     */
    public static boolean isStrawBed(@Nullable Material material) {
        return material != null && isStrawBedName(material.name());
    }

    static boolean isStrawBedName(@NotNull String materialName) {
        return materialName.equals(STRAW_BED);
    }
}
