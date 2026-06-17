package me.ryanhamshire.GriefPrevention;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MessageLocalization {

    private static final String DEFAULT_LOCALE = "en";

    private MessageLocalization() {
        throw new AssertionError("Instantiation of an utility class.");
    }

    private static @NotNull String normalizeLocale(@Nullable String locale) {
        if (locale == null || locale.trim().isEmpty()) return DEFAULT_LOCALE;
        return locale.trim().replace('-', '_');
    }

    private static void ensureLanguageFiles() {
        Path languageFolder = Paths.get(DataStore.languageFolderPath);
        if (Files.notExists(languageFolder)) {
            try {
                Files.createDirectories(languageFolder);
            } catch (IOException e) {
                GriefPrevention.instance
                    .getLogger()
                    .severe("Unable to create language folder at \"" + languageFolder + "\": " + e.getMessage());
                return;
            }
        }
    }

    public static @NotNull String applyConfiguredLocaleToMessages(
        @Nullable String locale,
        @NotNull Messages[] messages
    ) {
        ensureLanguageFiles();
        String normalized = normalizeLocale(locale);

        // Minimal implementation: if external properties exist, we could load them.
        // For now, do not override enum defaults; just return normalized locale.
        return normalized;
    }
}
