package com.griefprevention.commands;

import me.ryanhamshire.GriefPrevention.*;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Unified command handler for /claim with subcommands
 */
public class UnifiedClaimCommand extends UnifiedCommandHandler {
    private final ClaimCreateCommandAction claimCreateAction;

    public UnifiedClaimCommand(@NotNull GriefPrevention plugin) {
        super(plugin, "claim");
        this.claimCreateAction = new ClaimCreateCommandAction(plugin);

        // Register subcommands
        registerSubcommand("create", this.claimCreateAction);
        registerSubcommand("trust", this::handleTrust, "accesstrust", "containertrust", "permissiontrust");
        registerSubcommand("untrust", this::handleUntrust);
        registerSubcommand("trustlist", this::handleTrustList);
        registerSubcommand("list", this::handleList);
        registerSubcommand("mode", createModeTabExecutor());
        registerSubcommand("restrictsubclaim", this::handleRestrictSubclaim);
        registerSubcommand("explosions", createExplosionsTabExecutor());
        registerSubcommand("witherexplosions", createWitherExplosionsTabExecutor(), "witherexplosion");
        registerSubcommand("buyblocks", createBuyBlocksTabExecutor());
        registerSubcommand("sellblocks", createSellBlocksTabExecutor());
        registerSubcommand("abandon", this::handleAbandon, "abandonall");
        registerSubcommand("siege", this::handleSiege);
        registerSubcommand("trapped", this::handleTrapped);
        registerSubcommand("expand", this::handleExpand);
        registerSubcommand("help", this::handleHelp);

        // Register standalone commands from Alias enum
        registerStandaloneCommand(Alias.ClaimCreate, this.claimCreateAction);
        registerStandaloneCommand(Alias.ClaimTrust, this::handleTrust);
        registerStandaloneCommand(Alias.ClaimUntrust, this::handleUntrust);
        registerStandaloneCommand(Alias.ClaimTrustlist, this::handleTrustList);
        registerStandaloneCommand(Alias.ClaimList, this::handleList);
        registerStandaloneCommand(Alias.ClaimMode, createModeTabExecutor());
        registerStandaloneCommand(Alias.ClaimRestrictSubclaim, this::handleRestrictSubclaim);
        registerStandaloneCommand(Alias.ClaimExplosions, this::handleExplosions);
        registerStandaloneCommand(Alias.ClaimWitherExplosions, this::handleWitherExplosions);
        registerStandaloneCommand(Alias.ClaimBuyBlocks, createBuyBlocksTabExecutor());
        registerStandaloneCommand(Alias.ClaimSellBlocks, createSellBlocksTabExecutor());
        registerStandaloneCommand(Alias.ClaimAbandon, createNoArgStandaloneTabExecutor(this::handleAbandon));
        registerStandaloneCommand(Alias.ClaimSiege, this::handleSiege);
        registerStandaloneCommand(Alias.ClaimTrapped, this::handleTrapped);
        registerStandaloneCommand(Alias.ClaimExpand, this::handleExpand);
        registerStandaloneCommand(Alias.ClaimHelp, this::handleHelp);
    }

    @Override
    protected void handleDefault(CommandSender sender) {
        // Check if root command is disabled first
        if (!rootCommandEnabled) {
            sender.sendMessage("This command is disabled.");
            return;
        }

        if (handleConfiguredFallback(sender)) {
            return;
        }

        // Check if use-as-help-cmd is enabled
        if (rootCommandConfig != null && rootCommandConfig.shouldUseAsHelpCmd()) {
            sendHelpMessage(sender, new String[0]);
            return;
        }

        // Default behavior: create a claim
        this.claimCreateAction.execute(sender, new String[0]);
    }

    @Override
    protected boolean handleUnknownSubcommand(CommandSender sender, String subcommand, String[] args) {
        if (sender instanceof Player) {
            GriefPrevention.sendMessage((Player) sender, TextMode.Err, Messages.CommandNotFound, subcommand);
        } else {
            sender.sendMessage("Unknown subcommand: " + subcommand);
        }
        return true;
    }

    private boolean handleTrust(CommandSender sender, String[] args) {
        if (!(sender instanceof Player))
            return false;

        if (args.length < 1 || args.length > 2)
            return false;

        String recipientName = args[0];

        if (args.length == 1) {
            // No trust type specified, use default trust command
            return plugin.handleTrustCommand(sender, new String[] { recipientName });
        }

        // Handle specific trust types
        String type = args[1].toLowerCase();
        switch (type) {
            case "build":
                // Build trust is the default, so just use the standard trust command
                return plugin.handleTrustCommand(sender, new String[] { recipientName });
            case "access":
                return plugin.getCommand("accesstrust").execute(sender, "accesstrust", new String[] { recipientName });
            case "container":
                return plugin.getCommand("containertrust").execute(sender, "containertrust",
                        new String[] { recipientName });
            case "permission":
                return plugin.getCommand("permissiontrust").execute(sender, "permissiontrust",
                        new String[] { recipientName });
            default:
                return false;
        }
    }

    private boolean handleUntrust(CommandSender sender, String[] args) {
        // Delegate to existing untrust command logic
        // The new structure uses a single options argument that can be:
        // - A player name (from player: player)
        // - "all" (from all: [all])
        // - "public" (from public: [public])
        return plugin.handleUntrustCommand(sender, args);
    }

    private boolean handleTrustList(CommandSender sender, String[] args) {
        // Delegate to existing trustlist command logic
        return plugin.handleTrustListCommand(sender, args);
    }

    private boolean handleList(CommandSender sender, String[] args) {
        // Delegate to existing claimslist command logic
        return plugin.handleClaimsListCommand(sender, args);
    }

    private boolean handleMode(CommandSender sender, String[] args) {
        // Handle mode switching (basic, subdivide, 3d)
        return plugin.handleModeCommand(sender, args);
    }

    private TabExecutor createModeTabExecutor() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command command,
                    @NotNull String alias, @NotNull String[] args) {
                if (!"mode".equalsIgnoreCase(command.getName())) {
                    if (args.length > 0) {
                        return false;
                    }
                    if ("shapedclaims".equalsIgnoreCase(alias) || "shapedclaim".equalsIgnoreCase(alias)) {
                        return handleMode(sender, new String[] { "shaped" });
                    }
                    return handleMode(sender, new String[0]);
                }

                if (args.length == 0 && ("shapedclaims".equalsIgnoreCase(alias) || "shapedclaim".equalsIgnoreCase(alias))) {
                    if (!plugin.config_claims_allowShapedClaims) {
                        if (sender instanceof org.bukkit.entity.Player) {
                            GriefPrevention.sendMessage((org.bukkit.entity.Player) sender, TextMode.Err, Messages.ShapedClaimsDisabled);
                        }
                        return true;
                    }
                    if (sender instanceof org.bukkit.entity.Player
                            && (!((org.bukkit.entity.Player) sender).hasPermission("griefprevention.shapedclaims")
                            || !((org.bukkit.entity.Player) sender).hasPermission("griefprevention.claims"))) {
                        org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;
                        GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoPermissionForCommand);
                        return true;
                    }
                    return plugin.handleModeCommand(sender, new String[] { "shaped" });
                }
                return handleMode(sender, args);
            }

            @Override
            public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                    @NotNull org.bukkit.command.Command command, @NotNull String alias, @NotNull String[] args) {
                if (!"mode".equalsIgnoreCase(command.getName())) {
                    return java.util.Collections.emptyList();
                }

                // Provide tab completion for mode options
                if (args.length == 1) {
                    String prefix = args[0].toLowerCase();
                    java.util.stream.Stream<String> modes = java.util.Arrays.asList("basic", "2d", "3d").stream();
                    if (plugin.config_claims_allowShapedClaims) {
                        modes = java.util.stream.Stream.concat(modes, java.util.stream.Stream.of("shaped"));
                    }
                    return modes
                            .filter(mode -> mode.toLowerCase().startsWith(prefix))
                            .collect(java.util.stream.Collectors.toList());
                }
                return java.util.Collections.emptyList();
            }
        };
    }

    private boolean handleRestrictSubclaim(CommandSender sender, String[] args) {
        // Delegate to existing restrictsubclaim command logic
        return plugin.handleRestrictSubclaimCommand(sender, args);
    }

    private boolean handleExplosions(CommandSender sender, String[] args) {
        return plugin.handleClaimExplosionsCommand(sender, args);
    }

    private boolean handleWitherExplosions(CommandSender sender, String[] args) {
        return plugin.handleWitherExplosionsCommand(sender, args);
    }

    private TabExecutor createExplosionsTabExecutor() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command command,
                    @NotNull String alias, @NotNull String[] args) {
                return handleExplosions(sender, args);
            }

            @Override
            public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                    @NotNull org.bukkit.command.Command command, @NotNull String alias, @NotNull String[] args) {
                if (args.length == 1) {
                    return java.util.Arrays.asList("on", "off").stream()
                            .filter(option -> option.startsWith(args[0].toLowerCase()))
                            .collect(java.util.stream.Collectors.toList());
                }
                return java.util.Collections.emptyList();
            }
        };
    }

    private TabExecutor createWitherExplosionsTabExecutor() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command command,
                    @NotNull String alias, @NotNull String[] args) {
                return handleWitherExplosions(sender, args);
            }

            @Override
            public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                    @NotNull org.bukkit.command.Command command, @NotNull String alias, @NotNull String[] args) {
                if (args.length == 1) {
                    return java.util.Arrays.asList("on", "off").stream()
                            .filter(option -> option.startsWith(args[0].toLowerCase()))
                            .collect(java.util.stream.Collectors.toList());
                }
                return java.util.Collections.emptyList();
            }
        };
    }

    private org.bukkit.command.TabExecutor createBuyBlocksTabExecutor() {
        return new org.bukkit.command.TabExecutor() {
            @Override
            public boolean onCommand(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command command, @NotNull String label, String[] args) {
                return handleBuyBlocks(sender, args);
            }

            @Override
            public java.util.List<String> onTabComplete(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command command, @NotNull String label, String[] args) {
                if (args.length == 1) {
                    java.util.List<String> result = java.util.Arrays.asList("10", "50", "100", "500", "1000").stream()
                            .filter(s -> s.startsWith(args[0]))
                            .collect(java.util.stream.Collectors.toList());
                    return result;
                }
                return java.util.Collections.emptyList();
            }
        };
    }

    private org.bukkit.command.TabExecutor createSellBlocksTabExecutor() {
        return new org.bukkit.command.TabExecutor() {
            @Override
            public boolean onCommand(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command command, @NotNull String label, String[] args) {
                return handleSellBlocks(sender, args);
            }

            @Override
            public java.util.List<String> onTabComplete(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command command, @NotNull String label, String[] args) {
                if (args.length == 1) {
                    java.util.List<String> result = java.util.Arrays.asList("10", "50", "100", "500", "1000").stream()
                            .filter(s -> s.startsWith(args[0]))
                            .collect(java.util.stream.Collectors.toList());
                    return result;
                }
                return java.util.Collections.emptyList();
            }
        };
    }

    private boolean handleBuyBlocks(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.dataStore.getMessage(Messages.CommandRequiresPlayer));
            return true;
        }
        Player player = (Player) sender;

        // Check if economy is enabled in config
        if (!plugin.config_economy_claimBlocksEnabled) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.EconomyDisabled);
            return true;
        }

        // Check for Vault economy
        net.milkbowl.vault.economy.Economy economy = getEconomy();
        if (economy == null) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.EconomyNoVault);
            return true;
        }

        // Parse amount argument
        if (args.length < 1) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.EconomyBuyBlocksUsage);
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[0]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.EconomyInvalidAmount);
            return true;
        }

        // Calculate cost
        double cost = amount * plugin.config_economy_claimBlocksPurchaseCost;
        double balance = economy.getBalance(player);

        if (balance < cost) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.EconomyNotEnoughMoney,
                    String.format("%.2f", cost), String.format("%.2f", balance));
            return true;
        }

        // Direct purchase. The hopper confirmation GUI has been migrated to
        // GPExpansion; when that plugin is present it registers its own
        // /buyclaimblocks and takes priority, so this fallback path is used
        // only for the /claim buyblocks alias or when GPExpansion is absent.
        net.milkbowl.vault.economy.EconomyResponse withdrawal = economy.withdrawPlayer(player, cost);
        if (!withdrawal.transactionSuccess()) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.EconomyNotEnoughMoney,
                    String.format("%.2f", cost), String.format("%.2f", balance));
            return true;
        }

        PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
        playerData.setBonusClaimBlocks(playerData.getBonusClaimBlocks() + amount);
        plugin.dataStore.savePlayerData(player.getUniqueId(), playerData);

        int newTotal = playerData.getRemainingClaimBlocks();
        GriefPrevention.sendMessage(player, TextMode.Success, Messages.EconomyBuyBlocksConfirmation,
                String.valueOf(amount), String.format("%.2f", cost), String.valueOf(newTotal));

        return true;
    }

    private boolean handleSellBlocks(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.dataStore.getMessage(Messages.CommandRequiresPlayer));
            return true;
        }
        Player player = (Player) sender;

        // Check if economy is enabled in config
        if (!plugin.config_economy_claimBlocksEnabled) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.EconomyDisabled);
            return true;
        }

        // Check for Vault economy
        net.milkbowl.vault.economy.Economy economy = getEconomy();
        if (economy == null) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.EconomyNoVault);
            return true;
        }

        // Parse amount argument
        if (args.length < 1) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.EconomySellBlocksUsage);
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[0]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.EconomyInvalidAmount);
            return true;
        }

        // Check if player has enough blocks to sell
        PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
        int availableBlocks = playerData.getRemainingClaimBlocks();

        if (availableBlocks < amount) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.EconomyNotEnoughBlocks,
                    String.valueOf(amount), String.valueOf(availableBlocks));
            return true;
        }

        // Calculate value
        double value = amount * plugin.config_economy_claimBlocksSellValue;

        // Process the transaction - reduce bonus blocks first, then accrued if needed
        int bonusBlocks = playerData.getBonusClaimBlocks();
        if (bonusBlocks >= amount) {
            playerData.setBonusClaimBlocks(bonusBlocks - amount);
        } else {
            // Use all bonus blocks first, then reduce accrued
            int remaining = amount - bonusBlocks;
            playerData.setBonusClaimBlocks(0);
            playerData.setAccruedClaimBlocks(playerData.getAccruedClaimBlocks() - remaining);
        }
        plugin.dataStore.savePlayerData(player.getUniqueId(), playerData);

        economy.depositPlayer(player, value);

        int newTotal = playerData.getRemainingClaimBlocks();
        GriefPrevention.sendMessage(player, TextMode.Success, Messages.EconomySellBlocksConfirmation,
                String.valueOf(amount), String.format("%.2f", value), String.valueOf(newTotal));

        return true;
    }

    private static net.milkbowl.vault.economy.Economy cachedEconomy = null;
    private static boolean economyChecked = false;

    private net.milkbowl.vault.economy.Economy getEconomy() {
        if (economyChecked) return cachedEconomy;
        economyChecked = true;

        try {
            if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
                return null;
            }
            @SuppressWarnings("null")
            org.bukkit.plugin.RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> rsp =
                    plugin.getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
            if (rsp == null) return null;
            cachedEconomy = rsp.getProvider();
        } catch (NoClassDefFoundError e) {
            // Vault is not installed
            cachedEconomy = null;
        }
        return cachedEconomy;
    }

    private boolean handleAbandon(CommandSender sender, String[] args) {
        if (args.length > 0 && "all".equalsIgnoreCase(args[0])) {
            return plugin.abandonAllClaimsHandler(sender);
        }
        if (args.length > 0 && "toplevel".equalsIgnoreCase(args[0])) {
            if (sender instanceof Player) {
                return plugin.abandonClaimHandler((Player) sender, true);
            }
            return false;
        }
        if (sender instanceof Player) {
            return plugin.abandonClaimHandler((Player) sender, false);
        }
        return false;
    }

    private boolean handleSiege(CommandSender sender, String[] args) {
        // Siege feature is not available in this version
        if (sender instanceof Player) {
            GriefPrevention.sendMessage((Player) sender, TextMode.Info, "The siege feature is not available in this version.");
        } else {
            sender.sendMessage("The siege feature is not available in this version.");
        }
        return true;
    }

    private boolean handleTrapped(CommandSender sender, String[] args) {
        if (sender instanceof Player) {
            return plugin.handleTrappedCommand((Player) sender, args);
        } else {
            return false;
        }
    }

    private boolean handleExpand(CommandSender sender, String[] args) {
        if (sender instanceof Player) {
            return plugin.handleExtendClaimCommand((Player) sender, args);
        } else {
            return false;
        }
    }

    private boolean handleHelp(CommandSender sender, String[] args) {
        sendHelpMessage(sender, args);
        return true;
    }
}
