package com.griefprevention.fabric;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** FabricPermissionDefaults is a copy of plugin.yml's permission tree; this keeps the copy honest. */
class FabricPermissionTreeParityTest
{
    @Test
    void everyNodeFabricDeclaresHasPluginYmlsDefaultAndParents() throws IOException
    {
        Map<String, Object> permissions = pluginYmlPermissions();
        List<String> differences = new ArrayList<>();
        for (String node : FabricPermissionDefaults.declared())
        {
            Map<?, ?> declaration = (Map<?, ?>) permissions.get(node);
            if (declaration == null)
            {
                differences.add(node + " is not in plugin.yml");
                continue;
            }
            // Bukkit defaults a node without a default to op.
            Object pluginDefault = declaration.get("default");
            String expectedDefault = pluginDefault == null ? "op" : String.valueOf(pluginDefault);
            if (!expectedDefault.equals(FabricPermissionDefaults.declaredDefault(node)))
            {
                differences.add(node + " defaults to " + expectedDefault + " in plugin.yml but "
                        + FabricPermissionDefaults.declaredDefault(node) + " on Fabric");
            }
            Set<String> expectedParents = parentsOf(node, permissions);
            Set<String> fabricParents = new TreeSet<>(FabricPermissionDefaults.declaredParents(node));
            if (!expectedParents.equals(fabricParents))
            {
                differences.add(node + " has parents " + expectedParents + " in plugin.yml but "
                        + fabricParents + " on Fabric");
            }
        }
        assertEquals(new ArrayList<String>(), differences);
    }

    private static Set<String> parentsOf(String node, Map<String, Object> permissions)
    {
        Set<String> parents = new TreeSet<>();
        for (Map.Entry<String, Object> entry : permissions.entrySet())
        {
            Object children = ((Map<?, ?>) entry.getValue()).get("children");
            if (children instanceof Map && ((Map<?, ?>) children).containsKey(node))
            {
                parents.add(entry.getKey());
            }
        }
        return parents;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> pluginYmlPermissions() throws IOException
    {
        try (Reader reader = Files.newBufferedReader(
                Paths.get(System.getProperty("griefprevention3d.bukkitDescriptor")), StandardCharsets.UTF_8))
        {
            Map<String, Object> descriptor = new Yaml().load(reader);
            return (Map<String, Object>) descriptor.get("permissions");
        }
    }
}
