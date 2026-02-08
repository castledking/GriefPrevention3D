package com.griefprevention.api;

import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry for addons that extend /claim and /aclaim tab completion.
 * Addons register here and their completions are merged with GP3D's native completions.
 */
public final class ClaimCommandAddonRegistry {

    private static final List<ClaimCommandAddon> addons = new CopyOnWriteArrayList<>();

    private ClaimCommandAddonRegistry() {}

    /**
     * Register an addon to provide additional tab completions.
     */
    public static void register(ClaimCommandAddon addon) {
        if (addon != null && !addons.contains(addon)) {
            addons.add(addon);
        }
    }

    /**
     * Unregister an addon.
     */
    public static void unregister(ClaimCommandAddon addon) {
        addons.remove(addon);
    }

    /**
     * Get additional tab completions from all registered addons.
     * Filters by prefix and deduplicates.
     */
    public static List<String> getAdditionalTabCompletions(
            CommandSender sender,
            String rootCommand,
            String subcommand,
            String[] args,
            String prefix
    ) {
        List<String> result = new ArrayList<>();
        String lowerPrefix = (prefix != null ? prefix : "").toLowerCase();

        for (ClaimCommandAddon addon : addons) {
            try {
                List<String> extra = addon.getTabCompletions(sender, rootCommand, subcommand, args);
                if (extra != null) {
                    for (String s : extra) {
                        if (s != null && !s.isBlank() && !result.contains(s)
                                && (lowerPrefix.isEmpty() || s.toLowerCase().startsWith(lowerPrefix))) {
                            result.add(s);
                        }
                    }
                }
            } catch (Exception e) {
                // Log but don't break other addons
                org.bukkit.Bukkit.getLogger().warning(
                        "[GriefPrevention] ClaimCommandAddon " + addon.getClass().getName() + " threw: " + e.getMessage());
            }
        }
        return result;
    }

    /**
     * Get additional subcommand completions from addons (for args.length == 0).
     */
    public static List<String> getAdditionalSubcommandCompletions(CommandSender sender, String rootCommand, String prefix) {
        List<String> result = new ArrayList<>();
        String lowerPrefix = (prefix != null ? prefix : "").toLowerCase();

        for (ClaimCommandAddon addon : addons) {
            try {
                List<String> extra = addon.getSubcommandCompletions(sender, rootCommand);
                if (extra != null) {
                    for (String s : extra) {
                        if (s != null && !s.isBlank() && !result.contains(s)
                                && (lowerPrefix.isEmpty() || s.toLowerCase().startsWith(lowerPrefix))) {
                            result.add(s);
                        }
                    }
                }
            } catch (Exception e) {
                org.bukkit.Bukkit.getLogger().warning(
                        "[GriefPrevention] ClaimCommandAddon " + addon.getClass().getName() + " threw: " + e.getMessage());
            }
        }
        return result;
    }
}
