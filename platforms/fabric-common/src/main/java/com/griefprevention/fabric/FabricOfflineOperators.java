package com.griefprevention.fabric;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Whether a player who may be offline is a server operator, for permissions whose plugin.yml default
 * is {@code op}. Bukkit's {@code OfflinePlayer.isOp()} reads the operator list; Fabric's lookup into it
 * takes a different key type on each release, so the list is read from {@code ops.json}, whose format
 * has stayed the same.
 */
final class FabricOfflineOperators
{
    static final String OPS_FILE = "ops.json";
    /** The level {@link FabricVersionCompat#isGameMaster} asks an online player for. */
    private static final int GAME_MASTER_LEVEL = 2;

    private FabricOfflineOperators()
    {
    }

    static boolean isOperator(@NotNull MinecraftServer server, @NotNull UUID playerId)
    {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null)
        {
            return FabricClaimRepository.isOperator(online);
        }
        return isListedOperator(server.getServerDirectory().resolve(OPS_FILE), playerId);
    }

    /** @return whether {@code ops.json} lists the player at the game-master level or above */
    static boolean isListedOperator(@NotNull Path opsFile, @NotNull UUID playerId)
    {
        if (!Files.isRegularFile(opsFile))
        {
            return false;
        }
        try (Reader reader = Files.newBufferedReader(opsFile, StandardCharsets.UTF_8))
        {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonArray())
            {
                return false;
            }
            for (JsonElement element : root.getAsJsonArray())
            {
                if (!element.isJsonObject())
                {
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();
                JsonElement id = entry.get("uuid");
                if (id == null || !id.isJsonPrimitive() || !playerId.toString().equalsIgnoreCase(id.getAsString()))
                {
                    continue;
                }
                JsonElement level = entry.get("level");
                // Vanilla writes the level; an entry without one gets the server's default op level, 4.
                return level == null || !level.isJsonPrimitive() || level.getAsInt() >= GAME_MASTER_LEVEL;
            }
        }
        catch (IOException | RuntimeException unreadable)
        {
            return false;
        }
        return false;
    }
}
