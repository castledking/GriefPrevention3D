/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2012 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.GriefPrevention;

import org.jetbrains.annotations.NotNull;

/**
 * Default configuration values for alias.yml
 * These are used to populate missing keys in user configurations
 */
public enum Alias {

  // Commands section
  ClaimCommand("""
      enable: true
      commands: [claim]
      description: Command to manage your claim(s)
      permission: griefprevention.claims
      """, "claim"),

  AClaimCommand("""
      enable: true
      commands: [aclaim]
      description: Command to manage administrative claims
      permission: griefprevention.adminclaims
      """, "aclaim"),

  // Subcommands section - claim commands
  ClaimCreate("""
      enable: true
      commands: [create]
      standalone: [createclaim]
      usage: "/claim create [radius]"
      description: Create or expand a claim centered on you.
      arguments:
        radius:
          type: integer
      """, "createclaim"),

  ClaimTrust("""
      enable: true
      commands: [trust]
      standalone: [trust]
      usage: "/claim trust <player> [type]"
      description: Grant a player access to your claim.
      arguments:
        player:
          type: player
        type:
          options:
            access: [access]
            container: [container]
            permission: [permission]
      """, "trust"),

  ClaimUntrust("""
      enable: true
      commands: [untrust]
      standalone: [untrust]
      usage: "/claim untrust <player|all>"
      description: Revoke claim access from a player or everyone.
      arguments:
        target:
          type: player
          options:
            all: [all]
            public: [public]
      """, "untrust"),

  ClaimTrustlist("""
      enable: true
      commands: [trustlist]
      standalone: [trustlist]
      usage: "/claim trustlist"
      description: Show players who have access to this claim.
      """, "trustlist"),

  ClaimList("""
      enable: true
      commands: [list]
      standalone: [claimslist]
      usage: "/claim list [player]"
      description: List claims owned by you or another player.
      arguments:
        player:
          type: player
      """, "claimslist"),

  ClaimMode("""
      enable: true
      commands: [mode]
      standalone: [basicclaims]
      usage: "/claim mode <basic|2d|3d>"
      description: Change your golden shovel claim mode.
      arguments:
        mode:
          options:
            basic: [basic]
            2d: [2d]
            3d: [3d]
      """, "basicclaims"),

  ClaimRestrictSubclaim("""
      enable: true
      commands: [restrictsubclaim]
      standalone: [restrictsubclaim]
      usage: "/claim restrictsubclaim"
      description: Toggle whether a subdivision inherits parent permissions.
      """, "restrictsubclaim"),

  ClaimBuyBlocks("""
      enable: true
      commands: [buyblocks]
      standalone: [buyclaimblocks]
      usage: "/claim buyblocks <amount>"
      description: Purchase additional claim blocks with server currency.
      arguments:
        amount:
          type: integer
      """, "buyclaimblocks"),

  ClaimSellBlocks("""
      enable: true
      commands: [sellblocks]
      standalone: [sellclaimblocks]
      usage: "/claim sellblocks <amount>"
      description: Sell claim blocks for server currency.
      arguments:
        amount:
          type: integer
      """, "sellclaimblocks"),

  ClaimExplosions("""
      enable: true
      commands: [explosions]
      standalone: [claimexplosions]
      usage: "/claim explosions [on|off]"
      description: Toggle explosions inside your current claim.
      arguments:
        state:
          options:
            on: [on]
            off: [off]
      """, "explosions"),

  ClaimAbandon("""
      enable: true
      commands: [abandon]
      standalone: [abandonclaim]
      usage: "/claim abandon [all]"
      description: Abandon the claim you are standing in or all claims you own.
      arguments:
        scope:
          options:
            all: [all]
      """, "abandon"),

  ClaimSiege("""
      enable: true
      commands: [siege]
      standalone: [siege]
      usage: "/claim siege <player>"
      description: Challenge another player to a siege (if enabled).
      arguments:
        player:
          type: player
      """, "siege"),

  ClaimTrapped("""
      enable: true
      commands: [trapped]
      standalone: [trapped]
      usage: "/claim trapped"
      description: Attempt to escape if you are stuck inside a claim.
      """, "trapped"),

  ClaimHelp("""
      enable: true
      commands: [help]
      standalone: [claimhelp]
      usage: "/claim help [page]"
      description: View a list of all available claim subcommands.
      arguments:
        page:
          type: integer
      """, "claimhelp"),

  // Subcommands section - aclaim commands
  AClaimRestore("""
      enable: true
      commands: [restore]
      standalone: [restorenature]
      usage: "/aclaim restore [type] [radius]"
      description: Restore an area to nature. Types: nature (default), aggressive, fill.
      arguments:
        type:
          options:
            nature: [nature]
            aggressive: [aggressive]
            fill: [fill]
        radius:
          type: integer
      """, "restorenature"),

  AClaimIgnore("""
      enable: true
      commands: [ignore]
      standalone: [ignoreclaims]
      usage: "/aclaim ignore"
      description: Toggle ignoring nearby claims.
      """, "ignoreclaims"),

  AClaimMode("""
      enable: true
      commands: [mode]
      standalone: [adminclaims]
      usage: "/aclaim mode <admin>"
      description: Switch your shovel to admin-claim mode.
      arguments:
        mode:
          options:
            admin: [admin]
      """, "adminclaims"),

  AClaimAdminList("""
      enable: true
      commands: [adminlist]
      standalone: [adminclaimslist]
      usage: "/adminlist"
      description: List administrative claims on the current server.
      """, "adminclaimslist"),

  AClaimList("""
      enable: true
      commands: [adminclaimslist]
      usage: "/aclaim list [player]"
      description: Show claims owned by a player (including admin claims).
      arguments:
        player:
          type: player
      """, "adminclaimslist"),

  AClaimCheckExpiry("""
      enable: true
      commands: [checkexpiry]
      standalone: [claimcheckexpiry]
      usage: "/aclaim checkexpiry <player>"
      description: View claim expiration details for a player.
      arguments:
        player:
          type: player
      """, "claimcheckexpiry"),

  AClaimBlocks("""
      enable: true
      commands: [blocks]
      standalone: [adjustbonusclaimblocks]
      usage: "/aclaim blocks <add|set> <player> [amount]"
      description: Adjust a player's claim block balance.
      arguments:
        action:
          options:
            add: [add, adjust]
            set: [set]
        player:
          type: player
        amount:
          type: integer
      """, "adjustbonusclaimblocks"),

  AClaimDelete("""
      enable: true
      commands: [delete]
      standalone: [deleteclaim]
      usage: "/aclaim delete <player|world|all>"
      description: Delete claims owned by a player or within a world.
      arguments:
        scope:
          options:
            player: [player]
            world: [world]
            all: [all]
      """, "deleteclaim"),

  AClaimTransfer("""
      enable: true
      commands: [transfer]
      standalone: [transferclaim]
      usage: "/aclaim transfer <player>"
      description: Transfer the claim you are standing in to another player.
      arguments:
        player:
          type: player
      """, "transferclaim"),

  AClaimHelp("""
      enable: true
      commands: [help]
      standalone: [aclaimhelp]
      usage: "/aclaim help [page]"
      description: View a list of all available admin claim subcommands.
      arguments:
        page:
          type: integer
      """, "aclaimhelp"),

  // Empty subcommands sections (for backwards compatibility)
  ClaimSubcommands(""),

  AClaimSubcommands("");

  final @NotNull String defaultValue;
  final @NotNull String standalone;

  Alias(@NotNull String defaultValue) {
    this(defaultValue, "");
  }

  Alias(@NotNull String defaultValue, @NotNull String standalone) {
    this.defaultValue = defaultValue;
    this.standalone = standalone;
  }

  public @NotNull String getDefaultValue() {
    return defaultValue;
  }

  public @NotNull String getStandalone() {
    return standalone;
  }

  /**
   * Gets all default alias configuration as a single YAML string
   */
  public static @NotNull String getDefaultYaml() {
    return """
        # ============================================
        #      GRIEFPREVENTION ALIAS CONFIGURATION
        # ============================================
        # Customize command names, translations, and tab completion.
        # Full documentation: https://github.com/castledking/GriefPrevention3D/tree/master/src/main/resources/alias.yml
        # Reload changes with: /gpreload

        # Set to false to disable all alias customization (uses default command names)
        enabled: true

        commands:
          claim:
            enable: true
            commands: [claim]
            description: Command to manage your claim(s)
            permission: griefprevention.claims

          aclaim:
            enable: true
            commands: [aclaim]
            description: Command to manage administrative claims
            permission: griefprevention.adminclaims

        subcommands:
          claim:
            create:
              enable: true
              commands: [create]
              standalone: [createclaim]
              usage: "/claim create [radius]"
              description: Create or expand a claim centered on you.
              arguments:
                radius:
                  type: integer

            trust:
              enable: true
              commands: [trust]
              standalone: [trust]
              usage: "/claim trust <player> [type]"
              description: Grant a player access to your claim.
              arguments:
                player:
                  type: player
                type:
                  options:
                    access: [access]
                    container: [container]
                    permission: [permission]

            untrust:
              enable: true
              commands: [untrust]
              standalone: [untrust]
              usage: "/claim untrust <player|all>"
              description: Revoke claim access from a player or everyone.
              arguments:
                player:
                  type: player
                  options:
                    all: [all]
                    public: [public]

            trustlist:
              enable: true
              commands: [trustlist]
              standalone: [trustlist]
              usage: "/claim trustlist"
              description: Show players who have access to this claim.

            list:
              enable: true
              commands: [list]
              standalone: [claimslist]
              usage: "/claim list [player]"
              description: List claims owned by you or another player.
              arguments:
                player:
                  type: player

            mode:
              enable: true
              commands: [mode]
              standalone: [claimmode]
              usage: "/claim mode <basic|2d|3d>"
              description: Change your golden shovel claim mode.
              arguments:
                mode:
                  options:
                    basic: [basic]
                    2d: [2d, subdivide]
                    3d: [3d]

            restrictsubclaim:
              enable: true
              commands: [restrictsubclaim]
              standalone: [restrictsubclaim]
              usage: "/claim restrictsubclaim"
              description: Toggle whether a subdivision inherits parent permissions.

            
            explosions:
              enable: true
              commands: [explosions]
              standalone: [claimexplosions]
              usage: "/claim explosions [on|off]"
              description: Toggle explosions inside your current claim.
              arguments:
                state:
                  options:
                    on: [on]
                    off: [off]

            buyblocks:
              enable: true
              commands: [buyblocks]
              standalone: [buyclaimblocks]
              usage: "/claim buyblocks"
              description: Purchase additional claim blocks.

            sellblocks:
              enable: true
              commands: [sellblocks]
              standalone: [sellclaimblocks]
              usage: "/claim sellblocks"
              description: Sell excess claim blocks for currency.

            abandon:
              enable: true
              commands: [abandon]
              standalone: [abandonclaim]
              usage: "/claim abandon [all]"
              description: Abandon the claim you are standing in or all claims you own.
              arguments:
                scope:
                  options:
                    all: [all]

            siege:
              enable: true
              commands: [siege]
              standalone: [siege]
              usage: "/claim siege <player>"
              description: Challenge another player to a siege (if enabled).
              arguments:
                player:
                  type: player

            trapped:
              enable: true
              commands: [trapped]
              standalone: [trapped]
              usage: "/claim trapped"
              description: Attempt to escape if you are stuck inside a claim.

            help:
              enable: true
              commands: [help]
              standalone: [claimhelp]
              usage: "/claim help [page]"
              description: View a list of all available claim subcommands.
              arguments:
                page:
                  type: integer

          aclaim:
            restore:
              enable: true
              commands: [restore]
              standalone: [restorenature]
              usage: "/aclaim restore [mode]"
              description: Restore an area to nature using the specified mode.
              arguments:
                mode:
                  options:
                    default: [default]
                    aggressive: [aggressive]
                    fill: [fill]

            restoreaggressive:
              enable: true
              commands: [restoreaggressive]
              standalone: [restorenatureaggressive]
              usage: "/aclaim restoreaggressive"
              description: Switches the shovel tool to aggressive restoration mode.

            restorefill:
              enable: true
              commands: [restorefill]
              standalone: [restorenaturefill]
              usage: "/aclaim restorefill [radius]"
              description: Switches the shovel tool to fill mode.
              arguments:
                radius:
                  type: integer

            ignore:
              enable: true
              commands: [ignore]
              standalone: [ignoreclaims]
              usage: "/aclaim ignore"
              description: Toggle ignoring nearby claims.

            mode:
              enable: true
              commands: [mode]
              standalone: [adminclaims]
              usage: "/aclaim mode <admin>"
              description: Switch your shovel to admin-claim mode.
              arguments:
                mode:
                  options:
                    admin: [admin]

            adminlist:
              enable: true
              commands: [adminlist]
              standalone: [adminclaimslist]
              usage: "/aclaim adminlist"
              description: List administrative claims on the current server.

            checkexpiry:
              enable: true
              commands: [checkexpiry]
              standalone: [claimcheckexpiry]
              usage: "/aclaim checkexpiry <player>"
              description: View claim expiration details for a player.
              arguments:
                player:
                  type: player

            blocks:
              enable: true
              commands: [blocks]
              standalone: [adjustbonusclaimblocks]
              usage: "/aclaim blocks <add|set> <player> [amount]"
              description: Adjust a player's claim block balance.
              arguments:
                action:
                  options:
                    add: [add, adjust]
                    set: [set]
                player:
                  type: player
                amount:
                  type: integer

            delete:
              enable: true
              commands: [delete]
              standalone: [deleteclaim]
              usage: "/aclaim delete <player|world|all>"
              description: Delete claims owned by a player or within a world.
              arguments:
                scope:
                  options:
                    player: [player]
                    world: [world]
                    all: [all]

            transfer:
              enable: true
              commands: [transfer]
              standalone: [transferclaim]
              usage: "/aclaim transfer <player>"
              description: Transfer the claim you are standing in to another player.
              arguments:
                player:
                  type: player

            help:
              enable: true
              commands: [help]
              standalone: [aclaimhelp]
              usage: "/aclaim help [page]"
              description: View a list of all available admin claim subcommands.
              arguments:
                page:
                  type: integer
        """;
  }

  /**
   * Helper method to indent a multi-line string by a specified number of levels
   * (each level is 2 spaces).
   */
  private static @NotNull String indent(@NotNull String text, int levels) {
    String[] lines = text.split("\n", -1);
    StringBuilder sb = new StringBuilder();
    String baseIndent = "  ".repeat(levels);

    for (String line : lines) {
      if (line.trim().isEmpty()) {
        sb.append("\n");
        continue;
      }

      // Preserve the existing indentation of the line
      // and add the base indentation level
      String trimmed = line.trim();
      int originalIndent = line.indexOf(trimmed);
      String preservedIndent = originalIndent > 0 ? line.substring(0, originalIndent) : "";

      sb.append(baseIndent).append(preservedIndent).append(trimmed).append("\n");
    }
    return sb.toString();
  }
}