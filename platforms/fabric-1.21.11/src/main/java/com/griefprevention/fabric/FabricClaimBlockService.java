package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBlockBalance;
import com.griefprevention.claims.ClaimBlockConfigCodec;
import com.griefprevention.claims.ClaimBlockConfigException;
import com.griefprevention.claims.ClaimBlockSettings;
import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.PlayerClaimBlockAccount;
import com.griefprevention.persistence.PlayerDataDocument;
import com.griefprevention.persistence.PlayerDataDocumentCodec;
import com.griefprevention.persistence.PlayerDataFormatException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lazily reads Paper-compatible player entitlements and derives balances from the shared claim
 * graph. Claim mutations never rewrite player files; only Paper's existing accrual/admin paths do.
 */
final class FabricClaimBlockService
{
    private static final String PLAYER_DATA_FOLDER = "PlayerData";
    private static final PlayerDataDocumentCodec PLAYER_DATA_CODEC = new PlayerDataDocumentCodec();
    private static final ClaimBlockConfigCodec CONFIG_CODEC = new ClaimBlockConfigCodec();

    private final @NotNull Path dataFolder;
    private final @NotNull Path playerDataFolder;
    private final @NotNull Logger logger;
    private final @NotNull FabricGroupBonusPermissionResolver permissions;
    private int initialBlocks = ClaimBlockSettings.DEFAULT_INITIAL_BLOCKS;
    private @NotNull Map<String, Integer> groupBonusBlocks = Collections.emptyMap();

    FabricClaimBlockService(
            @NotNull Path dataFolder,
            @NotNull Logger logger,
            @NotNull FabricGroupBonusPermissionResolver permissions)
    {
        this.dataFolder = dataFolder;
        this.playerDataFolder = dataFolder.resolve(PLAYER_DATA_FOLDER);
        this.logger = logger;
        this.permissions = permissions;
    }

    static @NotNull FabricClaimBlockService create(@NotNull Path dataFolder, @NotNull Logger logger)
    {
        return new FabricClaimBlockService(
                dataFolder,
                logger,
                FabricGroupBonusPermissions.detect(logger)
        );
    }

    synchronized void reload()
    {
        try
        {
            Files.createDirectories(this.playerDataFolder);
            ClaimBlockSettings settings = readSettings();
            Map<String, Integer> loadedGroupBonuses = readGroupBonuses();

            this.initialBlocks = settings.initialBlocks();
            this.groupBonusBlocks = Collections.unmodifiableMap(loadedGroupBonuses);
            this.logger.info(
                    "Loaded Fabric claim-block accounting with {} initial blocks and {} permission-group bonuses via {}.",
                    this.initialBlocks,
                    this.groupBonusBlocks.size(),
                    this.permissions.description()
            );
        }
        catch (IOException | ClaimBlockConfigException exception)
        {
            throw new IllegalStateException(
                    "Could not safely activate Fabric claim-block accounting from " + this.dataFolder + ".",
                    exception
            );
        }
    }

    synchronized @NotNull ClaimBlockBalance balance(
            @NotNull UUID playerId,
            @NotNull Collection<ClaimSnapshot> claims)
            throws IOException
    {
        PlayerDataDocument playerData = readPlayerData(playerId);
        int groupBonus = 0;
        for (Map.Entry<String, Integer> entry : this.groupBonusBlocks.entrySet())
        {
            if (this.permissions.hasPermission(playerId, entry.getKey()))
            {
                // Bukkit's group-bonus accumulator uses ordinary int arithmetic. Preserve that
                // behavior here; PlayerClaimBlockAccount handles overflow in the final total.
                groupBonus += entry.getValue();
            }
        }

        return new PlayerClaimBlockAccount(
                playerId,
                playerData.accruedClaimBlocks(),
                playerData.bonusClaimBlocks(),
                groupBonus
        ).balance(claims);
    }

    private @NotNull ClaimBlockSettings readSettings()
            throws IOException, ClaimBlockConfigException
    {
        Path config = this.dataFolder.resolve("config.yml");
        if (!Files.exists(config, LinkOption.NOFOLLOW_LINKS))
        {
            return ClaimBlockSettings.upstreamDefaults();
        }
        if (!Files.isRegularFile(config, LinkOption.NOFOLLOW_LINKS))
        {
            throw new IOException("Claim-block config is not a regular file: " + config);
        }
        return CONFIG_CODEC.decode(Files.readString(config, StandardCharsets.UTF_8));
    }

    private @NotNull Map<String, Integer> readGroupBonuses() throws IOException
    {
        Map<String, Integer> result = new LinkedHashMap<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(this.playerDataFolder, "$*"))
        {
            for (Path file : stream)
            {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                {
                    continue;
                }

                String fileName = file.getFileName().toString();
                String permission = fileName.substring(1);
                if (permission.isEmpty())
                {
                    continue;
                }

                try
                {
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    if (lines.isEmpty())
                    {
                        throw new NumberFormatException("empty file");
                    }
                    result.put(permission, Integer.parseInt(lines.get(0)));
                }
                catch (IOException | NumberFormatException exception)
                {
                    // Match Bukkit's compatibility behavior: one broken group record is logged and
                    // ignored rather than preventing every player from loading.
                    this.logger.warn("Ignoring malformed Fabric claim-block group file {}.", file, exception);
                }
            }
        }
        return result;
    }

    private @NotNull PlayerDataDocument readPlayerData(@NotNull UUID playerId) throws IOException
    {
        Path playerFile = this.playerDataFolder.resolve(playerId.toString());
        if (!Files.exists(playerFile, LinkOption.NOFOLLOW_LINKS))
        {
            return new PlayerDataDocument(this.initialBlocks, 0);
        }
        if (!Files.isRegularFile(playerFile, LinkOption.NOFOLLOW_LINKS))
        {
            throw new IOException("Player data is not a regular file: " + playerFile);
        }

        String input = Files.readString(playerFile, StandardCharsets.UTF_8);
        try
        {
            return PLAYER_DATA_CODEC.decode(input);
        }
        catch (PlayerDataFormatException exception)
        {
            throw new IOException(
                    "Refusing claim mutation because player data is malformed: " + playerFile,
                    exception
            );
        }
    }
}
