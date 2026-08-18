package com.griefprevention.messages;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An immutable set of message templates keyed exactly as they appear under the {@code Messages} root
 * of {@code messages.yml}.
 *
 * <p>Placeholder substitution matches the Paper plugin: each {@code {i}} is replaced with the
 * {@code i}-th argument, left to right, and placeholders without a matching argument are left alone.
 */
public final class MessageCatalog
{
    private static final MessageCatalog EMPTY = new MessageCatalog(Collections.emptyMap());

    private final Map<String, String> templates;

    private MessageCatalog(@NotNull Map<String, String> templates)
    {
        this.templates = templates;
    }

    /**
     * @return a catalog with no overrides, so every lookup falls back to its Paper default
     */
    public static @NotNull MessageCatalog empty()
    {
        return EMPTY;
    }

    public static @NotNull MessageCatalog of(@NotNull Map<String, String> templates)
    {
        return new MessageCatalog(Collections.unmodifiableMap(new LinkedHashMap<>(templates)));
    }

    /**
     * @return the raw template for the key, or null when the key is absent
     */
    public @Nullable String template(@NotNull String key)
    {
        return this.templates.get(key);
    }

    /**
     * Formats a message, falling back to the key's Paper default when {@code messages.yml} omits it.
     *
     * @return the message with legacy {@code §} codes still in place, for the platform to render
     */
    public @NotNull String format(@NotNull MessageKey key, @NotNull String @NotNull... args)
    {
        String template = this.templates.get(key.key());
        return substitute(
                template == null ? LegacyText.translate(key.defaultValue()) : template,
                args
        );
    }

    private static @NotNull String substitute(@NotNull String template, @NotNull String @NotNull... args)
    {
        String message = template;
        for (int i = 0; i < args.length; i++)
        {
            message = message.replace("{" + i + "}", args[i]);
        }
        return message;
    }
}
