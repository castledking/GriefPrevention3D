package me.ryanhamshire.GriefPrevention;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConfigTest
{
    @Test
    void loadsFromDatabasePropertiesWhenPresent(@TempDir Path tempDir) throws Exception
    {
        File propsFile = tempDir.resolve(DatabaseConfig.PROPERTIES_FILE_NAME).toFile();
        Properties props = new Properties();
        props.setProperty("jdbcUrl", "jdbc:mysql://db.example.com:3306/gp3d");
        props.setProperty("username", "gpuser");
        props.setProperty("password", "s3cr3t");
        props.setProperty("poolSize", "12");
        props.setProperty("connectionTimeoutMs", "15000");
        props.setProperty("idleTimeoutMs", "120000");
        props.setProperty("maxLifetimeMs", "900000");
        props.setProperty("validationTimeoutSeconds", "5");
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(propsFile), StandardCharsets.UTF_8))
        {
            props.store(writer, null);
        }

        DatabaseConfig config = DatabaseConfig.load(tempDir.toFile(), new YamlConfiguration(), new YamlConfiguration());

        assertTrue(config.isEnabled());
        assertTrue(config.isMySql());
        assertTrue(config.isMySqlFamily());
        assertFalse(config.isSqlite());
        assertEquals("jdbc:mysql://db.example.com:3306/gp3d", config.jdbcUrl());
        assertEquals("gpuser", config.username());
        assertEquals("s3cr3t", config.password());
        assertEquals(12, config.effectivePoolSize());
        assertEquals(15000L, config.connectionTimeoutMs());
        assertEquals(120000L, config.idleTimeoutMs());
        assertEquals(900000L, config.maxLifetimeMs());
        assertEquals(5, config.validationTimeoutSeconds());
        assertFalse(config.toString().contains("s3cr3t"));
    }

    @Test
    void migratesLegacyDataFolderConfigIntoDatabaseProperties(@TempDir Path tempDir)
    {
        YamlConfiguration dataConfig = new YamlConfiguration();
        dataConfig.set("GriefPrevention.Database.URL", "jdbc:mariadb://localhost:3306/claims");
        dataConfig.set("GriefPrevention.Database.UserName", "admin");
        dataConfig.set("GriefPrevention.Database.Password", "hunter2");
        dataConfig.set("GriefPrevention.Database.PoolSize", 6);

        DatabaseConfig config = DatabaseConfig.load(tempDir.toFile(), dataConfig, new YamlConfiguration());

        assertTrue(config.isEnabled());
        assertTrue(config.isMariaDb());
        assertTrue(config.isMySqlFamily());
        assertEquals(6, config.effectivePoolSize());
        assertTrue(tempDir.resolve(DatabaseConfig.PROPERTIES_FILE_NAME).toFile().isFile(),
                "Legacy config.yml credentials should be migrated to database.properties");

        // Second load reads from the migrated database.properties even if config.yml no longer has the keys.
        DatabaseConfig reloaded = DatabaseConfig.load(tempDir.toFile(), new YamlConfiguration(), new YamlConfiguration());
        assertEquals("jdbc:mariadb://localhost:3306/claims", reloaded.jdbcUrl());
        assertEquals("admin", reloaded.username());
        assertEquals("hunter2", reloaded.password());
        assertEquals(6, reloaded.effectivePoolSize());
    }

    @Test
    void fallsBackToPluginConfigDatabaseSection(@TempDir Path tempDir)
    {
        YamlConfiguration pluginConfig = new YamlConfiguration();
        pluginConfig.set("database.url", "jdbc:mysql://127.0.0.1:3306/gp");
        pluginConfig.set("database.username", "root");
        pluginConfig.set("database.password", "pass");

        DatabaseConfig config = DatabaseConfig.load(tempDir.toFile(), new YamlConfiguration(), pluginConfig);

        assertTrue(config.isEnabled());
        assertEquals("jdbc:mysql://127.0.0.1:3306/gp", config.jdbcUrl());
        assertEquals("root", config.username());
        assertEquals("pass", config.password());
    }

    @Test
    void clampsPoolSizeAndForcesSingleConnectionForSqlite()
    {
        DatabaseConfig sqlite = new DatabaseConfig("jdbc:sqlite:test.db", "", "", "", 16, 100, 100, 100, 0);
        assertTrue(sqlite.isSqlite());
        assertEquals(1, sqlite.effectivePoolSize());
        assertEquals(1_000L, sqlite.connectionTimeoutMs());
        assertEquals(10_000L, sqlite.idleTimeoutMs());
        assertEquals(30_000L, sqlite.maxLifetimeMs());
        assertEquals(1, sqlite.validationTimeoutSeconds());

        DatabaseConfig hugePool = new DatabaseConfig("jdbc:mysql://localhost/db", "u", "p", "", 999, 10_000, 300_000, 1_800_000, 3);
        assertEquals(DatabaseConfig.MAX_POOL_SIZE, hugePool.effectivePoolSize());
    }
}
