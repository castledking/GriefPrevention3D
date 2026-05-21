package com.griefprevention.compat;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.FileConfigurationOptions;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class YamlConfigurationCompat {

    private YamlConfigurationCompat() {
    }

    public static FileConfigurationOptions setHeader(FileConfigurationOptions options, String... lines) {
        return setHeader(options, Arrays.asList(lines));
    }

    public static FileConfigurationOptions setHeader(FileConfigurationOptions options, List<String> lines) {
        try {
            Method setHeader = FileConfigurationOptions.class.getMethod("setHeader", List.class);
            setHeader.invoke(options, lines);
        } catch (ReflectiveOperationException | LinkageError e) {
            options.header(String.join("\n", lines));
        }
        return options;
    }

    /**
     * Gets comments for a path in a FileConfiguration, returning empty list if not supported.
     */
    public static List<String> getComments(FileConfiguration config, String path) {
        try {
            Method getComments = FileConfiguration.class.getMethod("getComments", String.class);
            @SuppressWarnings("unchecked")
            List<String> comments = (List<String>) getComments.invoke(config, path);
            return comments != null ? comments : Collections.emptyList();
        } catch (ReflectiveOperationException | LinkageError e) {
            return Collections.emptyList();
        }
    }

    /**
     * Sets comments for a path in a FileConfiguration, doing nothing if not supported.
     */
    public static void setComments(FileConfiguration config, String path, List<String> comments) {
        try {
            Method setComments = FileConfiguration.class.getMethod("setComments", String.class, List.class);
            setComments.invoke(config, path, comments);
        } catch (ReflectiveOperationException | LinkageError e) {
            // Comments not supported in this version
        }
    }
}
