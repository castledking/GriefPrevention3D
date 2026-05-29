package com.griefprevention.api;

import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Collections;

/**
 * Addon interface for extending /claim and /aclaim tab completion and command flow.
 * <p>
 * Register via {@link ClaimCommandAddonRegistry#register(ClaimCommandAddon)}.
 */
public interface ClaimCommandAddon {

    /**
     * Provide additional tab completions for a subcommand.
     * These are merged with GP3D's native completions (additive).
     *
     * @param sender      the command sender
     * @param rootCommand "claim" or "aclaim"
     * @param subcommand  canonical subcommand name (e.g. "list", "abandon", "trust")
     * @param args        subcommand arguments (excluding the subcommand itself)
     * @return additional completions to add; empty list if none
     */
    List<String> getTabCompletions(CommandSender sender, String rootCommand, String subcommand, String[] args);

    /**
     * Provide additional subcommand names for the root command (args.length == 0 case).
     * E.g. add "rent" or "sell" to /claim tab completion if your addon provides those.
     *
     * @param sender      the command sender
     * @param rootCommand "claim" or "aclaim"
     * @return additional subcommand names; empty list if none
     */
    default List<String> getSubcommandCompletions(CommandSender sender, String rootCommand) {
        return Collections.emptyList();
    }

    /**
     * Handle an addon-defined subcommand under /claim or /aclaim.
     * This is only consulted when GP3D does not already own the subcommand.
     *
     * @param context command context, including selected/current claim state when available
     * @return true if the addon handled the command, otherwise false
     */
    default boolean handleSubcommand(ClaimCommandContext context) {
        return false;
    }
}
