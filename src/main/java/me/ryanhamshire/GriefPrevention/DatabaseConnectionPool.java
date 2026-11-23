package me.ryanhamshire.GriefPrevention;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe, zero-dependency JDBC connection pool designed for Folia's multi-threaded region
 * execution model as well as Paper and Spigot.
 *
 * <p>Each caller borrows its own {@link Connection}, preventing cross-thread statement collisions
 * on a single JDBC connection. Idle connections are validated and recycled transparently when
 * expired or dropped by MySQL {@code wait_timeout}.</p>
 */
final class DatabaseConnectionPool implements AutoCloseable
{
    private static final long VALIDATION_INTERVAL_MS = 1_000L;

    @FunctionalInterface
    interface SqlFunction<T>
    {
        T apply(@NotNull Connection connection) throws SQLException;
    }

    @FunctionalInterface
    interface SqlConsumer
    {
        void accept(@NotNull Connection connection) throws SQLException;
    }

    private static final class PooledConnection
    {
        final Connection connection;
        final long createdAtMs;
        volatile long lastUsedAtMs;
        final boolean reused;

        PooledConnection(@NotNull Connection connection, long createdAtMs, long lastUsedAtMs, boolean reused)
        {
            this.connection = connection;
            this.createdAtMs = createdAtMs;
            this.lastUsedAtMs = lastUsedAtMs;
            this.reused = reused;
        }

        PooledConnection asReused()
        {
            return this.reused ? this : new PooledConnection(this.connection, this.createdAtMs, this.lastUsedAtMs, true);
        }
    }

    private final @NotNull DatabaseConfig config;
    private final @Nullable File driversDirectory;
    private final int maxPoolSize;
    private final @NotNull Semaphore permits;
    private final @NotNull ConcurrentLinkedDeque<PooledConnection> idle = new ConcurrentLinkedDeque<>();
    private final @NotNull AtomicBoolean closed = new AtomicBoolean(false);

    private volatile @Nullable Driver externalDriver;
    private volatile @Nullable URLClassLoader externalDriverLoader;

    DatabaseConnectionPool(@NotNull DatabaseConfig config)
    {
        this(config, new File(DataStore.dataLayerFolderPath, "drivers"));
    }

    DatabaseConnectionPool(@NotNull DatabaseConfig config, @Nullable File driversDirectory)
    {
        this.config = config;
        this.driversDirectory = driversDirectory;
        this.maxPoolSize = config.effectivePoolSize();
        this.permits = new Semaphore(this.maxPoolSize, true);
    }

    @NotNull DatabaseConfig config()
    {
        return this.config;
    }

    int maxPoolSize()
    {
        return this.maxPoolSize;
    }

    int idleCount()
    {
        return this.idle.size();
    }

    boolean isClosed()
    {
        return this.closed.get();
    }

    /**
     * Verifies that the configured database can be reached and queried.
     */
    void verifyConnectivity() throws SQLException
    {
        withConnection(connection -> {
            if (!connection.isValid(this.config.validationTimeoutSeconds()))
            {
                throw new SQLException("Initial database connection failed validation.");
            }
            return null;
        });
    }

    /**
     * Borrows a connection, executes {@code action}, and returns the connection to the pool.
     * Automatically retries once with a fresh connection if a reused idle connection dropped.
     */
    <T> T withConnection(@NotNull SqlFunction<T> action) throws SQLException
    {
        for (int attempt = 0; attempt < 2; attempt++)
        {
            PooledConnection pooled = this.borrow(attempt > 0);
            boolean broken = false;
            try
            {
                return action.apply(pooled.connection);
            }
            catch (SQLException e)
            {
                broken = isConnectionFailure(e) || isClosedQuietly(pooled.connection);
                if (attempt == 0 && pooled.reused && broken && !this.closed.get())
                {
                    continue;
                }
                throw e;
            }
            catch (RuntimeException e)
            {
                broken = isClosedQuietly(pooled.connection);
                throw e;
            }
            finally
            {
                this.release(pooled, broken);
            }
        }
        throw new SQLException("Unable to acquire a working database connection.");
    }

    /**
     * Executes {@code action} inside an atomic JDBC transaction ({@code autoCommit = false}),
     * committing on success and rolling back on any failure.
     */
    void withTransaction(@NotNull SqlConsumer action) throws SQLException
    {
        for (int attempt = 0; attempt < 2; attempt++)
        {
            PooledConnection pooled = this.borrow(attempt > 0);
            boolean broken = false;
            boolean previousAutoCommit = true;
            boolean autoCommitChanged = false;
            try
            {
                Connection connection = pooled.connection;
                previousAutoCommit = connection.getAutoCommit();
                if (previousAutoCommit)
                {
                    connection.setAutoCommit(false);
                    autoCommitChanged = true;
                }

                action.accept(connection);
                connection.commit();
                return;
            }
            catch (SQLException e)
            {
                broken = isConnectionFailure(e) || isClosedQuietly(pooled.connection);
                rollbackQuietly(pooled.connection);
                if (attempt == 0 && pooled.reused && broken && !this.closed.get())
                {
                    continue;
                }
                throw e;
            }
            catch (RuntimeException e)
            {
                rollbackQuietly(pooled.connection);
                broken = isClosedQuietly(pooled.connection);
                throw e;
            }
            finally
            {
                if (!broken && autoCommitChanged)
                {
                    try
                    {
                        pooled.connection.setAutoCommit(previousAutoCommit);
                    }
                    catch (SQLException e)
                    {
                        broken = true;
                    }
                }
                this.release(pooled, broken);
            }
        }
    }

    private @NotNull PooledConnection borrow(boolean forceFresh) throws SQLException
    {
        if (this.closed.get())
        {
            throw new SQLException("Database connection pool is closed.");
        }

        boolean acquired;
        try
        {
            acquired = this.permits.tryAcquire(this.config.connectionTimeoutMs(), TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for a database connection.", e);
        }

        if (!acquired)
        {
            throw new SQLException(
                    "Timed out after " + this.config.connectionTimeoutMs()
                            + "ms waiting for a database connection (poolSize=" + this.maxPoolSize + ")."
            );
        }

        if (this.closed.get())
        {
            this.permits.release();
            throw new SQLException("Database connection pool is closed.");
        }

        try
        {
            long now = System.currentTimeMillis();
            if (!forceFresh)
            {
                PooledConnection candidate;
                while ((candidate = this.idle.pollFirst()) != null)
                {
                    if (isHealthyForReuse(candidate, now))
                    {
                        return candidate.asReused();
                    }
                    closeQuietly(candidate.connection);
                }
            }

            Connection fresh = this.openConnection();
            return new PooledConnection(fresh, now, now, false);
        }
        catch (SQLException | RuntimeException e)
        {
            this.permits.release();
            throw e;
        }
    }

    private void release(@NotNull PooledConnection pooled, boolean broken)
    {
        try
        {
            long now = System.currentTimeMillis();
            boolean discard = broken
                    || this.closed.get()
                    || (now - pooled.createdAtMs) >= this.config.maxLifetimeMs()
                    || isClosedQuietly(pooled.connection);

            if (!discard)
            {
                try
                {
                    if (!pooled.connection.getAutoCommit())
                    {
                        rollbackQuietly(pooled.connection);
                        pooled.connection.setAutoCommit(true);
                    }
                }
                catch (SQLException e)
                {
                    discard = true;
                }
            }

            if (discard)
            {
                closeQuietly(pooled.connection);
            }
            else
            {
                pooled.lastUsedAtMs = now;
                this.idle.offerFirst(pooled);
                if (this.closed.get())
                {
                    this.drainIdleConnections();
                }
            }
        }
        finally
        {
            this.permits.release();
        }
    }

    private boolean isHealthyForReuse(@NotNull PooledConnection candidate, long now)
    {
        if ((now - candidate.createdAtMs) >= this.config.maxLifetimeMs())
        {
            return false;
        }
        if ((now - candidate.lastUsedAtMs) >= this.config.idleTimeoutMs())
        {
            return false;
        }
        try
        {
            if (candidate.connection.isClosed())
            {
                return false;
            }
            if ((now - candidate.lastUsedAtMs) >= VALIDATION_INTERVAL_MS)
            {
                return candidate.connection.isValid(this.config.validationTimeoutSeconds());
            }
            return true;
        }
        catch (SQLException e)
        {
            return false;
        }
    }

    private @NotNull Connection openConnection() throws SQLException
    {
        Properties props = this.buildConnectionProperties();
        String url = this.config.jdbcUrl();

        // 1. If we already resolved an external driver JAR, use it directly.
        Driver cachedExternal = this.externalDriver;
        if (cachedExternal != null)
        {
            Connection conn = cachedExternal.connect(url, props);
            if (conn != null) return conn;
        }

        // 2. Ensure standard/configured driver classes are loaded on this classloader.
        this.ensureDriverLoaded(url);

        // 3. Try standard DriverManager first (uses server-bundled MySQL/SQLite or classpath drivers).
        try
        {
            return DriverManager.getConnection(url, props);
        }
        catch (SQLException initialError)
        {
            // 4. If no suitable driver was found on the server classpath, look in GriefPreventionData/drivers/*.jar.
            if (isNoSuitableDriver(initialError))
            {
                Driver loaded = this.loadExternalDriver(url);
                if (loaded != null)
                {
                    Connection conn = loaded.connect(url, props);
                    if (conn != null) return conn;
                }
            }
            throw initialError;
        }
    }

    private @NotNull Properties buildConnectionProperties()
    {
        Properties props = new Properties();
        String url = this.config.jdbcUrl();

        if (!this.config.username().isEmpty() || this.config.isMySqlFamily())
        {
            props.setProperty("user", this.config.username());
            props.setProperty("password", this.config.password());
        }

        if (this.config.isMySqlFamily())
        {
            putIfAbsentInUrl(props, url, "useUnicode", "true");
            putIfAbsentInUrl(props, url, "characterEncoding", "UTF-8");
            putIfAbsentInUrl(props, url, "cachePrepStmts", "true");
            putIfAbsentInUrl(props, url, "prepStmtCacheSize", "250");
            putIfAbsentInUrl(props, url, "prepStmtCacheSqlLimit", "2048");
            putIfAbsentInUrl(props, url, "connectTimeout", String.valueOf(this.config.connectionTimeoutMs()));
            putIfAbsentInUrl(props, url, "socketTimeout", "30000");
            putIfAbsentInUrl(props, url, "tcpKeepAlive", "true");
        }

        return props;
    }

    private static void putIfAbsentInUrl(
            @NotNull Properties props,
            @NotNull String url,
            @NotNull String key,
            @NotNull String value)
    {
        int queryIndex = url.indexOf('?');
        if (queryIndex >= 0)
        {
            String query = url.substring(queryIndex + 1).toLowerCase(Locale.ROOT);
            String needle = key.toLowerCase(Locale.ROOT) + "=";
            if (query.startsWith(needle) || query.contains("&" + needle))
            {
                return;
            }
        }
        props.setProperty(key, value);
    }

    private void ensureDriverLoaded(@NotNull String url)
    {
        if (!this.config.driverClass().isEmpty())
        {
            tryLoadClass(this.config.driverClass());
            return;
        }

        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("jdbc:mysql://"))
        {
            if (!tryLoadClass("com.mysql.cj.jdbc.Driver"))
            {
                if (!tryLoadClass("com.mysql.jdbc.Driver"))
                {
                    tryLoadClass("org.mariadb.jdbc.Driver");
                }
            }
        }
        else if (lower.startsWith("jdbc:mariadb://"))
        {
            if (!tryLoadClass("org.mariadb.jdbc.Driver"))
            {
                tryLoadClass("com.mysql.cj.jdbc.Driver");
            }
        }
        else if (lower.startsWith("jdbc:sqlite:"))
        {
            tryLoadClass("org.sqlite.JDBC");
        }
    }

    private synchronized @Nullable Driver loadExternalDriver(@NotNull String url)
    {
        if (this.externalDriver != null)
        {
            return this.externalDriver;
        }
        if (this.driversDirectory == null || !this.driversDirectory.isDirectory())
        {
            return null;
        }
        File[] files = this.driversDirectory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (files == null || files.length == 0)
        {
            return null;
        }

        List<URL> urls = new ArrayList<>(files.length);
        for (File jar : files)
        {
            try
            {
                urls.add(jar.toURI().toURL());
            }
            catch (Exception ignored) {}
        }
        if (urls.isEmpty()) return null;

        URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]), this.getClass().getClassLoader());
        try
        {
            if (!this.config.driverClass().isEmpty())
            {
                try
                {
                    Class<?> driverType = Class.forName(this.config.driverClass(), true, loader);
                    if (Driver.class.isAssignableFrom(driverType))
                    {
                        Driver driver = (Driver) driverType.getDeclaredConstructor().newInstance();
                        if (driver.acceptsURL(url))
                        {
                            this.externalDriverLoader = loader;
                            this.externalDriver = driver;
                            return driver;
                        }
                    }
                }
                catch (Exception ignored) {}
            }

            ServiceLoader<Driver> drivers = ServiceLoader.load(Driver.class, loader);
            for (Driver driver : drivers)
            {
                try
                {
                    if (driver.acceptsURL(url))
                    {
                        this.externalDriverLoader = loader;
                        this.externalDriver = driver;
                        return driver;
                    }
                }
                catch (SQLException ignored) {}
            }
        }
        catch (Throwable ignored) {}

        try
        {
            loader.close();
        }
        catch (IOException ignored) {}
        return null;
    }

    private static boolean tryLoadClass(@NotNull String className)
    {
        try
        {
            Class.forName(className);
            return true;
        }
        catch (ClassNotFoundException | LinkageError ignored)
        {
            return false;
        }
    }

    private static boolean isNoSuitableDriver(@NotNull SQLException exception)
    {
        String sqlState = exception.getSQLState();
        if ("08001".equals(sqlState)) return true;
        String message = exception.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("no suitable driver");
    }

    static boolean isConnectionFailure(@NotNull SQLException exception)
    {
        SQLException current = exception;
        while (current != null)
        {
            if (current instanceof SQLTransientConnectionException
                    || current instanceof SQLNonTransientConnectionException
                    || current instanceof SQLRecoverableException)
            {
                return true;
            }
            String sqlState = current.getSQLState();
            if (sqlState != null && sqlState.startsWith("08"))
            {
                return true;
            }
            String className = current.getClass().getName();
            if (className.contains("CommunicationsException") || className.contains("ConnectionClosed"))
            {
                return true;
            }
            current = current.getNextException();
        }
        return false;
    }

    private void drainIdleConnections()
    {
        PooledConnection candidate;
        while ((candidate = this.idle.pollFirst()) != null)
        {
            closeQuietly(candidate.connection);
        }
    }

    private static boolean isClosedQuietly(@NotNull Connection connection)
    {
        try
        {
            return connection.isClosed();
        }
        catch (SQLException e)
        {
            return true;
        }
    }

    private static void rollbackQuietly(@NotNull Connection connection)
    {
        try
        {
            if (!connection.isClosed() && !connection.getAutoCommit())
            {
                connection.rollback();
            }
        }
        catch (SQLException ignored) {}
    }

    private static void closeQuietly(@Nullable Connection connection)
    {
        if (connection == null) return;
        try
        {
            if (!connection.isClosed())
            {
                connection.close();
            }
        }
        catch (SQLException ignored) {}
    }

    @Override
    public void close()
    {
        this.closed.set(true);
        this.drainIdleConnections();
        URLClassLoader loader = this.externalDriverLoader;
        this.externalDriverLoader = null;
        this.externalDriver = null;
        if (loader != null)
        {
            try
            {
                loader.close();
            }
            catch (IOException ignored) {}
        }
    }
}
