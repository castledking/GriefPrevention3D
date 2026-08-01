package com.griefprevention.fabric;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricMixinPackageIsolationTest
{
    private static final String ENTRYPOINT =
            "com.griefprevention.fabric.bootstrap.UniversalFabricBootstrap";
    private static final Pattern MIXIN_PACKAGE = Pattern.compile("\\\"package\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    @Test
    void entrypointIsOutsideMixinOwnedPackage() throws Exception
    {
        String modJson = readResource("fabric.mod.json");
        String mixinJson = readResource("griefprevention3d.mixins.json");
        Matcher packageMatcher = MIXIN_PACKAGE.matcher(mixinJson);

        assertTrue(modJson.contains("\"" + ENTRYPOINT + "\""), "expected Fabric entrypoint is absent");
        assertTrue(packageMatcher.find(), "mixin config must declare its package");

        String mixinPackage = packageMatcher.group(1);
        assertFalse(
                ENTRYPOINT.startsWith(mixinPackage + "."),
                () -> ENTRYPOINT + " is inside mixin-owned package " + mixinPackage
        );
    }

    private static String readResource(String name) throws IOException
    {
        try (InputStream stream = FabricMixinPackageIsolationTest.class.getClassLoader().getResourceAsStream(name))
        {
            if (stream == null)
            {
                throw new IOException("Missing test resource: " + name);
            }

            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
