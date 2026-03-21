package com.griefprevention.visualization;

import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A named style for claim boundary visualization.
 */
public interface VisualizationStyle
{

    /**
     * Get the namespaced key for this style.
     *
     * @return the style key
     */
    @NotNull NamespacedKey getKey();

    /**
     * Get the block-based renderer for this style, if any.
     *
     * <p>This is used by the built-in fake block visualization providers. Styles without a block renderer may still be
     * handled by custom {@link VisualizationProvider visualization providers} supplied through
     * {@link com.griefprevention.events.BoundaryVisualizationEvent}.</p>
     *
     * @return the block renderer, or {@code null} if not available
     */
    default @Nullable BlockBoundaryRenderer getBlockRenderer()
    {
        return null;
    }

}
