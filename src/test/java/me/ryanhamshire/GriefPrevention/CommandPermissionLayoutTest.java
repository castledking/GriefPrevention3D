package me.ryanhamshire.GriefPrevention;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every claim and admin command has its own permission node under a parent bundle, so servers can negate one command
 * without removing the rest of griefprevention.claims or griefprevention.adminclaims.
 */
class CommandPermissionLayoutTest
{
    @Test
    void everyCommandPermissionIsDeclared()
    {
        YamlConfiguration plugin = load("plugin.yml");
        ConfigurationSection commands = plugin.getConfigurationSection("commands");
        assertNotNull(commands);

        for (String command : commands.getKeys(false))
        {
            String permission = plugin.getString("commands." + command + ".permission");
            assertNotNull(permission, "/" + command + " has no permission");
            assertTrue(plugin.contains("permissions." + permission + ".description"),
                    "/" + command + " uses undeclared permission " + permission);
        }
    }

    @Test
    void claimCommandsUseTheirOwnNodesUnderGriefPreventionClaims()
    {
        YamlConfiguration plugin = load("plugin.yml");

        for (String node : Arrays.asList("trust", "untrust", "trustlist", "containertrust", "accesstrust",
                "managetrust", "claimpvp", "claimalerts", "toggleclaimalerts", "scrollresize"))
        {
            assertTrue(plugin.getBoolean("permissions.griefprevention.claims.children.griefprevention." + node),
                    "griefprevention." + node + " should be a child of griefprevention.claims");
        }

        assertEquals("griefprevention.trustlist", plugin.getString("commands.trustlist.permission"));
        assertEquals("griefprevention.claimpvp", plugin.getString("commands.claimpvp.permission"));
        assertEquals("griefprevention.toggleclaimalerts", plugin.getString("commands.claimtogglealerts.permission"));
    }

    @Test
    void adminClaimCommandsUseTheirOwnNodesUnderGriefPreventionAdminClaims()
    {
        YamlConfiguration plugin = load("plugin.yml");

        for (String node : Arrays.asList("3dadminclaims", "adminclaimslist", "deletealladminclaims"))
        {
            assertTrue(plugin.getBoolean("permissions.griefprevention.adminclaims.children.griefprevention." + node),
                    "griefprevention." + node + " should be a child of griefprevention.adminclaims");
            assertEquals("op", plugin.getString("permissions.griefprevention." + node + ".default"));
        }

        assertEquals("griefprevention.3dadminclaims", plugin.getString("commands.3dadminclaims.permission"));
        assertEquals("griefprevention.adminclaimslist", plugin.getString("commands.adminclaimslist.permission"));
        assertEquals("griefprevention.deletealladminclaims",
                plugin.getString("commands.deletealladminclaims.permission"));
    }

    @Test
    void claimPvpSubcommandUsesItsOwnNode() throws Exception
    {
        YamlConfiguration builtIn = new YamlConfiguration();
        builtIn.loadFromString(Alias.getDefaultYaml());

        assertEquals("griefprevention.claimpvp", load("alias.yml").getString("subcommands.claim.pvp.permission"));
        String builtInPermission = builtIn.getString("subcommands.claim.pvp.permission");
        assertTrue(builtInPermission == null || builtInPermission.equals("griefprevention.claimpvp"),
                "built-in alias defaults give /claim pvp " + builtInPermission);
    }

    private YamlConfiguration load(String resourceName)
    {
        InputStream resource = getClass().getClassLoader().getResourceAsStream(resourceName);
        assertNotNull(resource, resourceName + " is missing");
        return YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
    }
}
