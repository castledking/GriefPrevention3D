package com.griefprevention.fabric;

import com.griefprevention.claims.EconomySettings;
import com.griefprevention.fabric.FabricDenialFeedback.TextMode;
import com.griefprevention.messages.MessageKey;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Paper's {@code /buyclaimblocks} and {@code /sellclaimblocks}.
 *
 * <p>Fabric has no Vault/economy provider, so these commands keep Paper's config and validation
 * checks but report the disabled/no-provider messages instead of performing transactions.
 */
final class FabricEconomyCommands
{
    private final FabricSettings settings;
    private final FabricClaimBlockService blocks;

    FabricEconomyCommands(@NotNull FabricSettings settings, @NotNull FabricClaimBlockService blocks)
    {
        this.settings = settings;
        this.blocks = blocks;
    }

    boolean buyClaimBlocks(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        if (!sender.checkPermission(FabricPermissionDefaults.BUY_SELL_CLAIM_BLOCKS))
        {
            return true;
        }
        if (sender.requirePlayer() == null)
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

        EconomySettings economy = this.settings.economy();
        if (!economy.claimBlocksEnabled())
        {
            sender.send(TextMode.ERROR, MessageKey.ECONOMY_DISABLED);
            return true;
        }

        int bonusLimit = economy.claimBlocksMaxBonus();
        if (bonusLimit > 0)
        {
            try
            {
                int currentBonus = this.blocks.entitlement(sender.requirePlayer().getUUID()).bonus();
                if ((long) currentBonus + amount > bonusLimit)
                {
                    sender.send(TextMode.ERROR, MessageKey.MAX_BONUS_REACHED,
                            String.valueOf(amount), String.valueOf(bonusLimit));
                    return true;
                }
            }
            catch (Exception ignored)
            {
                // If we cannot read the balance, fall through to the no-provider response.
            }
        }

        // No Vault/economy provider on Fabric; keep Paper's user-facing parity as far as possible.
        sender.send(TextMode.ERROR, MessageKey.ECONOMY_NO_VAULT);
        // A future Fabric economy integration would validate balance/cost here:
        // formatMoney(amount * economy.claimBlocksPurchaseCost())
        return true;
    }

    boolean sellClaimBlocks(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        if (!sender.checkPermission(FabricPermissionDefaults.BUY_SELL_CLAIM_BLOCKS))
        {
            return true;
        }
        if (sender.requirePlayer() == null)
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

        EconomySettings economy = this.settings.economy();
        if (!economy.claimBlocksEnabled())
        {
            sender.send(TextMode.ERROR, MessageKey.ECONOMY_DISABLED);
            return true;
        }

        // No Vault/economy provider on Fabric.
        sender.send(TextMode.ERROR, MessageKey.ECONOMY_NO_VAULT);
        // A future integration would check available blocks and pay formatMoney(amount * economy.claimBlocksSellValue()).
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

    @SuppressWarnings("unused")
    private static @NotNull String formatMoney(double value)
    {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.ROOT);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }
}
