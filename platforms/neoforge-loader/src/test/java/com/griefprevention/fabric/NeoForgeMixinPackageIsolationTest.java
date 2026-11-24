package com.griefprevention.fabric;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeoForgeMixinPackageIsolationTest
{
    private static final String BOOTSTRAP =
            "com.griefprevention.neoforge.bootstrap.UniversalNeoForgeBootstrap";
    private static final String GATE =
            "com.griefprevention.neoforge.bootstrap.NeoForgeAdapterMixinGate";
    private static final Pattern MIXIN_PACKAGE = Pattern.compile("\\\"package\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    @Test
    void bootstrapIsOutsideMixinOwnedPackageAndGateIsConfigured() throws Exception
    {
        String modsToml = readResource("META-INF/neoforge.mods.toml");
        String mixinJson = readResource("griefprevention3d-neoforge.mixins.json");
        Matcher packageMatcher = MIXIN_PACKAGE.matcher(mixinJson);

        assertTrue(modsToml.contains("modId=\"griefprevention3d\""), "expected NeoForge modId is absent");
        assertTrue(modsToml.contains("config=\"griefprevention3d-neoforge.mixins.json\""),
                "expected NeoForge mixin config reference is absent");
        assertTrue(mixinJson.contains("\"" + GATE + "\""), "expected NeoForge mixin gate plugin is absent");
        assertTrue(packageMatcher.find(), "mixin config must declare its package");

        String mixinPackage = packageMatcher.group(1);
        assertFalse(
                BOOTSTRAP.startsWith(mixinPackage + "."),
                () -> BOOTSTRAP + " is inside mixin-owned package " + mixinPackage
        );
    }

    private static String readResource(String name) throws IOException
    {
        try (InputStream stream = NeoForgeMixinPackageIsolationTest.class.getClassLoader().getResourceAsStream(name))
        {
            if (stream == null)
            {
                throw new IOException("Missing test resource: " + name);
            }

            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
