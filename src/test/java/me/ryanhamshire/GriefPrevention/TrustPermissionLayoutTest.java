package me.ryanhamshire.GriefPrevention;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustPermissionLayoutTest
{
    @Test
    void managerAndPermissionNodeTrustHaveDistinctPermissionsAndParents()
    {
        InputStream resource = getClass().getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(resource);
        YamlConfiguration plugin = YamlConfiguration.loadConfiguration(
                new InputStreamReader(resource, StandardCharsets.UTF_8)
        );

        assertTrue(plugin.getBoolean(
                "permissions.griefprevention.claims.children.griefprevention.managetrust"));
        assertFalse(plugin.contains(
                "permissions.griefprevention.claims.children.griefprevention.permissiontrust"));
        assertTrue(plugin.getBoolean(
                "permissions.griefprevention.adminclaims.children.griefprevention.permissiontrust"));
        assertTrue(plugin.getBoolean("permissions.griefprevention.managetrust.default"));
        assertFalse(plugin.getBoolean("permissions.griefprevention.permissiontrust.default"));
        assertEquals(
                Collections.singletonList("mt"),
                plugin.getStringList("commands.managetrust.aliases")
        );
        assertEquals(
                "griefprevention.permissiontrust",
                plugin.getString("commands.permissiontrust.permission")
        );
        // /pt follows the command it was an alias of in name only: it is now permission-node
        // trust, not manage trust.
        assertEquals(
                Collections.singletonList("pt"),
                plugin.getStringList("commands.permissiontrust.aliases")
        );
        assertEquals(
                "/<command> <permission> <access|container|build|manage>",
                plugin.getString("commands.permissiontrust.usage")
        );
    }

    @Test
    void permissionTrustIsMappedAsAnAdminClaimSubcommandInEveryAliasDefault() throws Exception
    {
        YamlConfiguration builtIn = new YamlConfiguration();
        builtIn.loadFromString(Alias.getDefaultYaml());

        InputStream resource = getClass().getClassLoader().getResourceAsStream("alias.yml");
        assertNotNull(resource);
        YamlConfiguration packaged = YamlConfiguration.loadConfiguration(
                new InputStreamReader(resource, StandardCharsets.UTF_8)
        );

        assertPermissionTrustAliasLayout(builtIn);
        assertPermissionTrustAliasLayout(packaged);
        assertEquals("permissiontrust", Alias.AClaimTrust.getStandalone());
    }

    private static void assertPermissionTrustAliasLayout(YamlConfiguration aliases)
    {
        String path = "subcommands.aclaim.trust";
        assertEquals(Collections.singletonList("trust"), aliases.getStringList(path + ".commands"));
        assertEquals(
                Collections.singletonList("permissiontrust"),
                aliases.getStringList(path + ".standalone")
        );
        assertEquals("griefprevention.permissiontrust", aliases.getString(path + ".permission"));
        assertEquals(
                Collections.singletonList("permission"),
                aliases.getStringList(path + ".arguments.target.options.permission")
        );
        assertEquals(
                Arrays.asList("container", "inventory"),
                aliases.getStringList(path + ".arguments.type.options.container")
        );

        assertTrue(aliases.contains("subcommands.claim.trust.arguments.type.options.manage"));
        assertFalse(aliases.contains("subcommands.claim.trust.arguments.type.options.permission"));
    }
}
