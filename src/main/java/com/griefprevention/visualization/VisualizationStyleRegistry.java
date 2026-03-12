package com.griefprevention.visualization;

import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for visualization styles.
 */
public final class VisualizationStyleRegistry
{

    private final Map<NamespacedKey, VisualizationStyle> styles = new LinkedHashMap<>();

    public VisualizationStyleRegistry()
    {
        for (VisualizationType type : VisualizationType.values())
        {
            register(type);
        }
    }

    public void register(@NotNull VisualizationStyle style)
    {
        styles.put(style.getKey(), style);
    }

    public void unregister(@NotNull NamespacedKey key)
    {
        VisualizationType builtIn = VisualizationType.fromKey(key);
        if (builtIn != null)
        {
            styles.put(key, builtIn);
            return;
        }

        styles.remove(key);
    }

    public @Nullable VisualizationStyle get(@NotNull NamespacedKey key)
    {
        return styles.get(key);
    }

    public @NotNull List<VisualizationStyle> getStyles()
    {
        return new ArrayList<>(styles.values());
    }

}
