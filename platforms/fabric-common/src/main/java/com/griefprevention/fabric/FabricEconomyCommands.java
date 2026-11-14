package com.griefprevention.fabric;

import com.griefprevention.claims.EconomySettings;
import com.griefprevention.fabric.FabricDenialFeedback.TextMode;
import com.griefprevention.messages.MessageKey;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Paper's {@code /buyclaimblocks} and {@code /sellclaimblocks}.
 *
 * <p>Transactions run through an optional Fabric economy provider (e.g. EconomyCraft). Without a
 * provider, or when {@code GriefPrevention.Economy.ClaimBlocksEnabled} is off, the commands report
 * the matching Paper messages instead of changing balances.
 */
final class FabricEconomyCommands
{
    private final FabricSettings settings;
    private final FabricClaimBlockService blocks;
    private final FabricClaimRepository claims;
    private final FabricEconomy economy;
    private final Logger logger;

    FabricEconomyCommands(
            @NotNull FabricSettings settings,
            @NotNull FabricClaimBlockService blocks,
            @NotNull FabricClaimRepository claims,
            @NotNull FabricEconomy economy,
            @NotNull Logger logger)
    {
        this.settings = settings;
        this.blocks = blocks;
        this.claims = claims;
        this.economy = economy;
        this.logger = logger;
    }

    boolean buyClaimBlocks(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        if (!sender.checkPermission(FabricPermissionDefaults.BUY_SELL_CLAIM_BLOCKS))
        {
            return true;
        }
        ServerPlayer player = sender.requirePlayer();
        if (player == null)
        {
            return true;
        }
        if (args.length != 1)
        {
            sender.send(TextMode.ERROR, MessageKey.ECONOMY_BUY_BLOCKS_USAGE);
            return true;
        }

        Integer amount = parsePositiveAmount(sender, args[0]);
        if (amount == null)
        {
            return true;
        }

        EconomySettings economySettings = this.settings.economy();
        if (!economySettings.claimBlocksEnabled())
        {
            sender.send(TextMode.ERROR, MessageKey.ECONOMY_DISABLED);
            return true;
        }

        FabricEconomyProvider provider = this.economy.provider(sender.server());
        if (provider == null)
        {
            sender.send(TextMode.ERROR, MessageKey.ECONOMY_NO_VAULT);
            return true;
        }

        int bonusLimit = economySettings.claimBlocksMaxBonus();
        int currentBonus;
        try
        {
            currentBonus = this.blocks.entitlement(player.getUUID()).bonus();
        }
        catch (IOException exception)
        {
            sender.sendText(TextMode.ERROR, "Could not read claim-block data: " + exception.getMessage());
            this.logger.warn("Failed to read claim-block entitlement for buyclaimblocks by {}", sender.name(), exception);
            return true;
        }
        if (bonusLimit > 0 && (long) currentBonus + amount > bonusLimit)
        {
            sender.send(TextMode.ERROR, MessageKey.MAX_BONUS_REACHED,
                    String.valueOf(amount), String.valueOf(bonusLimit));
            return true;
        }

        long cost = cost(amount, economySettings.claimBlocksPurchaseCost());
        long balance = provider.balance(player);
        if (balance < cost)
        {
            sender.send(TextMode.ERROR, MessageKey.ECONOMY_NOT_ENOUGH_MONEY,
                    provider.formatChat(cost), provider.formatChat(balance));
            return true;
        }
        if (!provider.withdraw(player, cost))
        {
            balance = provider.balance(player);
            sender.send(TextMode.ERROR, MessageKey.ECONOMY_NOT_ENOUGH_MONEY,
                    provider.formatChat(cost), provider.formatChat(balance));
            return true;
        }

        int newBonus;
        try
        {
            newBonus = this.blocks.adjustBonusClaimBlocks(player.getUUID(), amount);
        }
        catch (IOException | ArithmeticException exception)
        {
            provider.deposit(player.getUUID(), cost);
            sender.sendText(TextMode.ERROR, "Could not update claim blocks: " + exception.getMessage());
            this.logger.warn("Failed to grant purchased claim blocks to {}; refunded {}", sender.name(), cost, exception);
            return true;
        }

        int newTotal = totalRemaining(player);
        sender.send(TextMode.SUCCESS, MessageKey.ECONOMY_BUY_BLOCKS_CONFIRMATION,
                String.valueOf(amount), provider.formatChat(cost), String.valueOf(newTotal));
        this.logger.info("{} purchased {} claim blocks for {} (bonus total now {}).",
                sender.name(), amount, provider.formatChat(cost), newBonus);
        return true;
    }

    boolean sellClaimBlocks(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        if (!sender.checkPermission(FabricPermissionDefaults.BUY_SELL_CLAIM_BLOCKS))
        {
            return true;
        }
        ServerPlayer player = sender.requirePlayer();
        if (player == null)
        {
            return true;
        }
        if (args.length != 1)
        {
            sender.send(TextMode.ERROR, MessageKey.ECONOMY_SELL_BLOCKS_USAGE);
            return true;
        }

        Integer amount = parsePositiveAmount(sender, args[0]);
        if (amount == null)
        {
            return true;
        }

        EconomySettings economySettings = this.settings.economy();
        if (!economySettings.claimBlocksEnabled())
        {
            sender.send(TextMode.ERROR, MessageKey.ECONOMY_DISABLED);
            return true;
        }

        FabricEconomyProvider provider = this.economy.provider(sender.server());
        if (provider == null)
        {
            sender.send(TextMode.ERROR, MessageKey.ECONOMY_NO_VAULT);
            return true;
        }

        FabricClaimBlockService.Entitlement entitlement;
        int availableBlocks;
        try
        {
            entitlement = this.blocks.entitlement(player.getUUID());
            availableBlocks = this.blocks.balance(player.getUUID(), this.claims.snapshots()).remaining();
        }
        catch (IOException exception)
        {
            sender.sendText(TextMode.ERROR, "Could not read claim-block data: " + exception.getMessage());
            this.logger.warn("Failed to read claim-block balance for sellclaimblocks by {}", sender.name(), exception);
            return true;
        }
        if (availableBlocks < amount)
        {
            sender.send(TextMode.ERROR, MessageKey.ECONOMY_NOT_ENOUGH_BLOCKS,
                    String.valueOf(amount), String.valueOf(availableBlocks));
            return true;
        }

        long value = cost(amount, economySettings.claimBlocksSellValue());
        int bonusBlocks = entitlement.bonus();
        int accruedBlocks = entitlement.accrued();
        try
        {
            if (bonusBlocks >= amount)
            {
                this.blocks.adjustBonusClaimBlocks(player.getUUID(), -amount);
            }
            else
            {
                int remaining = amount - bonusBlocks;
                if (bonusBlocks > 0)
                {
                    this.blocks.adjustBonusClaimBlocks(player.getUUID(), -bonusBlocks);
                }
                this.blocks.adjustAccruedClaimBlocks(player.getUUID(), -remaining);
            }
        }
        catch (IOException | ArithmeticException exception)
        {
            sender.sendText(TextMode.ERROR, "Could not update claim blocks: " + exception.getMessage());
            this.logger.warn("Failed to remove sold claim blocks from {}", sender.name(), exception);
            return true;
        }

        provider.deposit(player.getUUID(), value);
        int newTotal = totalRemaining(player);
        sender.send(TextMode.SUCCESS, MessageKey.ECONOMY_SELL_BLOCKS_CONFIRMATION,
                String.valueOf(amount), provider.formatChat(value), String.valueOf(newTotal));
        this.logger.info("{} sold {} claim blocks for {} (was bonus={}, accrued={}).",
                sender.name(), amount, provider.formatChat(value), bonusBlocks, accruedBlocks);
        return true;
    }

    private static @org.jetbrains.annotations.Nullable Integer parsePositiveAmount(
            @NotNull FabricCommandSender sender,
            @NotNull String raw)
    {
        try
        {
            int amount = Integer.parseInt(raw);
            if (amount <= 0)
            {
                throw new NumberFormatException();
            }
            return amount;
        }
        catch (NumberFormatException exception)
        {
            sender.send(TextMode.ERROR, MessageKey.ECONOMY_INVALID_AMOUNT);
            return null;
        }
    }

    private int totalRemaining(@NotNull ServerPlayer player)
    {
        try
        {
            return this.blocks.balance(player.getUUID(), this.claims.snapshots()).remaining();
        }
        catch (IOException exception)
        {
            return 0;
        }
    }

    /** Converts a per-block price into whole currency units, rounding half up (no fractional currency). */
    static long cost(int amount, double perBlock)
    {
        if (perBlock <= 0.0D || Double.isNaN(perBlock) || Double.isInfinite(perBlock))
        {
            return 0L;
        }
        BigDecimal total = BigDecimal.valueOf(perBlock).multiply(BigDecimal.valueOf(amount));
        try
        {
            return total.setScale(0, RoundingMode.HALF_UP).longValueExact();
        }
        catch (ArithmeticException exception)
        {
            return Long.MAX_VALUE;
        }
    }

    @Nullable FabricEconomyProvider provider(@NotNull net.minecraft.server.MinecraftServer server)
    {
        return this.economy.provider(server);
    }
}
