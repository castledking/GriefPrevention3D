package com.griefprevention.fabric;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Finds players for commands the way Paper's {@code resolvePlayerByName} does.
 *
 * <p>Only local data is read: the players online, and vanilla's {@code usercache.json}, which keeps
 * the same layout on every release this adapter serves. Vanilla's own profile cache changed shape in
 * 1.21.9, so it is not used.
 */
final class FabricPlayerLookup
{
    /** The profile cache vanilla keeps in the server directory. */
    static final String USER_CACHE_FILE = "usercache.json";

    private static volatile @Nullable UserCache userCache;

    private FabricPlayerLookup()
    {
    }

    /** A player the server knows: who they are, and the name they last joined with. */
    record KnownPlayer(@NotNull UUID id, @NotNull String name)
    {
    }

    /** @return the online player as a known player */
    static @NotNull KnownPlayer of(@NotNull ServerPlayer player)
    {
        return new KnownPlayer(player.getUUID(), nameOf(player));
    }

    /** @return the name the player joined with */
    static @NotNull String nameOf(@NotNull ServerPlayer player)
    {
        return player.getName().getString();
    }

    /**
     * Looks a player up among those online, then those the server has seen, then as a UUID.
     *
     * <p>Vanilla's own name lookup asks Mojang about names it has not seen, even in offline mode,
     * where it would hand back the online account's UUID instead of the one the player joins with;
     * Paper, likewise, only knows players who have logged in.
     *
     * @return the player, or null when no player by that name is known
     */
    static @Nullable KnownPlayer find(@NotNull MinecraftServer server, @NotNull String nameOrId)
    {
        ServerPlayer online = server.getPlayerList().getPlayerByName(nameOrId);
        if (online != null)
        {
            return of(online);
        }

        KnownPlayer seen = findInUserCache(userCachePath(server), nameOrId);
        if (seen != null)
        {
            return seen;
        }

        UUID playerId;
        try
        {
            playerId = UUID.fromString(nameOrId);
        }
        catch (IllegalArgumentException notAUuid)
        {
            return null;
        }
        return new KnownPlayer(playerId, name(server, playerId));
    }

    /**
     * Reads a name from vanilla's {@code usercache.json}, which lists the players who have joined,
     * most recently seen first.
     *
     * @return the cached player, or null if the name is not there or the file cannot be read
     */
    static @Nullable KnownPlayer findInUserCache(@NotNull Path userCache, @NotNull String name)
    {
        for (KnownPlayer entry : read(userCache).entries())
        {
            if (entry.name().equalsIgnoreCase(name))
            {
                return entry;
            }
        }
        return null;
    }

    /** @return the name a player last joined with, if {@code usercache.json} lists them */
    static @Nullable String findNameInUserCache(@NotNull Path userCache, @NotNull UUID playerId)
    {
        for (KnownPlayer entry : read(userCache).entries())
        {
            if (entry.id().equals(playerId))
            {
                return entry.name();
            }
        }
        return null;
    }

    /** @return the player's name if the server has seen them, otherwise their UUID */
    static @NotNull String name(@NotNull MinecraftServer server, @NotNull UUID playerId)
    {
        String known = knownName(server, playerId);
        return known != null ? known : playerId.toString();
    }

    /** @return the player's name if they are online or the server has seen them, otherwise null */
    static @Nullable String knownName(@NotNull MinecraftServer server, @NotNull UUID playerId)
    {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null)
        {
            return nameOf(online);
        }
        // Only the local cache is consulted by UUID, so this never waits on the network.
        return findNameInUserCache(userCachePath(server), playerId);
    }

    private static @NotNull Path userCachePath(@NotNull MinecraftServer server)
    {
        return server.getServerDirectory().resolve(USER_CACHE_FILE);
    }

    /** The parsed cache, reread only when the file changes: denial messages ask for owner names often. */
    private static @NotNull UserCache read(@NotNull Path path)
    {
        FileTime modified;
        long size;
        try
        {
            modified = Files.isRegularFile(path) ? Files.getLastModifiedTime(path) : null;
            size = modified == null ? -1 : Files.size(path);
        }
        catch (IOException unreadable)
        {
            modified = null;
            size = -1;
        }
        UserCache cached = userCache;
        if (cached != null && cached.path().equals(path) && Objects.equals(cached.modified(), modified)
                && cached.size() == size)
        {
            return cached;
        }
        UserCache loaded = new UserCache(path, modified, size,
                modified == null ? Collections.emptyList() : parse(path));
        userCache = loaded;
        return loaded;
    }

    private static @NotNull List<KnownPlayer> parse(@NotNull Path path)
    {
        List<KnownPlayer> entries = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
        {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonArray())
            {
                return entries;
            }
            for (JsonElement element : root.getAsJsonArray())
            {
                if (!element.isJsonObject())
                {
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();
                String entryName = string(entry, "name");
                String entryId = string(entry, "uuid");
                if (entryName == null || entryId == null)
                {
                    continue;
                }
                try
                {
                    entries.add(new KnownPlayer(UUID.fromString(entryId), entryName));
                }
                catch (IllegalArgumentException malformedId)
                {
                    // Keep going; an older entry for the same name may still be usable.
                }
            }
        }
        catch (IOException | RuntimeException unreadable)
        {
            return Collections.emptyList();
        }
        return entries;
    }

    private static @Nullable String string(@NotNull JsonObject entry, @NotNull String field)
    {
        JsonElement value = entry.get(field);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private record UserCache(
            @NotNull Path path,
            @Nullable FileTime modified,
            long size,
            @NotNull List<KnownPlayer> entries)
    {
    }
}
