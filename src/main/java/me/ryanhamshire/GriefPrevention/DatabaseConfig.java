package me.ryanhamshire.GriefPrevention;

import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

/**
 * Database connection and pool configuration for {@link DatabaseDataStore}.
 *
 * <p>Loaded from {@code plugins/GriefPreventionData/database.properties} when present, with
 * backwards-compatible fallback and automatic migration from legacy {@code config.yml} keys
 * ({@code GriefPrevention.Database.*} or {@code database.*}).</p>
 */
final class DatabaseConfig
{
    static final String PROPERTIES_FILE_NAME = "database.properties";

    static final int DEFAULT_POOL_SIZE = 8;
    static final int MIN_POOL_SIZE = 1;
    static final int MAX_POOL_SIZE = 32;

    static final long DEFAULT_CONNECTION_TIMEOUT_MS = 10_000L;
    static final long DEFAULT_IDLE_TIMEOUT_MS = 300_000L;
    static final long DEFAULT_MAX_LIFETIME_MS = 1_800_000L;
    static final int DEFAULT_VALIDATION_TIMEOUT_SECONDS = 3;

    private final @NotNull String jdbcUrl;
    private final @NotNull String username;
    private final @NotNull String password;
    private final @NotNull String driverClass;
    private final int maximumPoolSize;
    private final long connectionTimeoutMs;
    private final long idleTimeoutMs;
    private final long maxLifetimeMs;
    private final int validationTimeoutSeconds;

    DatabaseConfig(
            @Nullable String jdbcUrl,
            @Nullable String username,
            @Nullable String password,
            @Nullable String driverClass,
            int maximumPoolSize,
            long connectionTimeoutMs,
            long idleTimeoutMs,
            long maxLifetimeMs,
            int validationTimeoutSeconds)
    {
        this.jdbcUrl = jdbcUrl == null ? "" : jdbcUrl.trim();
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        this.driverClass = driverClass == null ? "" : driverClass.trim();
        this.maximumPoolSize = clampInt(maximumPoolSize, MIN_POOL_SIZE, MAX_POOL_SIZE);
        this.connectionTimeoutMs = clampLong(connectionTimeoutMs, 1_000L, 120_000L);
        this.idleTimeoutMs = clampLong(idleTimeoutMs, 10_000L, 3_600_000L);
        this.maxLifetimeMs = clampLong(maxLifetimeMs, 30_000L, 86_400_000L);
        this.validationTimeoutSeconds = clampInt(validationTimeoutSeconds, 1, 30);
    }

    static @NotNull DatabaseConfig disabled()
    {
        return of("", "", "");
    }

    static @NotNull DatabaseConfig of(@Nullable String jdbcUrl, @Nullable String username, @Nullable String password)
    {
        return new DatabaseConfig(
                jdbcUrl,
                username,
                password,
                "",
                DEFAULT_POOL_SIZE,
                DEFAULT_CONNECTION_TIMEOUT_MS,
                DEFAULT_IDLE_TIMEOUT_MS,
                DEFAULT_MAX_LIFETIME_MS,
                DEFAULT_VALIDATION_TIMEOUT_SECONDS
        );
    }

    static @NotNull DatabaseConfig load(
            @NotNull File dataLayerFolder,
            @Nullable FileConfiguration dataFolderConfig,
            @Nullable FileConfiguration pluginFolderConfig)
    {
        File propsFile = new File(dataLayerFolder, PROPERTIES_FILE_NAME);
        if (propsFile.isFile())
        {
            return loadFromPropertiesFile(propsFile);
        }

        // 1. Check GriefPreventionData/config.yml legacy keys (GriefPrevention.Database.*)
        String url = getConfigString(dataFolderConfig, "GriefPrevention.Database.URL");
        String user = getConfigString(dataFolderConfig, "GriefPrevention.Database.UserName");
        String pass = getConfigString(dataFolderConfig, "GriefPrevention.Database.Password");
        String driver = getConfigString(dataFolderConfig, "GriefPrevention.Database.DriverClass");
        int poolSize = getConfigInt(dataFolderConfig, "GriefPrevention.Database.PoolSize", DEFAULT_POOL_SIZE);
        long connTimeout = getConfigLong(dataFolderConfig, "GriefPrevention.Database.ConnectionTimeoutMs", DEFAULT_CONNECTION_TIMEOUT_MS);
        long idleTimeout = getConfigLong(dataFolderConfig, "GriefPrevention.Database.IdleTimeoutMs", DEFAULT_IDLE_TIMEOUT_MS);
        long maxLifetime = getConfigLong(dataFolderConfig, "GriefPrevention.Database.MaxLifetimeMs", DEFAULT_MAX_LIFETIME_MS);
        int validationSec = getConfigInt(dataFolderConfig, "GriefPrevention.Database.ValidationTimeoutSeconds", DEFAULT_VALIDATION_TIMEOUT_SECONDS);
        boolean migratedFromLegacyConfig = !url.isEmpty();

        // 2. Fallback: GriefPreventionData/config.yml lowercase database.*
        if (url.isEmpty())
        {
            url = getConfigString(dataFolderConfig, "database.url");
            if (!url.isEmpty())
            {
                user = getConfigString(dataFolderConfig, "database.username");
                pass = getConfigString(dataFolderConfig, "database.password");
                driver = getConfigString(dataFolderConfig, "database.driverClass");
                poolSize = getConfigInt(dataFolderConfig, "database.poolSize", DEFAULT_POOL_SIZE);
                migratedFromLegacyConfig = true;
            }
        }

        // 3. Fallback: plugins/GriefPrevention3D/config.yml database.* (backwards compatibility)
        if (url.isEmpty())
        {
            url = getConfigString(pluginFolderConfig, "database.url");
            if (!url.isEmpty())
            {
                user = getConfigString(pluginFolderConfig, "database.username");
                pass = getConfigString(pluginFolderConfig, "database.password");
                driver = getConfigString(pluginFolderConfig, "database.driverClass");
                poolSize = getConfigInt(pluginFolderConfig, "database.poolSize", DEFAULT_POOL_SIZE);
            }
        }

        DatabaseConfig config = new DatabaseConfig(
                url,
                user,
                pass,
                driver,
                poolSize,
                connTimeout,
                idleTimeout,
                maxLifetime,
                validationSec
        );

        // Migrate credentials out of GriefPreventionData/config.yml into database.properties so
        // passwords are not lost when loadConfig() rewrites config.yml and are not shared accidentally.
        if (migratedFromLegacyConfig && config.isEnabled())
        {
            writePropertiesFile(propsFile, config);
        }

        return config;
    }

    private static @NotNull DatabaseConfig loadFromPropertiesFile(@NotNull File propsFile)
    {
        Properties props = new Properties();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(propsFile), StandardCharsets.UTF_8)))
        {
            props.load(reader);
        }
        catch (IOException e)
        {
            GriefPrevention.AddLogEntry("ERROR: Unable to read " + propsFile.getPath() + ": " + e.getMessage());
            return disabled();
        }

        String url = firstNonEmpty(props.getProperty("jdbcUrl"), props.getProperty("url"));
        String user = firstNonEmpty(props.getProperty("username"), props.getProperty("user"));
        String pass = props.getProperty("password", "");
        String driver = props.getProperty("driverClass", "");
        int poolSize = parseInt(firstNonEmpty(props.getProperty("poolSize"), props.getProperty("maximumPoolSize")), DEFAULT_POOL_SIZE);
        long connTimeout = parseLong(props.getProperty("connectionTimeoutMs"), DEFAULT_CONNECTION_TIMEOUT_MS);
        long idleTimeout = parseLong(props.getProperty("idleTimeoutMs"), DEFAULT_IDLE_TIMEOUT_MS);
        long maxLifetime = parseLong(props.getProperty("maxLifetimeMs"), DEFAULT_MAX_LIFETIME_MS);
        int validationSec = parseInt(props.getProperty("validationTimeoutSeconds"), DEFAULT_VALIDATION_TIMEOUT_SECONDS);

        return new DatabaseConfig(url, user, pass, driver, poolSize, connTimeout, idleTimeout, maxLifetime, validationSec);
    }

    static void writePropertiesFile(@NotNull File propsFile, @NotNull DatabaseConfig config)
    {
        File parent = propsFile.getParentFile();
        if (parent != null && !parent.exists())
        {
            parent.mkdirs();
        }
        Properties props = new Properties();
        props.setProperty("jdbcUrl", config.jdbcUrl);
        props.setProperty("username", config.username);
        props.setProperty("password", config.password);
        if (!config.driverClass.isEmpty())
        {
            props.setProperty("driverClass", config.driverClass);
        }
        props.setProperty("poolSize", String.valueOf(config.maximumPoolSize));
        props.setProperty("connectionTimeoutMs", String.valueOf(config.connectionTimeoutMs));
        props.setProperty("idleTimeoutMs", String.valueOf(config.idleTimeoutMs));
        props.setProperty("maxLifetimeMs", String.valueOf(config.maxLifetimeMs));
        props.setProperty("validationTimeoutSeconds", String.valueOf(config.validationTimeoutSeconds));

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(propsFile), StandardCharsets.UTF_8)))
        {
            props.store(writer, "GriefPrevention3D database settings (MySQL/MariaDB/SQLite). Keep this file private.");
        }
        catch (IOException e)
        {
            GriefPrevention.AddLogEntry("Unable to write " + propsFile.getPath() + ": " + e.getMessage());
        }
    }

    boolean isEnabled()
    {
        return !this.jdbcUrl.isEmpty();
    }

    @NotNull String jdbcUrl()
    {
        return this.jdbcUrl;
    }

    @NotNull String username()
    {
        return this.username;
    }

    @NotNull String password()
    {
        return this.password;
    }

    @NotNull String driverClass()
    {
        return this.driverClass;
    }

    int maximumPoolSize()
    {
        return this.maximumPoolSize;
    }

    int effectivePoolSize()
    {
        return this.isSqlite() ? 1 : this.maximumPoolSize;
    }

    long connectionTimeoutMs()
    {
        return this.connectionTimeoutMs;
    }

    long idleTimeoutMs()
    {
        return this.idleTimeoutMs;
    }

    long maxLifetimeMs()
    {
        return this.maxLifetimeMs;
    }

    int validationTimeoutSeconds()
    {
        return this.validationTimeoutSeconds;
    }

    boolean isMySql()
    {
        return this.jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:mysql://");
    }

    boolean isMariaDb()
    {
        return this.jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:mariadb://");
    }

    boolean isMySqlFamily()
    {
        return this.isMySql() || this.isMariaDb();
    }

    boolean isSqlite()
    {
        return this.jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:sqlite:");
    }

    private static @NotNull String getConfigString(@Nullable FileConfiguration config, @NotNull String path)
    {
        if (config == null) return "";
        String value = config.getString(path, "");
        return value == null ? "" : value.trim();
    }

    private static int getConfigInt(@Nullable FileConfiguration config, @NotNull String path, int fallback)
    {
        if (config == null || !config.contains(path)) return fallback;
        return config.getInt(path, fallback);
    }

    private static long getConfigLong(@Nullable FileConfiguration config, @NotNull String path, long fallback)
    {
        if (config == null || !config.contains(path)) return fallback;
        return config.getLong(path, fallback);
    }

    private static @NotNull String firstNonEmpty(@Nullable String first, @Nullable String second)
    {
        if (first != null && !first.trim().isEmpty()) return first.trim();
        if (second != null && !second.trim().isEmpty()) return second.trim();
        return "";
    }

    private static int parseInt(@Nullable String raw, int fallback)
    {
        if (raw == null || raw.trim().isEmpty()) return fallback;
        try
        {
            return Integer.parseInt(raw.trim());
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
    }

    private static long parseLong(@Nullable String raw, long fallback)
    {
        if (raw == null || raw.trim().isEmpty()) return fallback;
        try
        {
            return Long.parseLong(raw.trim());
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
    }

    private static int clampInt(int value, int min, int max)
    {
        return Math.max(min, Math.min(max, value));
    }

    private static long clampLong(long value, long min, long max)
    {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public String toString()
    {
        return "DatabaseConfig{jdbcUrl='" + this.jdbcUrl + "', username='" + this.username
                + "', password='***', poolSize=" + this.effectivePoolSize() + "}";
    }
}
