package com.griefprevention.nukkit;

import cn.nukkit.Nukkit;
import cn.nukkit.permission.Permission;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.plugin.PluginDescription;
import cn.nukkit.utils.Utils;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads the generated nukkit.yml the way Nukkit's plugin loader does. Nukkit's {@link Permission}
 * needs a running server, so permissions are compared as the YAML both platforms parse.
 */
class NukkitDescriptorTest
{
    @Test
    void nukkitStartsTheAdapterAtThisVersion() throws Exception
    {
        PluginDescription description = describe(nukkitDescriptor());

        assertEquals("GriefPrevention3D", description.getName());
        assertEquals(GriefPreventionNukkit.class.getName(), description.getMain());
        assertEquals(System.getProperty("griefprevention3d.version"), description.getVersion());
        assertTrue(PluginBase.class.isAssignableFrom(Class.forName(description.getMain())));
    }

    @Test
    void theRequiredApiIsOneThisNukkitAccepts() throws IOException
    {
        String[] server = Nukkit.API_VERSION.split("\\.");
        boolean compatible = false;
        // PluginManager.loadPlugins: the same major version, and no newer minor version.
        for (String api : describe(nukkitDescriptor()).getCompatibleAPIs())
        {
            String[] required = api.split("\\.");
            assertTrue(api.matches("[0-9]+\\.[0-9]+\\.[0-9]+"), api);
            compatible |= required[0].equals(server[0]) && Integer.parseInt(required[1]) <= Integer.parseInt(server[1]);
        }
        assertTrue(compatible);
    }

    @Test
    void commandsAreRegisteredByTheAdapterNotTheDescriptor() throws IOException
    {
        assertTrue(describe(nukkitDescriptor()).getCommands().isEmpty());
    }

    @Test
    void nukkitDeclaresTheSamePermissionsAsBukkit() throws IOException
    {
        Map<String, Object> bukkit = permissions(bukkitDescriptor());

        assertFalse(bukkit.isEmpty());
        assertEquals(bukkit, permissions(nukkitDescriptor()));
    }

    @Test
    void everyPermissionDefaultIsOneNukkitUnderstands() throws IOException
    {
        // Permission.loadPermission throws on any other value, and the plugin would not load.
        for (Map.Entry<String, Object> permission : permissions(nukkitDescriptor()).entrySet())
        {
            Object value = node(permission.getValue()).get("default");
            if (value != null)
            {
                assertNotNull(Permission.getByName(String.valueOf(value)), permission.getKey());
            }
        }
    }

    @Test
    void noPermissionRevokesAChild() throws IOException
    {
        // Nukkit grants every child a permission lists, whatever its value; Bukkit revokes false ones.
        for (Map.Entry<String, Object> permission : permissions(nukkitDescriptor()).entrySet())
        {
            Object children = node(permission.getValue()).get("children");
            if (children != null)
            {
                for (Map.Entry<String, Object> child : node(children).entrySet())
                {
                    assertEquals(Boolean.TRUE, child.getValue(), permission.getKey() + " -> " + child.getKey());
                }
            }
        }
    }

    @Test
    void permissionDefaultsMatchPaper() throws IOException
    {
        Map<String, Object> permissions = permissions(nukkitDescriptor());

        assertEquals(true, node(permissions.get("griefprevention.claims")).get("default"));
        assertTrue(node(node(permissions.get("griefprevention.claims")).get("children")).containsKey("griefprevention.trust"));
        assertEquals("op", node(permissions.get("griefprevention.adminclaims")).get("default"));
        assertEquals(false, node(permissions.get("griefprevention.transferclaim.free")).get("default"));
    }

    private static String nukkitDescriptor() throws IOException
    {
        try (InputStream stream = NukkitDescriptorTest.class.getClassLoader().getResourceAsStream("nukkit.yml"))
        {
            assertNotNull(stream, "nukkit.yml");
            return Utils.readFile(stream);
        }
    }

    private static String bukkitDescriptor() throws IOException
    {
        return new String(
                Files.readAllBytes(Paths.get(System.getProperty("griefprevention3d.bukkitDescriptor"))),
                StandardCharsets.UTF_8);
    }

    /** Nukkit's description of the plugin, less the permissions it cannot build without a server. */
    private static PluginDescription describe(String descriptor)
    {
        Map<String, Object> yaml = parse(descriptor);
        yaml.remove("permissions");
        return new PluginDescription(yaml);
    }

    private static Map<String, Object> permissions(String descriptor)
    {
        return node(parse(descriptor).get("permissions"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String descriptor)
    {
        return new Yaml().loadAs(descriptor, LinkedHashMap.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> node(Object value)
    {
        assertTrue(value instanceof Map, String.valueOf(value));
        return (Map<String, Object>) value;
    }
}
