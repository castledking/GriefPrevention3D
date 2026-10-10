package com.griefprevention.fabric;

import com.griefprevention.fabric.FabricDenialFeedback.TextMode;
import com.griefprevention.messages.MessageKey;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The staff commands that change claim-block balances. The legacy commands keep their upstream
 * meaning: {@code /adjustbonusclaimblocks} ({@code /acb}) adds bonus blocks to a player or, given a
 * {@code [permission]}, to everyone holding it; {@code /setaccruedclaimblocks} ({@code /scb})
 * replaces a player's accrued blocks. {@code /aclaim blocks} adds to either kind.
 */
final class FabricClaimBlockCommands
{
    private final FabricClaimBlockService blocks;
    private final Logger logger;

    FabricClaimBlockCommands(@NotNull FabricClaimBlockService blocks, @NotNull Logger logger)
    {
        this.blocks = blocks;
        this.logger = logger;
    }

    /** {@code /adjustbonusclaimblocks <player|[permission]> <amount>}. */
    boolean adjustBonus(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        if (!sender.checkPermission(FabricPermissionDefaults.ADJUST_CLAIM_BLOCKS))
        {
            return true;
        }
        if (args.length != 2)
        {
            return false;
        }
        Integer adjustment = parseAmount(args[1]);
        if (adjustment == null)
        {
            return false;
        }

        if (args[0].startsWith("[") && args[0].endsWith("]") && args[0].length() > 2)
        {
            adjustGroup(sender, args[0].substring(1, args[0].length() - 1), adjustment);
            return true;
        }

        FabricPlayerLookup.KnownPlayer target = FabricPlayerLookup.find(sender.server(), args[0]);
        if (target == null)
        {
            sender.sendError(MessageKey.PLAYER_NOT_FOUND_2);
            return true;
        }
        Integer total = change(sender, () -> this.blocks.adjustBonusClaimBlocks(target.id(), adjustment));
        if (total != null)
        {
            sender.send(TextMode.SUCCESS, MessageKey.ADJUST_BLOCKS_SUCCESS,
                    target.name(), String.valueOf(adjustment), String.valueOf(total));
            this.logger.info("{} adjusted {}'s bonus claim blocks by {}.", sender.name(), target.name(), adjustment);
        }
        return true;
    }

    /** {@code /adjustbonusclaimblocksall <amount>}: every online player. */
    boolean adjustBonusForAll(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        if (!sender.checkPermission(FabricPermissionDefaults.ADJUST_CLAIM_BLOCKS))
        {
            return true;
        }
        if (args.length != 1)
        {
            return false;
        }
        Integer adjustment = parseAmount(args[0]);
        if (adjustment == null)
        {
            return false;
        }

        List<String> adjusted = forEachOnlinePlayer(sender, playerId -> this.blocks.adjustBonusClaimBlocks(playerId, adjustment));
        sender.send(TextMode.SUCCESS, MessageKey.ADJUST_BLOCKS_ALL_SUCCESS, String.valueOf(adjustment));
        this.logger.info("{} adjusted the bonus claim blocks of {} online players by {}: {}",
                sender.name(), adjusted.size(), adjustment, adjusted);
        return true;
    }

    /** {@code /setaccruedclaimblocks <player> <amount>}. */
    boolean setAccrued(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        if (!sender.checkPermission(FabricPermissionDefaults.ADJUST_CLAIM_BLOCKS))
        {
            return true;
        }
        if (args.length != 2)
        {
            return false;
        }
        Integer amount = parseAmount(args[1]);
        if (amount == null)
        {
            return false;
        }

        FabricPlayerLookup.KnownPlayer target = FabricPlayerLookup.find(sender.server(), args[0]);
        if (target == null)
        {
            sender.sendError(MessageKey.PLAYER_NOT_FOUND_2);
            return true;
        }
        Integer result = change(sender, () -> {
            this.blocks.setAccruedClaimBlocks(target.id(), amount);
            return amount;
        });
        if (result != null)
        {
            sender.send(TextMode.SUCCESS, MessageKey.SET_CLAIM_BLOCKS_SUCCESS);
            this.logger.info("{} set {}'s accrued claim blocks to {}.", sender.name(), target.name(), amount);
        }
        return true;
    }

    /** {@code /setaccruedclaimblocksall <amount>}: every online player. */
    boolean setAccruedForAll(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        if (!sender.checkPermission(FabricPermissionDefaults.ADJUST_CLAIM_BLOCKS))
        {
            return true;
        }
        if (args.length != 1)
        {
            return false;
        }
        Integer amount = parseAmount(args[0]);
        if (amount == null)
        {
            return false;
        }

        List<String> updated = forEachOnlinePlayer(sender, playerId -> {
            this.blocks.setAccruedClaimBlocks(playerId, amount);
            return amount;
        });
        sender.send(TextMode.SUCCESS, MessageKey.SET_CLAIM_BLOCKS_SUCCESS);
        this.logger.info("{} set the accrued claim blocks of {} online players to {}: {}",
                sender.name(), updated.size(), amount, updated);
        return true;
    }

    /** {@code /aclaim blocks <bonus|accrued> <player|all> <amount>}: adds to either balance. */
    boolean adjust(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        if (!sender.checkPermission(FabricPermissionDefaults.ADJUST_CLAIM_BLOCKS))
        {
            return true;
        }
        if (args.length != 3)
        {
            return false;
        }
        String type = args[0].toLowerCase(Locale.ROOT);
        if (!type.equals("bonus") && !type.equals("accrued"))
        {
            sender.sendText(TextMode.ERROR, "Invalid block type: " + args[0] + ". Use 'bonus' or 'accrued'.");
            return true;
        }
        Integer amount = parseAmount(args[2]);
        if (amount == null)
        {
            sender.sendText(TextMode.ERROR, "Invalid amount: " + args[2]);
            return true;
        }
        boolean bonus = type.equals("bonus");

        if (args[1].equalsIgnoreCase("all"))
        {
            List<String> adjusted = forEachOnlinePlayer(sender, playerId -> bonus
                    ? this.blocks.adjustBonusClaimBlocks(playerId, amount)
                    : this.blocks.adjustAccruedClaimBlocks(playerId, amount));
            sender.sendText(TextMode.SUCCESS, "Adjusted " + type + " blocks for all online players by " + amount + ".");
            this.logger.info("{} adjusted the {} claim blocks of {} online players by {}: {}",
                    sender.name(), type, adjusted.size(), amount, adjusted);
            return true;
        }
        if (bonus && args[1].startsWith("[") && args[1].endsWith("]") && args[1].length() > 2)
        {
            adjustGroup(sender, args[1].substring(1, args[1].length() - 1), amount);
            return true;
        }

        FabricPlayerLookup.KnownPlayer target = FabricPlayerLookup.find(sender.server(), args[1]);
        if (target == null)
        {
            sender.sendError(MessageKey.PLAYER_NOT_FOUND_2);
            return true;
        }
        Integer total = change(sender, () -> bonus
                ? this.blocks.adjustBonusClaimBlocks(target.id(), amount)
                : this.blocks.adjustAccruedClaimBlocks(target.id(), amount));
        if (total != null)
        {
            sender.sendText(TextMode.SUCCESS, "Adjusted " + target.name() + "'s " + type + " blocks by " + amount
                    + ".  New total: " + total + ".");
            this.logger.info("{} adjusted {}'s {} claim blocks by {}.", sender.name(), target.name(), type, amount);
        }
        return true;
    }

    private void adjustGroup(@NotNull FabricCommandSender sender, @NotNull String permission, int adjustment)
    {
        if (!FabricClaimBlockService.isValidGroupPermission(permission))
        {
            sender.sendError(MessageKey.INVALID_PERMISSION_ID);
            return;
        }
        Integer total = change(sender, () -> this.blocks.adjustGroupBonusBlocks(permission, adjustment));
        if (total != null)
        {
            sender.send(TextMode.SUCCESS, MessageKey.ADJUST_GROUP_BLOCKS_SUCCESS,
                    permission, String.valueOf(adjustment), String.valueOf(total));
            this.logger.info("{} adjusted {}'s bonus claim blocks by {}.", sender.name(), permission, adjustment);
        }
    }

    /** Applies a change to every online player, naming the players it succeeded for. */
    private @NotNull List<String> forEachOnlinePlayer(
            @NotNull FabricCommandSender sender,
            @NotNull PlayerChange playerChange)
    {
        List<String> changed = new ArrayList<>();
        for (ServerPlayer player : sender.server().getPlayerList().getPlayers())
        {
            UUID playerId = player.getUUID();
            if (change(sender, () -> playerChange.apply(playerId)) != null)
            {
                changed.add(FabricPlayerLookup.nameOf(player));
            }
        }
        return changed;
    }

    /**
     * Runs one balance change, reporting a failure instead of leaving the sender without an answer.
     *
     * @return the change's result, or null if it failed
     */
    private @Nullable Integer change(@NotNull FabricCommandSender sender, @NotNull BalanceChange change)
    {
        try
        {
            return change.run();
        }
        catch (ArithmeticException overflow)
        {
            sender.sendText(TextMode.ERROR, "That change would take a claim-block total past what can be stored.");
        }
        catch (IOException exception)
        {
            this.logger.error("Could not save a claim-block change requested by {}.", sender.name(), exception);
            sender.sendText(TextMode.ERROR, "Could not save the claim-block change: " + exception.getMessage());
        }
        return null;
    }

    private static @Nullable Integer parseAmount(@NotNull String amount)
    {
        try
        {
            return Integer.parseInt(amount);
        }
        catch (NumberFormatException notANumber)
        {
            return null;
        }
    }

    @FunctionalInterface
    private interface BalanceChange
    {
        int run() throws IOException;
    }

    @FunctionalInterface
    private interface PlayerChange
    {
        int apply(@NotNull UUID playerId) throws IOException;
    }
}
