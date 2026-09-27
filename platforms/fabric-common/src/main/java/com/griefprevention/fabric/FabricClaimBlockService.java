package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBlockAbandonment;
import com.griefprevention.claims.ClaimBlockAccrual;
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
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Lazily reads Paper-compatible player entitlements and derives balances from the shared claim
 * graph. Playtime accrual, Bukkit's non-default abandon-return ratio and the admin claim-block
 * commands rewrite only the accrued and bonus lines, preserving all unrelated player-data content.
 */
final class FabricClaimBlockService
{
    private static final String PLAYER_DATA_FOLDER = "PlayerData";
    private static final PlayerDataDocumentCodec PLAYER_DATA_CODEC = new PlayerDataDocumentCodec();
    private static final ClaimBlockConfigCodec CONFIG_CODEC = new ClaimBlockConfigCodec();

    private final @NotNull Path dataFolder;
    private final @NotNull Path playerDataFolder;
    private final @NotNull Logger logger;
    private final @NotNull FabricPermissionResolver permissions;
    private final @NotNull Map<UUID, AccrualState> accrualByPlayer = new LinkedHashMap<>();
    private @NotNull ClaimBlockSettings settings = ClaimBlockSettings.upstreamDefaults();
    private @NotNull Map<String, Integer> groupBonusBlocks = Collections.emptyMap();

    FabricClaimBlockService(
            @NotNull Path dataFolder,
            @NotNull Logger logger,
            @NotNull FabricPermissionResolver permissions)
    {
        this.dataFolder = dataFolder;
        this.playerDataFolder = dataFolder.resolve(PLAYER_DATA_FOLDER);
        this.logger = logger;
        this.permissions = permissions;
    }

    synchronized void reload()
    {
        try
        {
            Files.createDirectories(this.playerDataFolder);
            ClaimBlockSettings settings = readSettings();
            Map<String, Integer> loadedGroupBonuses = readGroupBonuses();

            this.settings = settings;
            this.groupBonusBlocks = Collections.unmodifiableMap(loadedGroupBonuses);
            this.logger.info(
                    "Loaded Fabric claim-block accounting with {} initial blocks, {} blocks/hour up to {}, "
                            + "a {}-claim limit, an abandon return ratio of {}, and {} permission-group "
                            + "bonuses via {}.",
                    this.settings.initialBlocks(),
                    this.settings.blocksAccruedPerHour(),
                    this.settings.maximumAccruedClaimBlocks(),
                    this.settings.maximumClaimsPerPlayer(),
                    this.settings.abandonReturnRatio(),
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
        Entitlement entitlement = entitlement(playerId);
        return new PlayerClaimBlockAccount(
                playerId,
                entitlement.accrued(),
                entitlement.bonus(),
                entitlement.groupBonus()
        ).balance(claims);
    }

    /** The player's stored accrued and bonus blocks, plus the permission-group bonus they hold. */
    synchronized @NotNull Entitlement entitlement(@NotNull UUID playerId) throws IOException
    {
        PlayerDataRecord record = readPlayerDataRecord(playerId);
        PlayerDataDocument playerData = materializeAccrual(playerId, record);
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
        return new Entitlement(playerData.accruedClaimBlocks(), playerData.bonusClaimBlocks(), groupBonus);
    }

    /**
     * Adds to the player's bonus blocks, as {@code /adjustbonusclaimblocks} does.
     *
     * @return the new bonus total
     * @throws ArithmeticException if the total would overflow
     */
    synchronized int adjustBonusClaimBlocks(@NotNull UUID playerId, int adjustment) throws IOException
    {
        PlayerDataRecord record = readPlayerDataRecord(playerId);
        PlayerDataDocument effective = materializeAccrual(playerId, record);
        int bonus = Math.addExact(effective.bonusClaimBlocks(), adjustment);
        rewrite(playerId, record, effective.accruedClaimBlocks(), bonus);
        return bonus;
    }

    /**
     * Adds to the player's accrued blocks, as {@code /aclaim blocks accrued} does. Like Bukkit, the
     * result may exceed the accrual limit; only playtime accrual respects it.
     *
     * @return the new accrued total
     * @throws ArithmeticException if the total would overflow
     */
    synchronized int adjustAccruedClaimBlocks(@NotNull UUID playerId, int adjustment) throws IOException
    {
        PlayerDataRecord record = readPlayerDataRecord(playerId);
        PlayerDataDocument effective = materializeAccrual(playerId, record);
        int accrued = Math.addExact(effective.accruedClaimBlocks(), adjustment);
        rewrite(playerId, record, accrued, effective.bonusClaimBlocks());
        return accrued;
    }

    /** Replaces the player's accrued blocks, as {@code /setaccruedclaimblocks} does. */
    synchronized void setAccruedClaimBlocks(@NotNull UUID playerId, int accrued) throws IOException
    {
        PlayerDataRecord record = readPlayerDataRecord(playerId);
        PlayerDataDocument effective = materializeAccrual(playerId, record);
        rewrite(playerId, record, accrued, effective.bonusClaimBlocks());
    }

    /**
     * Adds to the bonus every holder of a permission receives, stored in Bukkit's
     * {@code PlayerData/$<permission>} file, as {@code /adjustbonusclaimblocks [permission]} does.
     *
     * @return the permission's new bonus total
     * @throws IllegalArgumentException if the permission cannot name a group file
     * @throws ArithmeticException if the total would overflow
     */
    synchronized int adjustGroupBonusBlocks(@NotNull String permission, int adjustment) throws IOException
    {
        if (!isValidGroupPermission(permission))
        {
            throw new IllegalArgumentException("Not a usable permission for a group bonus: " + permission);
        }
        Integer current = this.groupBonusBlocks.get(permission);
        int updated = Math.addExact(current == null ? 0 : current, adjustment);
        writeAtomically(this.playerDataFolder.resolve("$" + permission), updated + "\n");

        Map<String, Integer> groups = new LinkedHashMap<>(this.groupBonusBlocks);
        groups.put(permission, updated);
        this.groupBonusBlocks = Collections.unmodifiableMap(groups);
        return updated;
    }

    /**
     * @return whether a permission is safe to use as part of a group file name: letters, digits and
     *         the punctuation permission nodes use, so it can never escape the player data folder
     */
    static boolean isValidGroupPermission(@NotNull String permission)
    {
        if (permission.isEmpty())
        {
            return false;
        }
        for (int i = 0; i < permission.length(); i++)
        {
            char character = permission.charAt(i);
            boolean allowed = (character >= 'a' && character <= 'z')
                    || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9')
                    || character == '.'
                    || character == '_'
                    || character == '-'
                    || character == '+';
            if (!allowed)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Writes new accrued and bonus values, replacing only the lines that change so unknown addon
     * data in the record survives. The accrued line is also rewritten when pending playtime accrual
     * was just materialized, so that accrual is not lost.
     */
    private void rewrite(
            @NotNull UUID playerId,
            @NotNull PlayerDataRecord record,
            int accrued,
            int bonus)
            throws IOException
    {
        String updatedContents;
        if (record.contents == null)
        {
            updatedContents = PLAYER_DATA_CODEC.encode(new PlayerDataDocument(accrued, bonus));
        }
        else
        {
            updatedContents = record.contents;
            if (accrued != record.document.accruedClaimBlocks())
            {
                updatedContents = replaceAccruedClaimBlocks(record.path, updatedContents, accrued);
            }
            if (bonus != record.document.bonusClaimBlocks())
            {
                updatedContents = replaceBonusClaimBlocks(record.path, updatedContents, bonus);
            }
        }
        apply(new PlayerDataUpdate(
                playerId,
                record.path,
                record.contents,
                updatedContents,
                0,
                accrued
        ));
    }

    synchronized int maximumClaimsPerPlayer()
    {
        return this.settings.maximumClaimsPerPlayer();
    }

    synchronized boolean hasPermission(@NotNull UUID playerId, @NotNull String permission)
    {
        return this.permissions.hasPermission(playerId, permission);
    }

    synchronized boolean permissionOrDefault(
            @NotNull UUID playerId,
            @NotNull String permission,
            boolean defaultValue)
    {
        Boolean value = this.permissions.permissionValue(playerId, permission);
        return value == null ? defaultValue : value;
    }

    synchronized @NotNull ClaimBlockSettings settings()
    {
        return this.settings;
    }

    /** Adds a delivered playtime award and atomically persists it before returning. */
    synchronized void accrueBlocks(@NotNull UUID playerId, int blocks) throws IOException
    {
        if (blocks == 0)
        {
            return;
        }
        AccrualState state = this.accrualByPlayer.computeIfAbsent(
                playerId,
                ignored -> new AccrualState()
        );
        state.pendingBlocks = ClaimBlockAccrual.addPendingBlocks(state.pendingBlocks, blocks);
        flushAccrual(playerId);
    }

    synchronized @NotNull Set<UUID> playersWithAccrualState()
    {
        return new LinkedHashSet<>(this.accrualByPlayer.keySet());
    }

    /** Materializes and atomically saves pending accrual, then releases the cache. */
    synchronized void flushAccrual(@NotNull UUID playerId) throws IOException
    {
        AccrualState state = this.accrualByPlayer.get(playerId);
        if (state == null)
        {
            return;
        }

        PlayerDataRecord record = readPlayerDataRecord(playerId);
        PlayerDataDocument effective = materializeAccrual(playerId, record);
        if (state.dirty)
        {
            String updatedContents = record.contents == null
                    ? PLAYER_DATA_CODEC.encode(effective)
                    : replaceAccruedClaimBlocks(
                            record.path,
                            record.contents,
                            effective.accruedClaimBlocks()
                    );
            apply(new PlayerDataUpdate(
                    playerId,
                    record.path,
                    record.contents,
                    updatedContents,
                    0,
                    effective.accruedClaimBlocks()
            ));
        }
        this.accrualByPlayer.remove(playerId);
    }

    /**
     * Validates and stages Bukkit's accrued-block adjustment without touching the live record.
     * A {@code null} result means the default full-return ratio requires no update.
     */
    synchronized @Nullable PlayerDataUpdate prepareAbandonment(
            @NotNull UUID playerId,
            int claimArea)
            throws IOException
    {
        double returnRatio = this.settings.abandonReturnRatio();
        if (returnRatio == ClaimBlockSettings.DEFAULT_ABANDON_RETURN_RATIO)
        {
            return null;
        }

        PlayerDataRecord record = readPlayerDataRecord(playerId);
        PlayerDataDocument effective = materializeAccrual(playerId, record);
        int penalty = ClaimBlockAbandonment.accruedPenalty(claimArea, returnRatio);
        int updatedAccrued = ClaimBlockAbandonment.accruedAfterAbandonment(
                effective.accruedClaimBlocks(),
                claimArea,
                returnRatio
        );

        String updatedContents = record.contents == null
                ? PLAYER_DATA_CODEC.encode(new PlayerDataDocument(
                        updatedAccrued,
                        effective.bonusClaimBlocks()
                ))
                : replaceAccruedClaimBlocks(record.path, record.contents, updatedAccrued);
        return new PlayerDataUpdate(
                playerId,
                record.path,
                record.contents,
                updatedContents,
                penalty,
                updatedAccrued
        );
    }

    /** Applies a staged update only if no other writer changed the player record in the meantime. */
    synchronized void apply(@NotNull PlayerDataUpdate update) throws IOException
    {
        String currentContents = readCurrentContents(update.path);
        if (!Objects.equals(update.expectedContents, currentContents))
        {
            throw new IOException(
                    "Player data changed while updating claim blocks; refusing to overwrite: "
                            + update.path
            );
        }
        if (!Objects.equals(update.updatedContents, currentContents))
        {
            writeAtomically(update.path, update.updatedContents);
        }

        AccrualState state = this.accrualByPlayer.get(update.playerId);
        if (state != null)
        {
            state.loaded = true;
            state.accruedBlocks = update.updatedAccruedClaimBlocks;
            state.expectedContents = update.updatedContents;
            state.dirty = false;
        }
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

    private @NotNull PlayerDataRecord readPlayerDataRecord(@NotNull UUID playerId) throws IOException
    {
        Path playerFile = this.playerDataFolder.resolve(playerId.toString());
        String input = readCurrentContents(playerFile);
        if (input == null)
        {
            return new PlayerDataRecord(
                    playerFile,
                    null,
                    new PlayerDataDocument(this.settings.initialBlocks(), 0)
            );
        }
        try
        {
            return new PlayerDataRecord(playerFile, input, PLAYER_DATA_CODEC.decode(input));
        }
        catch (PlayerDataFormatException exception)
        {
            throw new IOException(
                    "Refusing claim mutation because player data is malformed: " + playerFile,
                    exception
            );
        }
    }

    private @NotNull PlayerDataDocument materializeAccrual(
            @NotNull UUID playerId,
            @NotNull PlayerDataRecord record)
            throws IOException
    {
        AccrualState state = this.accrualByPlayer.get(playerId);
        if (state == null)
        {
            return record.document;
        }

        if (!state.loaded)
        {
            state.loaded = true;
            state.accruedBlocks = record.document.accruedClaimBlocks();
            state.expectedContents = record.contents;
        }
        else if (!Objects.equals(state.expectedContents, record.contents))
        {
            throw new IOException(
                    "Player data changed while session claim blocks were pending; refusing to overwrite: "
                            + record.path
            );
        }

        if (state.pendingBlocks > 0)
        {
            int updatedAccrued = ClaimBlockAccrual.materializeAccruedBlocks(
                    state.accruedBlocks,
                    state.pendingBlocks,
                    this.settings.maximumAccruedClaimBlocks()
            );
            state.pendingBlocks = 0;
            if (updatedAccrued != state.accruedBlocks)
            {
                state.accruedBlocks = updatedAccrued;
                state.dirty = true;
            }
        }

        return new PlayerDataDocument(
                state.accruedBlocks,
                record.document.bonusClaimBlocks()
        );
    }

    private static @Nullable String readCurrentContents(@NotNull Path playerFile) throws IOException
    {
        if (!Files.exists(playerFile, LinkOption.NOFOLLOW_LINKS))
        {
            return null;
        }
        if (!Files.isRegularFile(playerFile, LinkOption.NOFOLLOW_LINKS))
        {
            throw new IOException("Player data is not a regular file: " + playerFile);
        }
        return Files.readString(playerFile, StandardCharsets.UTF_8);
    }

    private static @NotNull String replaceAccruedClaimBlocks(
            @NotNull Path playerFile,
            @NotNull String contents,
            int updatedAccrued)
            throws IOException
    {
        try
        {
            return PLAYER_DATA_CODEC.replaceAccruedClaimBlocks(contents, updatedAccrued);
        }
        catch (PlayerDataFormatException exception)
        {
            throw new IOException(
                    "Refusing claim mutation because player data is malformed: " + playerFile,
                    exception
            );
        }
    }

    private static @NotNull String replaceBonusClaimBlocks(
            @NotNull Path playerFile,
            @NotNull String contents,
            int updatedBonus)
            throws IOException
    {
        try
        {
            return PLAYER_DATA_CODEC.replaceBonusClaimBlocks(contents, updatedBonus);
        }
        catch (PlayerDataFormatException exception)
        {
            throw new IOException(
                    "Refusing claim-block change because player data is malformed: " + playerFile,
                    exception
            );
        }
    }

    private static void writeAtomically(@NotNull Path target, @NotNull String contents)
            throws IOException
    {
        Path parent = target.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "." + target.getFileName(), ".tmp");
        try
        {
            Files.writeString(temporary, contents, StandardCharsets.UTF_8);
            try
            {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
            catch (AtomicMoveNotSupportedException exception)
            {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally
        {
            Files.deleteIfExists(temporary);
        }
    }

    static final class PlayerDataUpdate
    {
        private final @NotNull UUID playerId;
        private final @NotNull Path path;
        private final @Nullable String expectedContents;
        private final @NotNull String updatedContents;
        private final int accruedPenalty;
        private final int updatedAccruedClaimBlocks;

        private PlayerDataUpdate(
                @NotNull UUID playerId,
                @NotNull Path path,
                @Nullable String expectedContents,
                @NotNull String updatedContents,
                int accruedPenalty,
                int updatedAccruedClaimBlocks)
        {
            this.playerId = playerId;
            this.path = path;
            this.expectedContents = expectedContents;
            this.updatedContents = updatedContents;
            this.accruedPenalty = accruedPenalty;
            this.updatedAccruedClaimBlocks = updatedAccruedClaimBlocks;
        }

        int accruedPenalty()
        {
            return this.accruedPenalty;
        }

        int updatedAccruedClaimBlocks()
        {
            return this.updatedAccruedClaimBlocks;
        }
    }

    /** A player's claim-block entitlement, split the way {@code /claimslist} reports it. */
    static final class Entitlement
    {
        private final int accrued;
        private final int bonus;
        private final int groupBonus;

        Entitlement(int accrued, int bonus, int groupBonus)
        {
            this.accrued = accrued;
            this.bonus = bonus;
            this.groupBonus = groupBonus;
        }

        int accrued()
        {
            return this.accrued;
        }

        int bonus()
        {
            return this.bonus;
        }

        int groupBonus()
        {
            return this.groupBonus;
        }
    }

    private static final class AccrualState
    {
        private boolean loaded;
        private int accruedBlocks;
        private int pendingBlocks;
        private boolean dirty;
        private @Nullable String expectedContents;
    }

    private static final class PlayerDataRecord
    {
        private final @NotNull Path path;
        private final @Nullable String contents;
        private final @NotNull PlayerDataDocument document;

        private PlayerDataRecord(
                @NotNull Path path,
                @Nullable String contents,
                @NotNull PlayerDataDocument document)
        {
            this.path = path;
            this.contents = contents;
            this.document = document;
        }
    }
}
