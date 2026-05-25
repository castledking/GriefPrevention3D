package com.griefprevention.visualization;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A named style for claim boundary visualization.
 *
 * <p>Keys should use a namespaced string such as {@code griefprevention:claim}
 * or {@code myaddon:custom_style}. This intentionally avoids Bukkit's
 * {@code NamespacedKey} type so the Bukkit-family jar can still load on legacy
 * servers where that class does not exist.</p>
 */
public interface VisualizationStyle
{
    /**
     * Get this style's stable key.
     *
     * @return the style key
     */
    @NotNull String getKey();

    /**
     * Get the block-based renderer for this style, if any.
     *
     * <p>Built-in fake-block visualizations use this renderer. Styles without a
     * block renderer can still be handled by a custom {@link VisualizationProvider}
     * supplied through {@link com.griefprevention.events.BoundaryVisualizationEvent}.</p>
     *
     * @return the block renderer, or {@code null} if not available
     */
    default @Nullable BlockBoundaryRenderer getBlockRenderer()
    {
        return null;
    }
}
