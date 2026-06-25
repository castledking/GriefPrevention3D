package com.griefprevention.visualization;

import com.griefprevention.test.ServerMocks;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualizationStyleRegistryTest
{
    private VisualizationStyleRegistry registry;

    @BeforeAll
    static void setUpServer()
    {
        Server server = ServerMocks.newServer();
        Bukkit.setServer(server);
    }

    @AfterAll
    static void tearDown()
    {
        ServerMocks.unsetBukkitServer();
    }

    @BeforeEach
    void setUp()
    {
        registry = new VisualizationStyleRegistry();
    }

    @Test
    void builtInTypesAreRegisteredByDefault()
    {
        for (VisualizationType type : VisualizationType.values())
        {
            VisualizationStyle style = registry.get(type.getKey());
            assertNotNull(style, "Built-in type should be registered: " + type.getKey());
            assertEquals(type, style);
        }
    }

    @Test
    void lookupIsCaseInsensitive()
    {
        VisualizationStyle lower = registry.get("griefprevention:claim");
        VisualizationStyle upper = registry.get("GRIEFPREVENTION:CLAIM");
        VisualizationStyle mixed = registry.get("GriefPrevention:Claim");

        assertNotNull(lower);
        assertEquals(lower, upper);
        assertEquals(lower, mixed);
    }

    @Test
    void registerCustomStyle()
    {
        VisualizationStyle custom = new VisualizationStyle()
        {
            @Override
            public @NotNull String getKey()
            {
                return "myaddon:custom_style";
            }
        };

        registry.register(custom);
        VisualizationStyle retrieved = registry.get("myaddon:custom_style");

        assertNotNull(retrieved);
        assertEquals(custom, retrieved);
    }

    @Test
    void registerOverwritesExisting()
    {
        VisualizationStyle replacement = new VisualizationStyle()
        {
            @Override
            public @NotNull String getKey()
            {
                return "griefprevention:claim";
            }
        };

        registry.register(replacement);
        VisualizationStyle retrieved = registry.get("griefprevention:claim");

        assertEquals(replacement, retrieved);
    }

    @Test
    void unregisterCustomStyleRemovesIt()
    {
        VisualizationStyle custom = new VisualizationStyle()
        {
            @Override
            public @NotNull String getKey()
            {
                return "addon:temp";
            }
        };

        registry.register(custom);
        assertNotNull(registry.get("addon:temp"));

        registry.unregister("addon:temp");
        assertNull(registry.get("addon:temp"));
    }

    @Test
    void unregisterBuiltInResetsToDefault()
    {
        VisualizationStyle replacement = new VisualizationStyle()
        {
            @Override
            public @NotNull String getKey()
            {
                return "griefprevention:claim";
            }
        };

        registry.register(replacement);
        assertEquals(replacement, registry.get("griefprevention:claim"));

        registry.unregister("griefprevention:claim");
        VisualizationStyle afterUnregister = registry.get("griefprevention:claim");

        assertNotNull(afterUnregister);
        assertEquals(VisualizationType.CLAIM, afterUnregister);
    }

    @Test
    void getStylesReturnsAllRegistered()
    {
        List<VisualizationStyle> styles = registry.getStyles();

        assertTrue(styles.size() >= VisualizationType.values().length);
    }

    @Test
    void unknownKeyReturnsNull()
    {
        assertNull(registry.get("nonexistent:key"));
    }
}
