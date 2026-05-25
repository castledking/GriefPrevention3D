package com.griefprevention.visualization;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Registry for boundary visualization styles.
 */
public final class VisualizationStyleRegistry
{
    private final Map<String, VisualizationStyle> styles = new LinkedHashMap<>();

    public VisualizationStyleRegistry()
    {
        for (VisualizationType type : VisualizationType.values())
        {
            register(type);
        }
    }

    public void register(@NotNull VisualizationStyle style)
    {
        Objects.requireNonNull(style, "style");
        styles.put(normalize(style.getKey()), style);
    }

    public void unregister(@NotNull String key)
    {
        String normalizedKey = normalize(key);
        VisualizationType builtIn = VisualizationType.fromKey(normalizedKey);
        if (builtIn != null)
        {
            styles.put(normalizedKey, builtIn);
            return;
        }

        styles.remove(normalizedKey);
    }

    public @Nullable VisualizationStyle get(@NotNull String key)
    {
        return styles.get(normalize(key));
    }

    public @NotNull List<VisualizationStyle> getStyles()
    {
        return new ArrayList<>(styles.values());
    }

    private static @NotNull String normalize(@NotNull String key)
    {
        return Objects.requireNonNull(key, "key").trim().toLowerCase(Locale.ROOT);
    }
}
