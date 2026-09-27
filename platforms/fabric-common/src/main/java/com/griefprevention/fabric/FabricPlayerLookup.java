package com.griefprevention.fabric;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Finds players for commands the way Paper's {@code resolvePlayerByName} does. */
final class FabricPlayerLookup
{
    /** The profile cache vanilla keeps in the server directory. */
    static final String USER_CACHE_FILE = "usercache.json";

    private FabricPlayerLookup()
    {
    }

    /**
     * Looks a player up among those online, then those the server has seen, then as a UUID.
     *
     * <p>Only local data is read. Vanilla's own name lookup asks Mojang about names it has not seen,
     * even in offline mode, where it would hand back the online account's UUID instead of the one the
     * player joins with; Paper, likewise, only knows players who have logged in.
     *
     * @return the player, or null when no player by that name is known
     */
    static @Nullable NameAndId find(@NotNull MinecraftServer server, @NotNull String nameOrId)
    {
        ServerPlayer online = server.getPlayerList().getPlayerByName(nameOrId);
        if (online != null)
        {
            return online.nameAndId();
        }

        NameAndId seen = findInUserCache(server.getServerDirectory().resolve(USER_CACHE_FILE), nameOrId);
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
        return new NameAndId(playerId, name(server, playerId));
    }

    /**
     * Reads a name from vanilla's {@code usercache.json}, which lists the players who have joined,
     * most recently seen first.
     *
     * @return the cached player, or null if the name is not there or the file cannot be read
     */
    static @Nullable NameAndId findInUserCache(@NotNull Path userCache, @NotNull String name)
    {
        if (!Files.isRegularFile(userCache))
        {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(userCache, StandardCharsets.UTF_8))
        {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonArray())
            {
                return null;
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
                if (entryName == null || entryId == null || !entryName.equalsIgnoreCase(name))
                {
                    continue;
                }
                try
                {
                    return new NameAndId(UUID.fromString(entryId), entryName);
                }
                catch (IllegalArgumentException malformedId)
                {
                    // Keep looking; an older entry for the same name may still be usable.
                }
            }
        }
        catch (IOException | RuntimeException unreadable)
        {
            return null;
        }
        return null;
    }

    private static @Nullable String string(@NotNull JsonObject entry, @NotNull String field)
    {
        JsonElement value = entry.get(field);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    /** @return the player's name if the server has seen them, otherwise their UUID */
    static @NotNull String name(@NotNull MinecraftServer server, @NotNull UUID playerId)
    {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null)
        {
            return online.nameAndId().name();
        }
        // Only the local cache is consulted by UUID, so this never waits on the network.
        return server.services().nameToIdCache().get(playerId).map(NameAndId::name).orElse(playerId.toString());
    }
}
