package com.griefprevention.messages;

import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads the {@code Messages} root of {@code messages.yml}.
 *
 * <p>Both shapes the Paper plugin accepts are supported: a key mapped directly to its text, and the
 * legacy {@code <Key>.Text} sub-key. Keys with any other shape, and every unknown key, are ignored
 * rather than failing the load, so an addon's extra sections cannot break message lookup.
 *
 * <p>Templates are translated through {@link LegacyText} as they are read, matching the point at
 * which Paper resolves color codes and {@code \n} escapes. Placeholder arguments substituted later
 * therefore cannot smuggle formatting codes into a message.
 */
public final class MessageCatalogCodec
{
    private static final String ROOT = "Messages";
    private static final String TEXT = "Text";

    private final Yaml yaml;

    public MessageCatalogCodec()
    {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(50);
        options.setNestingDepthLimit(100);
        options.setCodePointLimit(4 * 1024 * 1024);
        this.yaml = new Yaml(new SafeConstructor(options));
    }

    public synchronized @NotNull MessageCatalog decode(@NotNull String input) throws MessageCatalogException
    {
        final Object loaded;
        try
        {
            loaded = this.yaml.load(input);
        }
        catch (YAMLException exception)
        {
            throw new MessageCatalogException("Invalid messages YAML: " + exception.getMessage(), exception);
        }

        if (loaded == null)
        {
            return MessageCatalog.empty();
        }
        if (!(loaded instanceof Map))
        {
            throw new MessageCatalogException("Expected a mapping at the messages root.");
        }

        Object messages = ((Map<?, ?>) loaded).get(ROOT);
        if (messages == null)
        {
            return MessageCatalog.empty();
        }
        if (!(messages instanceof Map))
        {
            throw new MessageCatalogException("Expected a mapping at " + ROOT + ".");
        }

        Map<String, String> templates = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) messages).entrySet())
        {
            if (!(entry.getKey() instanceof String))
            {
                continue;
            }
            String key = (String) entry.getKey();
            String template = template(entry.getValue());
            if (template != null)
            {
                templates.put(key, LegacyText.translate(template));
            }
        }
        return MessageCatalog.of(templates);
    }

    private static String template(Object value)
    {
        if (value instanceof String)
        {
            return (String) value;
        }
        if (value instanceof Map)
        {
            Object text = ((Map<?, ?>) value).get(TEXT);
            return text instanceof String ? (String) text : null;
        }
        return null;
    }
}
