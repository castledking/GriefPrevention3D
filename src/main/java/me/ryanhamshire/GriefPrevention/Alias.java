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

import com.griefprevention.compat.Compat;
import org.jetbrains.annotations.NotNull;

/**
 * Default configuration values for alias.yml
 * These are used to populate missing keys in user configurations
 */
public enum Alias {
    // Commands section
    ClaimCommand(
        "enable: true" + "\n" +
"commands: [claim]" + "\n" +
"description: Command to manage your claim(s)" + "\n" +
"permission: griefprevention.claims" + "\n" +
"fallback: ''" + "\n" +
"" + "\n",
        "claim"
    ),

    AClaimCommand(
        "enable: true" + "\n" +
"commands: [aclaim]" + "\n" +
"description: Command to manage administrative claims" + "\n" +
"permission: griefprevention.adminclaims" + "\n" +
"fallback: ''" + "\n" +
"" + "\n",
        "aclaim"
    ),

    // Subcommands section - claim commands
    ClaimCreate(
        "enable: true" + "\n" +
"commands: [create]" + "\n" +
"standalone: [createclaim]" + "\n" +
"usage: \"/claim create [radius]\"" + "\n" +
"description: Create or expand a claim centered on you." + "\n" +
"arguments:" + "\n" +
"  radius:" + "\n" +
"    type: integer" + "\n" +
"" + "\n",
        "createclaim"
    ),

    ClaimTrust(
        "enable: true" + "\n" +
"commands: [trust]" + "\n" +
"standalone: [trust]" + "\n" +
"usage: \"/claim trust <player> [type]\"" + "\n" +
"description: Grant a player access to your claim." + "\n" +
"arguments:" + "\n" +
"  player:" + "\n" +
"    type: player" + "\n" +
"  type:" + "\n" +
"    options:" + "\n" +
"      access: [access]" + "\n" +
"      container: [container]" + "\n" +
"      permission: [permission]" + "\n" +
"      build: [build]" + "\n" +
"      neighbor: [neighbor]" + "\n" +
"" + "\n",
        "trust"
    ),

    ClaimUntrust(
        "enable: true" + "\n" +
"commands: [untrust]" + "\n" +
"standalone: [untrust]" + "\n" +
"usage: \"/claim untrust <player|all>\"" + "\n" +
"description: Revoke claim access from a player or everyone." + "\n" +
"arguments:" + "\n" +
"  options:" + "\n" +
"    player: player" + "\n" +
"    all: [all]" + "\n" +
"    public: [public]" + "\n" +
"" + "\n",
        "untrust"
    ),

    ClaimTrustlist(
        "enable: true" + "\n" +
"commands: [trustlist]" + "\n" +
"standalone: [trustlist]" + "\n" +
"usage: \"/claim trustlist\"" + "\n" +
"description: Show players who have access to this claim." + "\n" +
"" + "\n",
        "trustlist"
    ),

    ClaimList(
        "enable: true" + "\n" +
"commands: [list]" + "\n" +
"standalone: [claimslist]" + "\n" +
"usage: \"/claim list [player]\"" + "\n" +
"description: List claims owned by you or another player." + "\n" +
"arguments:" + "\n" +
"  player:" + "\n" +
"    type: player" + "\n" +
"" + "\n",
        "claimslist"
    ),

    ClaimMode(
        "enable: true" + "\n" +
"commands: [mode]" + "\n" +
"standalone: [basicclaims, shapedclaims, shapedclaim]" + "\n" +
"usage: \"/claim mode <basic|2d|3d|shaped>\"" + "\n" +
"description: Change your golden shovel claim mode." + "\n" +
"arguments:" + "\n" +
"  mode:" + "\n" +
"    options:" + "\n" +
"      basic: [basic]" + "\n" +
"      2d: [2d, subdivide]" + "\n" +
"      3d: [3d]" + "\n" +
"      shaped: [shaped]" + "\n" +
"      merge: [merge]" + "\n" +
"" + "\n",
        "basicclaims"
    ),

    ClaimRestrictSubclaim(
        "enable: true" + "\n" +
"commands: [restrictsubclaim]" + "\n" +
"standalone: [restrictsubclaim]" + "\n" +
"usage: \"/claim restrictsubclaim\"" + "\n" +
"description: Toggle whether a subdivision inherits parent permissions." + "\n" +
"" + "\n",
        "restrictsubclaim"
    ),

    ClaimBuyBlocks(
        "enable: true" + "\n" +
"commands: [buyblocks]" + "\n" +
"standalone: [buyclaimblocks]" + "\n" +
"usage: \"/claim buyblocks <amount>\"" + "\n" +
"description: Purchase additional claim blocks with server currency." + "\n" +
"arguments:" + "\n" +
"  amount:" + "\n" +
"    type: integer" + "\n" +
"" + "\n",
        "buyclaimblocks"
    ),

    ClaimSellBlocks(
        "enable: true" + "\n" +
"commands: [sellblocks]" + "\n" +
"standalone: [sellclaimblocks]" + "\n" +
"usage: \"/claim sellblocks <amount>\"" + "\n" +
"description: Sell claim blocks for server currency." + "\n" +
"arguments:" + "\n" +
"  amount:" + "\n" +
"    type: integer" + "\n" +
"" + "\n",
        "sellclaimblocks"
    ),

    ClaimExplosions(
        "enable: true" + "\n" +
"commands: [explosions]" + "\n" +
"standalone: [claimexplosions]" + "\n" +
"usage: \"/claim explosions [on|off]\"" + "\n" +
"description: Toggle explosions inside your current claim." + "\n" +
"arguments:" + "\n" +
"  state:" + "\n" +
"    options:" + "\n" +
"      on: [on]" + "\n" +
"      off: [off]" + "\n" +
"" + "\n",
        "explosions"
    ),

    ClaimWitherExplosions(
        "enable: true" + "\n" +
"commands: [witherexplosions]" + "\n" +
"standalone: [witherexplosions, witherexplosion]" + "\n" +
"usage: \"/claim witherexplosions [on|off]\"" + "\n" +
"description: Toggle wither explosions inside your current claim." + "\n" +
"permission: griefprevention.witherexplosions" + "\n" +
"arguments:" + "\n" +
"  state:" + "\n" +
"    options:" + "\n" +
"      on: [on]" + "\n" +
"      off: [off]" + "\n" +
"" + "\n",
        "witherexplosions"
    ),

    ClaimPvp(
        "enable: true" + "\n" +
"commands: [pvp]" + "\n" +
"standalone: [claimpvp]" + "\n" +
"usage: \"/claim pvp [true|false] [confirm]\"" + "\n" +
"description: Toggle PvP in your current claim." + "\n" +
"permission: griefprevention.claims" + "\n" +
"arguments:" + "\n" +
"  state:" + "\n" +
"    options:" + "\n" +
"      \"true\": [\"true\"]" + "\n" +
"      \"false\": [\"false\"]" + "\n" +
"" + "\n",
        "claimpvp"
    ),

    ClaimAbandon(
        "enable: true" + "\n" +
"commands: [abandon]" + "\n" +
"standalone: [abandonclaim]" + "\n" +
"usage: \"/claim abandon [all|toplevel]\"" + "\n" +
"description: Abandon the claim you are standing in, or use 'toplevel' to delete it and all subdivisions, or 'all' to abandon every claim you own." + "\n" +
"arguments:" + "\n" +
"  scope:" + "\n" +
"    options:" + "\n" +
"      all: [all]" + "\n" +
"      toplevel: [toplevel]" + "\n" +
"" + "\n",
        "abandonclaim"
    ),

    ClaimSiege(
        "enable: true" + "\n" +
"commands: [siege]" + "\n" +
"standalone: [siege]" + "\n" +
"usage: \"/claim siege <player>\"" + "\n" +
"description: Challenge another player to a siege (if enabled)." + "\n" +
"arguments:" + "\n" +
"  player:" + "\n" +
"    type: player" + "\n" +
"" + "\n",
        "siege"
    ),

    ClaimTrapped(
        "enable: true" + "\n" +
"commands: [trapped]" + "\n" +
"standalone: [trapped]" + "\n" +
"usage: \"/claim trapped\"" + "\n" +
"description: Attempt to escape if you are stuck inside a claim." + "\n" +
"" + "\n",
        "trapped"
    ),

    ClaimExpand(
        "enable: true" + "\n" +
"commands: [expand]" + "\n" +
"standalone: [expandclaim, extendclaim]" + "\n" +
"usage: \"/claim expand <numberOfBlocks>\"" + "\n" +
"description: Expand the claim you're standing in by pushing or pulling its boundary." + "\n" +
"arguments:" + "\n" +
"  numberOfBlocks:" + "\n" +
"    type: integer-negative" + "\n" +
"" + "\n",
        "expandclaim"
    ),

    ClaimHelp(
        "enable: true" + "\n" +
"commands: [help]" + "\n" +
"standalone: [claimhelp]" + "\n" +
"usage: \"/claim help [page]\"" + "\n" +
"description: View a list of all available claim subcommands." + "\n" +
"arguments:" + "\n" +
"  page:" + "\n" +
"    type: integer" + "\n" +
"" + "\n",
        "claimhelp"
    ),

    ClaimDistanceCheck(
        "enable: true" + "\n" +
"commands: [distance, checkdistance]" + "\n" +
"standalone: [checkclaimdistance, claimcheckdistance]" + "\n" +
"usage: \"/claim distance check\"" + "\n" +
"description: Check the minimum distance to nearby claims." + "\n" +
"permission: griefprevention.checkclaimdistance" + "\n" +
"" + "\n",
        "checkclaimdistance"
    ),

    ClaimDistanceToggle(
        "enable: true" + "\n" +
"commands: [distancetoggle, toggledistance]" + "\n" +
"standalone: [toggleclaimdistance, claimtoggledistance]" + "\n" +
"usage: \"/claim distance toggle\"" + "\n" +
"description: Toggle whether all players can bypass minimum distance for your claim." + "\n" +
"permission: griefprevention.toggleclaimdistance" + "\n" +
"" + "\n",
        "toggleclaimdistance"
    ),

    // Subcommands section - aclaim commands
    AClaimRestore(
        "enable: true" + "\n" +
"commands: [restore]" + "\n" +
"standalone: [restorenature]" + "\n" +
"usage: \"/aclaim restore [type] [radius]\"" + "\n" +
"description: Restore an area to nature. Types: nature (default), aggressive, fill." + "\n" +
"arguments:" + "\n" +
"  type:" + "\n" +
"    options:" + "\n" +
"      nature: [nature]" + "\n" +
"      aggressive: [aggressive]" + "\n" +
"      fill: [fill]" + "\n" +
"  radius:" + "\n" +
"    type: integer" + "\n" +
"" + "\n",
        "restorenature"
    ),

    AClaimIgnore(
        "enable: true" + "\n" +
"commands: [ignore]" + "\n" +
"standalone: [ignoreclaims]" + "\n" +
"usage: \"/aclaim ignore\"" + "\n" +
"description: Toggle ignoring nearby claims." + "\n" +
"" + "\n",
        "ignoreclaims"
    ),

    AClaimMode(
        "enable: true" + "\n" +
"commands: [mode]" + "\n" +
"standalone: [adminclaims]" + "\n" +
"usage: \"/aclaim mode <admin|admin3d>\"" + "\n" +
"description: Switch your shovel to admin-claim mode." + "\n" +
"arguments:" + "\n" +
"  mode:" + "\n" +
"    options:" + "\n" +
"      admin: [admin]" + "\n" +
"      admin3d: [admin3d]" + "\n" +
"" + "\n",
        "adminclaims"
    ),

    AClaimAdminList(
        "enable: true" + "\n" +
"commands: [adminlist]" + "\n" +
"standalone: [adminclaimslist]" + "\n" +
"usage: \"/adminlist\"" + "\n" +
"description: List administrative claims on the current server." + "\n" +
"" + "\n",
        "adminclaimslist"
    ),

    AClaimList(
        "enable: true" + "\n" +
"commands: [adminclaimslist]" + "\n" +
"usage: \"/aclaim list [player]\"" + "\n" +
"description: Show claims owned by a player (including admin claims)." + "\n" +
"arguments:" + "\n" +
"  player:" + "\n" +
"    type: player" + "\n" +
"" + "\n",
        "adminclaimslist"
    ),

    AClaimCheckExpiry(
        "enable: true" + "\n" +
"commands: [checkexpiry]" + "\n" +
"standalone: [claimcheckexpiry]" + "\n" +
"usage: \"/aclaim checkexpiry <player>\"" + "\n" +
"description: View claim expiration details for a player." + "\n" +
"arguments:" + "\n" +
"  player:" + "\n" +
"    type: player" + "\n" +
"" + "\n",
        "claimcheckexpiry"
    ),

    AClaimBlocks(
        "enable: true" + "\n" +
"commands: [blocks]" + "\n" +
"standalone: []" + "\n" +
"usage: \"/aclaim blocks <bonus|accrued> <player|all> <amount>\"" + "\n" +
"description: Adjust a player's claim block balance." + "\n" +
"arguments:" + "\n" +
"  type:" + "\n" +
"    options:" + "\n" +
"      bonus: [bonus]" + "\n" +
"      accrued: [accrued]" + "\n" +
"  player:" + "\n" +
"    type: player" + "\n" +
"    options:" + "\n" +
"      all: [all]" + "\n" +
"  amount:" + "\n" +
"    type: integer" + "\n" +
"" + "\n",
        "aclaimblocks"
    ),

    AClaimDelete(
        "enable: true" + "\n" +
"commands: [delete]" + "\n" +
"standalone: [deleteclaim]" + "\n" +
"usage: \"/aclaim delete <player|world|all>\"" + "\n" +
"description: Delete claims owned by a player or within a world." + "\n" +
"arguments:" + "\n" +
"  scope:" + "\n" +
"    options:" + "\n" +
"      player: [player]" + "\n" +
"      world: [world]" + "\n" +
"      all: [all]" + "\n" +
"" + "\n",
        "deleteclaim"
    ),

    AClaimTransfer(
        "enable: true" + "\n" +
"commands: [transfer]" + "\n" +
"standalone: [transferclaim]" + "\n" +
"usage: \"/aclaim transfer <player>\"" + "\n" +
"description: Transfer the claim you are standing in to another player." + "\n" +
"arguments:" + "\n" +
"  player:" + "\n" +
"    type: player" + "\n" +
"" + "\n",
        "transferclaim"
    ),

    AClaimMakeAdmin(
        "enable: true" + "\n" +
"commands: [makeadmin]" + "\n" +
"standalone: [makeadmin]" + "\n" +
"usage: \"/aclaim makeadmin\"" + "\n" +
"description: Convert the current top-level claim to an administrative claim." + "\n" +
"permission: griefprevention.adminclaims.convert" + "\n" +
"" + "\n",
        "makeadmin"
    ),

    AClaimMakeBasic(
        "enable: true" + "\n" +
"commands: [makebasic]" + "\n" +
"standalone: [makebasic]" + "\n" +
"usage: \"/aclaim makebasic\"" + "\n" +
"description: Convert the current top-level administrative claim to a basic claim you own." + "\n" +
"permission: griefprevention.adminclaims.convert" + "\n" +
"" + "\n",
        "makebasic"
    ),

    AClaimHelp(
        "enable: true" + "\n" +
"commands: [help]" + "\n" +
"standalone: [aclaimhelp]" + "\n" +
"usage: \"/aclaim help [page]\"" + "\n" +
"description: View a list of all available admin claim subcommands." + "\n" +
"arguments:" + "\n" +
"  page:" + "\n" +
"    type: integer" + "\n" +
"" + "\n",
        "aclaimhelp"
    ),

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
        return "# ============================================" + "\n" +
"#      GRIEFPREVENTION ALIAS CONFIGURATION" + "\n" +
"# ============================================" + "\n" +
"# Customize command names, translations, and tab completion." + "\n" +
"# Full documentation: https://github.com/castledking/GriefPrevention3D/tree/master/src/main/resources/alias.yml" + "\n" +
"# Reload changes with: /gpreload" + "\n" +
"" + "\n" +
"# Set to false to disable all alias customization (uses default command names)" + "\n" +
"enabled: true" + "\n" +
"# Set to false to disable standalone commands (/trust, /trapped, etc.); only /claim and /aclaim (or their translations) will be registered" + "\n" +
"standalone: true" + "\n" +
"" + "\n" +
"commands:" + "\n" +
"  claim:" + "\n" +
"    enable: true" + "\n" +
"    commands: [claim]" + "\n" +
"    description: Command to manage your claim(s)" + "\n" +
"    permission: griefprevention.claims" + "\n" +
"    fallback: ''" + "\n" +
"" + "\n" +
"  aclaim:" + "\n" +
"    enable: true" + "\n" +
"    commands: [aclaim]" + "\n" +
"    description: Command to manage administrative claims" + "\n" +
"    permission: griefprevention.adminclaims" + "\n" +
"    fallback: ''" + "\n" +
"" + "\n" +
"subcommands:" + "\n" +
"  claim:" + "\n" +
"    create:" + "\n" +
"      enable: true" + "\n" +
"      commands: [create]" + "\n" +
"      standalone: [createclaim]" + "\n" +
"      usage: \"/claim create [radius]\"" + "\n" +
"      description: Create or expand a claim centered on you." + "\n" +
"      arguments:" + "\n" +
"        radius:" + "\n" +
"          type: integer" + "\n" +
"" + "\n" +
"    trust:" + "\n" +
"      enable: true" + "\n" +
"      commands: [trust]" + "\n" +
"      standalone: [trust]" + "\n" +
"      usage: \"/claim trust <player> [type]\"" + "\n" +
"      description: Grant a player access to your claim." + "\n" +
"      arguments:" + "\n" +
"        player:" + "\n" +
"          type: player" + "\n" +
"        type:" + "\n" +
"          options:" + "\n" +
"            access: [access]" + "\n" +
"            container: [container]" + "\n" +
"            permission: [permission]" + "\n" +
"            build: [build]" + "\n" +
"" + "\n" +
"    untrust:" + "\n" +
"      enable: true" + "\n" +
"      commands: [untrust]" + "\n" +
"      standalone: [untrust]" + "\n" +
"      usage: \"/claim untrust <player|all>\"" + "\n" +
"      description: Revoke claim access from a player or everyone." + "\n" +
"      arguments:" + "\n" +
"        options:" + "\n" +
"          player: player" + "\n" +
"          all: [all]" + "\n" +
"          public: [public]" + "\n" +
"" + "\n" +
"    trustlist:" + "\n" +
"      enable: true" + "\n" +
"      commands: [trustlist]" + "\n" +
"      standalone: [trustlist]" + "\n" +
"      usage: \"/claim trustlist\"" + "\n" +
"      description: Show players who have access to this claim." + "\n" +
"" + "\n" +
"    list:" + "\n" +
"      enable: true" + "\n" +
"      commands: [list]" + "\n" +
"      standalone: [claimslist]" + "\n" +
"      usage: \"/claim list [player]\"" + "\n" +
"      description: List claims owned by you or another player." + "\n" +
"      arguments:" + "\n" +
"        player:" + "\n" +
"          type: player" + "\n" +
"" + "\n" +
"    mode:" + "\n" +
"      enable: true" + "\n" +
"      commands: [mode]" + "\n" +
"      standalone: [basicclaims, shapedclaims, shapedclaim]" + "\n" +
"      usage: \"/claim mode <basic|2d|3d|shaped|merge>\"" + "\n" +
"      description: Change your golden shovel claim mode." + "\n" +
"      arguments:" + "\n" +
"        mode:" + "\n" +
"          options:" + "\n" +
"            basic: [basic]" + "\n" +
"            2d: [2d, subdivide]" + "\n" +
"            3d: [3d]" + "\n" +
"            shaped: [shaped]" + "\n" +
"            merge: [merge]" + "\n" +
"" + "\n" +
"    restrictsubclaim:" + "\n" +
"      enable: true" + "\n" +
"      commands: [restrictsubclaim]" + "\n" +
"      standalone: [restrictsubclaim]" + "\n" +
"      usage: \"/claim restrictsubclaim\"" + "\n" +
"      description: Toggle whether a subdivision inherits parent permissions." + "\n" +
"" + "\n" +
"    explosions:" + "\n" +
"      enable: true" + "\n" +
"      commands: [explosions]" + "\n" +
"      standalone: [claimexplosions]" + "\n" +
"      usage: \"/claim explosions [on|off]\"" + "\n" +
"      description: Toggle explosions inside your current claim." + "\n" +
"      arguments:" + "\n" +
"        state:" + "\n" +
"          options:" + "\n" +
"            on: [on]" + "\n" +
"            off: [off]" + "\n" +
"" + "\n" +
"    witherexplosions:" + "\n" +
"      enable: true" + "\n" +
"      commands: [witherexplosions]" + "\n" +
"      standalone: [witherexplosions, witherexplosion]" + "\n" +
"      usage: \"/claim witherexplosions [on|off]\"" + "\n" +
"      description: Toggle wither explosions inside your current claim." + "\n" +
"      permission: griefprevention.witherexplosions" + "\n" +
"      arguments:" + "\n" +
"        state:" + "\n" +
"          options:" + "\n" +
"            on: [on]" + "\n" +
"            off: [off]" + "\n" +
"" + "\n" +
"    pvp:" + "\n" +
"      enable: true" + "\n" +
"      commands: [pvp]" + "\n" +
"      standalone: [claimpvp]" + "\n" +
"      usage: \"/claim pvp [true|false] [confirm]\"" + "\n" +
"      description: Toggle PvP in your current claim." + "\n" +
"      permission: griefprevention.claims" + "\n" +
"      arguments:" + "\n" +
"        state:" + "\n" +
"          options:" + "\n" +
"            \"true\": [\"true\", \"on\", \"enable\"]" + "\n" +
"            \"false\": [\"false\", \"off\", \"disable\"]" + "\n" +
"" + "\n" +
"    buyblocks:" + "\n" +
"      enable: true" + "\n" +
"      commands: [buyblocks]" + "\n" +
"      standalone: [buyclaimblocks]" + "\n" +
"      usage: \"/claim buyblocks\"" + "\n" +
"      description: Purchase additional claim blocks." + "\n" +
"" + "\n" +
"    sellblocks:" + "\n" +
"      enable: true" + "\n" +
"      commands: [sellblocks]" + "\n" +
"      standalone: [sellclaimblocks]" + "\n" +
"      usage: \"/claim sellblocks\"" + "\n" +
"      description: Sell excess claim blocks for currency." + "\n" +
"" + "\n" +
"    abandon:" + "\n" +
"      enable: true" + "\n" +
"      commands: [abandon]" + "\n" +
"      standalone: [abandonclaim]" + "\n" +
"      usage: \"/claim abandon [all|toplevel]\"" + "\n" +
"      description: Abandon the claim you are standing in, or use 'toplevel' to delete it and all subdivisions, or 'all' to abandon every claim you own." + "\n" +
"      arguments:" + "\n" +
"        scope:" + "\n" +
"          options:" + "\n" +
"            all: [all]" + "\n" +
"            toplevel: [toplevel]" + "\n" +
"" + "\n" +
"    siege:" + "\n" +
"      enable: true" + "\n" +
"      commands: [siege]" + "\n" +
"      standalone: [siege]" + "\n" +
"      usage: \"/claim siege <player>\"" + "\n" +
"      description: Challenge another player to a siege (if enabled)." + "\n" +
"      arguments:" + "\n" +
"        player:" + "\n" +
"          type: player" + "\n" +
"" + "\n" +
"    trapped:" + "\n" +
"      enable: true" + "\n" +
"      commands: [trapped]" + "\n" +
"      standalone: [trapped]" + "\n" +
"      usage: \"/claim trapped\"" + "\n" +
"      description: Attempt to escape if you are stuck inside a claim." + "\n" +
"" + "\n" +
"    expand:" + "\n" +
"      enable: true" + "\n" +
"      commands: [expand]" + "\n" +
"      standalone: [expandclaim, extendclaim]" + "\n" +
"      usage: \"/claim expand <numberOfBlocks>\"" + "\n" +
"      description: Expand the claim you're standing in by pushing or pulling its boundary." + "\n" +
"      arguments:" + "\n" +
"        numberOfBlocks:" + "\n" +
"          type: integer-negative" + "\n" +
"" + "\n" +
"    help:" + "\n" +
"      enable: true" + "\n" +
"      commands: [help]" + "\n" +
"      standalone: [claimhelp]" + "\n" +
"      usage: \"/claim help [page]\"" + "\n" +
"      description: View a list of all available claim subcommands." + "\n" +
"      arguments:" + "\n" +
"        page:" + "\n" +
"          type: integer" + "\n" +
"" + "\n" +
"  aclaim:" + "\n" +
"    restore:" + "\n" +
"      enable: true" + "\n" +
"      commands: [restore]" + "\n" +
"      standalone: [restorenature]" + "\n" +
"      usage: \"/aclaim restore [mode]\"" + "\n" +
"      description: Restore an area to nature using the specified mode." + "\n" +
"      arguments:" + "\n" +
"        mode:" + "\n" +
"          options:" + "\n" +
"            default: [default]" + "\n" +
"            aggressive: [aggressive]" + "\n" +
"            fill: [fill]" + "\n" +
"" + "\n" +
"    restoreaggressive:" + "\n" +
"      enable: true" + "\n" +
"      commands: [restoreaggressive]" + "\n" +
"      standalone: [restorenatureaggressive]" + "\n" +
"      usage: \"/aclaim restoreaggressive\"" + "\n" +
"      description: Switches the shovel tool to aggressive restoration mode." + "\n" +
"" + "\n" +
"    restorefill:" + "\n" +
"      enable: true" + "\n" +
"      commands: [restorefill]" + "\n" +
"      standalone: [restorenaturefill]" + "\n" +
"      usage: \"/aclaim restorefill [radius]\"" + "\n" +
"      description: Switches the shovel tool to fill mode." + "\n" +
"      arguments:" + "\n" +
"        radius:" + "\n" +
"          type: integer" + "\n" +
"" + "\n" +
"    ignore:" + "\n" +
"      enable: true" + "\n" +
"      commands: [ignore]" + "\n" +
"      standalone: [ignoreclaims]" + "\n" +
"      usage: \"/aclaim ignore\"" + "\n" +
"      description: Toggle ignoring nearby claims." + "\n" +
"" + "\n" +
"    mode:" + "\n" +
"      enable: true" + "\n" +
"      commands: [mode]" + "\n" +
"      standalone: [adminclaims]" + "\n" +
"      usage: \"/aclaim mode <admin|admin3d>\"" + "\n" +
"      description: Switch your shovel to admin-claim mode." + "\n" +
"      arguments:" + "\n" +
"        mode:" + "\n" +
"          options:" + "\n" +
"            admin: [admin]" + "\n" +
"            admin3d: [admin3d]" + "\n" +
"" + "\n" +
"    adminlist:" + "\n" +
"      enable: true" + "\n" +
"      commands: [adminlist]" + "\n" +
"      standalone: [adminclaimslist]" + "\n" +
"      usage: \"/aclaim adminlist\"" + "\n" +
"      description: List administrative claims on the current server." + "\n" +
"" + "\n" +
"    checkexpiry:" + "\n" +
"      enable: true" + "\n" +
"      commands: [checkexpiry]" + "\n" +
"      standalone: [claimcheckexpiry]" + "\n" +
"      usage: \"/aclaim checkexpiry <player>\"" + "\n" +
"      description: View claim expiration details for a player." + "\n" +
"      arguments:" + "\n" +
"        player:" + "\n" +
"          type: player" + "\n" +
"" + "\n" +
"    blocks:" + "\n" +
"      enable: true" + "\n" +
"      commands: [blocks]" + "\n" +
"      standalone: []" + "\n" +
"      usage: \"/aclaim blocks <bonus|accrued> <player|all> <amount>\"" + "\n" +
"      description: Adjust a player's claim block balance." + "\n" +
"      arguments:" + "\n" +
"        type:" + "\n" +
"          options:" + "\n" +
"            bonus: [bonus]" + "\n" +
"            accrued: [accrued]" + "\n" +
"        player:" + "\n" +
"          type: player" + "\n" +
"          options:" + "\n" +
"            all: [all]" + "\n" +
"        amount:" + "\n" +
"          type: integer" + "\n" +
"" + "\n" +
"    delete:" + "\n" +
"      enable: true" + "\n" +
"      commands: [delete]" + "\n" +
"      standalone: [deleteclaim]" + "\n" +
"      usage: \"/aclaim delete <player|world|all>\"" + "\n" +
"      description: Delete claims owned by a player or within a world." + "\n" +
"      arguments:" + "\n" +
"        scope:" + "\n" +
"          options:" + "\n" +
"            player: [player]" + "\n" +
"            world: [world]" + "\n" +
"            all: [all]" + "\n" +
"" + "\n" +
"    transfer:" + "\n" +
"      enable: true" + "\n" +
"      commands: [transfer]" + "\n" +
"      standalone: [transferclaim]" + "\n" +
"      usage: \"/aclaim transfer <player>\"" + "\n" +
"      description: Transfer the claim you are standing in to another player." + "\n" +
"      arguments:" + "\n" +
"        player:" + "\n" +
"          type: player" + "\n" +
"" + "\n" +
"    makeadmin:" + "\n" +
"      enable: true" + "\n" +
"      commands: [makeadmin]" + "\n" +
"      standalone: [makeadmin]" + "\n" +
"      usage: \"/aclaim makeadmin\"" + "\n" +
"      description: Convert the current top-level claim to an administrative claim." + "\n" +
"      permission: griefprevention.adminclaims.convert" + "\n" +
"" + "\n" +
"    makebasic:" + "\n" +
"      enable: true" + "\n" +
"      commands: [makebasic]" + "\n" +
"      standalone: [makebasic]" + "\n" +
"      usage: \"/aclaim makebasic\"" + "\n" +
"      description: Convert the current top-level administrative claim to a basic claim you own." + "\n" +
"      permission: griefprevention.adminclaims.convert" + "\n" +
"" + "\n" +
"    help:" + "\n" +
"      enable: true" + "\n" +
"      commands: [help]" + "\n" +
"      standalone: [aclaimhelp]" + "\n" +
"      usage: \"/aclaim help [page]\"" + "\n" +
"      description: View a list of all available admin claim subcommands." + "\n" +
"      arguments:" + "\n" +
"        page:" + "\n" +
"          type: integer" + "\n" +
"" + "\n"
;
    }

    /**
     * Helper method to indent a multi-line string by a specified number of levels
     * (each level is 2 spaces).
     */
    private static @NotNull String indent(@NotNull String text, int levels) {
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        String baseIndent = Compat.repeat("  ", levels);

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
