package com.griefprevention.neoforge.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Reads the build-generated NeoForge adapter index and picks the adapter whose Maven-style
 * Minecraft version range matches the running game.
 */
final class NeoForgeAdapterSelector
{
    static final String INDEX_RESOURCE = "META-INF/griefprevention3d/neoforge-adapters.properties";

    private NeoForgeAdapterSelector()
    {
    }

    static List<NeoForgeAdapter> readIndex(ClassLoader loader) throws IOException
    {
        InputStream stream = loader.getResourceAsStream(INDEX_RESOURCE);
        if (stream == null)
        {
            throw new IOException("The jar is missing its NeoForge adapter index " + INDEX_RESOURCE + ".");
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

    static List<NeoForgeAdapter> parse(Properties properties) throws IOException
    {
        String tags = properties.getProperty("adapters", "").trim();
        if (tags.isEmpty())
        {
            throw new IOException("The NeoForge adapter index lists no adapters.");
        }

        List<NeoForgeAdapter> adapters = new ArrayList<>();
        for (String rawTag : tags.split(","))
        {
            String tag = rawTag.trim();
            if (tag.isEmpty())
            {
                continue;
            }
            adapters.add(new NeoForgeAdapter(
                    tag,
                    required(properties, tag, "minecraft"),
                    required(properties, tag, "entrypoint"),
                    required(properties, tag, "mixins")
            ));
        }
        return Collections.unmodifiableList(adapters);
    }

    static NeoForgeAdapter select(List<NeoForgeAdapter> adapters, String minecraftVersion)
    {
        for (NeoForgeAdapter adapter : adapters)
        {
            if (matchesRange(minecraftVersion, adapter.minecraft()))
            {
                return adapter;
            }
        }
        return null;
    }

    /**
     * Evaluates a Maven version range such as {@code [1.21.11]}, {@code [1.21.11,1.21.12)},
     * {@code [26.1,26.4)}, or comma-separated disjoint ranges like {@code [1.21.11],[26.1,26.4)}.
     */
    static boolean matchesRange(String version, String rangeSpec)
    {
        if (version == null || version.isEmpty() || rangeSpec == null || rangeSpec.isEmpty())
        {
            return false;
        }
        String spec = rangeSpec.trim();
        int index = 0;
        while (index < spec.length())
        {
            while (index < spec.length() && (spec.charAt(index) == ' ' || spec.charAt(index) == ','))
            {
                index++;
            }
            if (index >= spec.length())
            {
                break;
            }
            char open = spec.charAt(index);
            if (open != '[' && open != '(')
            {
                int nextComma = spec.indexOf(',', index);
                String exact = (nextComma < 0 ? spec.substring(index) : spec.substring(index, nextComma)).trim();
                if (compareVersions(version, exact) == 0)
                {
                    return true;
                }
                index = nextComma < 0 ? spec.length() : nextComma + 1;
                continue;
            }
            int closeBracket = spec.indexOf(']', index + 1);
            int closeParen = spec.indexOf(')', index + 1);
            int close;
            if (closeBracket < 0) close = closeParen;
            else if (closeParen < 0) close = closeBracket;
            else close = Math.min(closeBracket, closeParen);
            if (close < 0)
            {
                return false;
            }
            boolean includeLow = open == '[';
            boolean includeHigh = spec.charAt(close) == ']';
            String inside = spec.substring(index + 1, close).trim();
            int comma = inside.indexOf(',');
            if (comma < 0)
            {
                if (includeLow && includeHigh && compareVersions(version, inside) == 0)
                {
                    return true;
                }
            }
            else
            {
                String low = inside.substring(0, comma).trim();
                String high = inside.substring(comma + 1).trim();
                boolean lowOk = low.isEmpty()
                        || (includeLow ? compareVersions(version, low) >= 0 : compareVersions(version, low) > 0);
                boolean highOk = high.isEmpty()
                        || (includeHigh ? compareVersions(version, high) <= 0 : compareVersions(version, high) < 0);
                if (lowOk && highOk)
                {
                    return true;
                }
            }
            index = close + 1;
        }
        return false;
    }

    static int compareVersions(String left, String right)
    {
        String leftCore = stripSuffix(left);
        String rightCore = stripSuffix(right);
        int[] leftParts = parseNumericParts(leftCore);
        int[] rightParts = parseNumericParts(rightCore);
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++)
        {
            int a = i < leftParts.length ? leftParts[i] : 0;
            int b = i < rightParts.length ? rightParts[i] : 0;
            if (a != b)
            {
                return Integer.compare(a, b);
            }
        }
        return 0;
    }

    private static String stripSuffix(String version)
    {
        int dash = version.indexOf('-');
        int plus = version.indexOf('+');
        int cut = version.length();
        if (dash >= 0) cut = Math.min(cut, dash);
        if (plus >= 0) cut = Math.min(cut, plus);
        return version.substring(0, cut).trim();
    }

    private static int[] parseNumericParts(String core)
    {
        String[] tokens = core.split("\\.");
        int[] values = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++)
        {
            try
            {
                values[i] = Integer.parseInt(tokens[i].trim());
            }
            catch (NumberFormatException ignored)
            {
                values[i] = 0;
            }
        }
        return values;
    }

    static String describe(List<NeoForgeAdapter> adapters)
    {
        StringBuilder builder = new StringBuilder();
        for (NeoForgeAdapter adapter : adapters)
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
            throw new IOException("The NeoForge adapter index is missing " + key + ".");
        }
        return value;
    }
}
