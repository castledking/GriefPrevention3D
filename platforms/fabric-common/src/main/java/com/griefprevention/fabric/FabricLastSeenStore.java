package com.griefprevention.fabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * When each player was last online, which Paper reads from Bukkit's {@code getLastPlayed()} and
 * Fabric has no equivalent for. The investigation tool shows it to staff as a claim owner's
 * inactivity.
 *
 * <p>Times are recorded as players join and leave, and written to {@code LastSeen.yml} in the data
 * folder at most once a minute and when the server stops, so a crash loses at most that minute.
 */
final class FabricLastSeenStore
{
    static final String FILE_NAME = "LastSeen.yml";
    private static final long FLUSH_INTERVAL_TICKS = 20L * 60L;
    private static final String HEADER =
            "# When each player was last online, in milliseconds since 1970, for GriefPrevention3D on Fabric.\n";

    private final @NotNull Path file;
    private final @NotNull Logger logger;
    private final Map<UUID, Long> lastSeen = new TreeMap<>();
    private boolean dirty;
    private long ticks;

    FabricLastSeenStore(@NotNull Path dataFolder, @NotNull Logger logger)
    {
        this.file = dataFolder.resolve(FILE_NAME);
        this.logger = logger;
        load();
    }

    void register()
    {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                record(handler.getPlayer().getUUID(), System.currentTimeMillis()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                record(handler.getPlayer().getUUID(), System.currentTimeMillis()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++this.ticks % FLUSH_INTERVAL_TICKS == 0)
            {
                flush();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(this::recordOnlinePlayersAndFlush);
        // Players are disconnected after SERVER_STOPPING; keep the times of those leaves too.
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> flush());
    }

    /**
     * @return when the player was last online, as epoch milliseconds, or null if they have not been
     *         seen since this store was introduced
     */
    synchronized @Nullable Long lastSeen(@NotNull UUID playerId)
    {
        return this.lastSeen.get(playerId);
    }

    synchronized void record(@NotNull UUID playerId, long epochMillis)
    {
        this.lastSeen.put(playerId, epochMillis);
        this.dirty = true;
    }

    synchronized void flush()
    {
        if (!this.dirty)
        {
            return;
        }
        StringBuilder contents = new StringBuilder(HEADER);
        for (Map.Entry<UUID, Long> entry : this.lastSeen.entrySet())
        {
            contents.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }
        try
        {
            writeAtomically(contents.toString());
            this.dirty = false;
        }
        catch (IOException exception)
        {
            this.logger.warn("Could not save player last-seen times to {}; will retry.", this.file, exception);
        }
    }

    private void recordOnlinePlayersAndFlush(@NotNull MinecraftServer server)
    {
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            record(player.getUUID(), now);
        }
        flush();
    }

    private synchronized void load()
    {
        if (!Files.isRegularFile(this.file, LinkOption.NOFOLLOW_LINKS))
        {
            return;
        }
        List<String> lines;
        try
        {
            lines = Files.readAllLines(this.file, StandardCharsets.UTF_8);
        }
        catch (IOException exception)
        {
            this.logger.warn("Could not read player last-seen times from {}.", this.file, exception);
            return;
        }

        int skipped = 0;
        for (String line : lines)
        {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#"))
            {
                continue;
            }
            int separator = trimmed.indexOf(':');
            try
            {
                UUID playerId = UUID.fromString(unquote(trimmed.substring(0, Math.max(separator, 0)).trim()));
                long epochMillis = Long.parseLong(trimmed.substring(separator + 1).trim());
                this.lastSeen.put(playerId, epochMillis);
            }
            catch (IllegalArgumentException | StringIndexOutOfBoundsException exception)
            {
                skipped++;
            }
        }
        if (skipped > 0)
        {
            this.logger.warn("Ignored {} unreadable lines in {}.", skipped, this.file);
        }
    }

    private static @NotNull String unquote(@NotNull String value)
    {
        if (value.length() >= 2
                && (value.charAt(0) == '\'' || value.charAt(0) == '"')
                && value.charAt(value.length() - 1) == value.charAt(0))
        {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private void writeAtomically(@NotNull String contents) throws IOException
    {
        Path parent = this.file.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "." + this.file.getFileName(), ".tmp");
        try
        {
            Files.writeString(temporary, contents, StandardCharsets.UTF_8);
            try
            {
                Files.move(temporary, this.file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException exception)
            {
                Files.move(temporary, this.file, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally
        {
            Files.deleteIfExists(temporary);
        }
    }
}
