package com.griefprevention.fabric.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Reads the build-generated adapter index and picks the adapter for the running Minecraft version.
 *
 * <p>The index is a properties file listing adapters newest first:
 * <pre>
 * adapters=mc26_1,mc1_21_11
 * adapter.mc26_1.minecraft=&gt;=26.1 &lt;26.4-
 * adapter.mc26_1.entrypoint=com.griefprevention.fabric.mc26_1.GriefPreventionFabric
 * adapter.mc26_1.mixins=com.griefprevention.fabric.mc26_1.mixin
 * </pre>
 * Each module jar carries an index naming only itself; the universal jar carries one naming every
 * adapter it bundles, under the relocated package each was copied to.
 */
final class FabricAdapterSelector
{
    static final String INDEX_RESOURCE = "META-INF/griefprevention3d/fabric-adapters.properties";

    /** Decides whether a Minecraft version satisfies an adapter's version predicate. */
    interface VersionMatcher
    {
        boolean matches(String minecraftVersion, String predicate);
    }

    private FabricAdapterSelector()
    {
    }

    static List<FabricAdapter> readIndex(ClassLoader loader) throws IOException
    {
        InputStream stream = loader.getResourceAsStream(INDEX_RESOURCE);
        if (stream == null)
        {
            throw new IOException("The jar is missing its Fabric adapter index " + INDEX_RESOURCE + ".");
        }

        Properties properties = new Properties();
        try
        {
            properties.load(stream);
        }
        finally
        {
            stream.close();
        }
        return parse(properties);
    }

    static List<FabricAdapter> parse(Properties properties) throws IOException
    {
        String tags = properties.getProperty("adapters", "").trim();
        if (tags.isEmpty())
        {
            throw new IOException("The Fabric adapter index lists no adapters.");
        }

        List<FabricAdapter> adapters = new ArrayList<FabricAdapter>();
        for (String rawTag : tags.split(","))
        {
            String tag = rawTag.trim();
            if (tag.isEmpty())
            {
                continue;
            }
            adapters.add(new FabricAdapter(
                    tag,
                    required(properties, tag, "minecraft"),
                    required(properties, tag, "entrypoint"),
                    required(properties, tag, "mixins")
            ));
        }
        return Collections.unmodifiableList(adapters);
    }

    /**
     * @return the first adapter whose range includes the version, or null when none does
     */
    static FabricAdapter select(
            List<FabricAdapter> adapters,
            String minecraftVersion,
            VersionMatcher matcher)
    {
        for (FabricAdapter adapter : adapters)
        {
            if (matcher.matches(minecraftVersion, adapter.minecraft()))
            {
                return adapter;
            }
        }
        return null;
    }

    static String describe(List<FabricAdapter> adapters)
    {
        StringBuilder builder = new StringBuilder();
        for (FabricAdapter adapter : adapters)
        {
            if (builder.length() > 0)
            {
                builder.append(", ");
            }
            builder.append(adapter.minecraft());
        }
        return builder.toString();
    }

    private static String required(Properties properties, String tag, String field) throws IOException
    {
        String key = "adapter." + tag + "." + field;
        String value = properties.getProperty(key, "").trim();
        if (value.isEmpty())
        {
            throw new IOException("The Fabric adapter index is missing " + key + ".");
        }
        return value;
    }
}
