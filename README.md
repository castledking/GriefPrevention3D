<p align="center">
<img alt="GriefPrevention3D" width=100% height=auto src="https://repository-images.githubusercontent.com/1022939485/d5c4ca88-6bc7-445d-b136-006e5206c1be">
</p>

<h1 align="center">The self-service anti-griefing plugin for Minecraft servers — now with full 3D subdivisions</h1>

<p align="center">
    <a href="https://modrinth.com/plugin/griefprevention-3d-subdivisions"><img alt="Download on Modrinth" src="https://img.shields.io/badge/Download%20on-Modrinth-1bd96a?style=for-the-badge&logo=modrinth&logoColor=white"></a>
    <a href="https://discord.com/invite/pCKdCX6nYr"><img src="https://img.shields.io/badge/Discord-Community-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord Community" /></a>
    <a href="https://github.com/castledking/GriefPrevention3D/issues"><img src="https://img.shields.io/badge/GitHub-Issues-181717?style=for-the-badge&logo=github" alt="GitHub Issues"></a>
    <a href="https://github.com/castledking/GriefPrevention3D/wiki"><img src="https://img.shields.io/badge/GitHub-Wiki-181717?style=for-the-badge&logo=github" alt="GitHub Wiki"></a>
</p>

Stop _responding_ to grief and prevent it instead. GriefPrevention stops grief before it starts automatically without any effort from administrators, and with very little (self service) effort from players.

**GriefPrevention3D** is a fork of the popular **GriefPrevention** plugin that adds full 3D subdivision support to land claims. Players can now create subdivisions with precise height boundaries for more complex builds.

##### [Watch this video](https://www.youtube.com/watch?v=hKrA6NXn7Sc) to learn more how GriefPrevention works in-game.
[![GriefPrevention Youtube Tutorial](https://img.youtube.com/vi/hKrA6NXn7Sc/0.jpg)](https://www.youtube.com/watch?v=hKrA6NXn7Sc)

---

## Key Features

- **3D Subdivisions**
  Create subdivisions with exact Y-level boundaries.
  ```
  /3dsubdivideclaims
  ```
  Use this command to switch to 3D subdivision mode.
####
- **3D Admin Claims**
  Create free, height-bounded administrative claims with exact Y coordinates.
  ```
  /3dadminclaims
  ```
  Use this command or `/aclaim mode admin3d` to switch to 3D admin claim mode.
  Requires the `griefprevention.adminclaims` permission.
####
- **Shaped Claims**
  ```
  AllowShapedClaims: false
  ```
  Set to **true** to enable non-rectangular claims.
  `/shapedclaims`
  Use this command to switch to the shaped claims mode.
####
- **Merge Claims**
  Merge two of your claims into a single larger claim.
  ```
  /mergeclaims
  ```
  Stand in the first claim and run `/mergeclaims`, then go to the second claim and run `/mergeclaims` again or right-click with your golden shovel. Both claims and the area between them become one claim.
  Can also be done with `/shapedclaims` — shape a path that overlaps another claim you own and they merge automatically.
####
- **Nested Subclaims**
  ```
  AllowNestedSubClaims: false
  ```
  Set to **true** to allow subdivisions inside other subdivisions.
####
- **Visualization Glow**
  ```
  VisualizationGlow: false
  ```
  Set to **true** to enable glowing claim boundary visualization. (Requires 1.19.3+)
####
- **Unified Command Handler**
  In **GriefPreventionData/alias.yml**:
  ```
  enabled: true
  ```
  Provides unified commands like:
  `/claim create`
  `/claim trust`
  `/claim abandon`
  [View Docs](https://github.com/castledking/GriefPrevention3D/blob/master/src/main/resources/alias.yml)
####
- **Neighbor Trust & Minimum Distance**
  ```
  GriefPrevention:
    Claims:
      MinimumDistance: 0
  ```
  Enforce a minimum distance between top-level claims to prevent claim spam and overcrowding. When set, players cannot create claims within the configured distance of another player's claim.
  - `/claim trust <player> neighbor` — Grant a player neighbor trust so they can bypass minimum distance checks for your claims.
  - `/neighbortrust <player>` (alias: `/distancetrust <player>`) — Standalone command for the same purpose.
  - `/claim distance check` — Show the configured minimum distance and list nearby claims.
  - `/claim distance toggle` — While standing in your own claim, toggle whether ALL players can bypass minimum distance for that claim.
  - Existing nearby claims auto-grant neighbor trust to each other, cleaned up automatically when claims are abandoned.
####
- **Wither Explosion Toggle**
  `/witherexplosions`
  Use this command to toggle wither explosions inside your claim.
####
- **Subtle Changes**
  - Resizing a claim now selects it and is accessible using common commands like `/claim abandon` or `/claim trust` during that resize session
  - `/restrictsubclaim` while standing in main claims now instantly restricts all subdivisions inside
  - `/trustlist` now shows inherited permissions
  - Split the `griefprevention.eavesdrop` permission to `griefprevention.eavesdrop.pm` & `griefprevention.eavesdrop.softmute` for more granular permission control
  - **Per-Player Locale**: Enabled by default. Players with a Spanish or Portuguese client locale receive messages in their language automatically, while others receive English. Disable with `PerPlayerLocale: false` in config.yml.
  - Various bug fixes and quality-of-life improvements
  - Full compatibility with original GriefPrevention features
  - Works with Spigot, Paper, Purpur, and Folia
  - Maintains all anti-grief protections

## Supported Platforms: Spigot, Paper, Purpur, and Folia.
### GriefPrevention3D targets and supports 1.8 - latest available version of these platforms.

## Download
### [⬇ Download the GriefPrevention3D.jar plugin here.](https://github.com/castledking/GriefPrevention3D/releases)

## Documentation
For usage instructions, see the official [GriefPrevention documentation](https://r.griefprevention.com/docs).

## Addons
### [Addons](https://r.griefprevention.com/addons) provide additional features to GriefPrevention. Some of these addons are listed in [GitHub Discussions](https://r.griefprevention.com/addons)

## Support
- [📖 Documentation](https://r.griefprevention.com/docs) - Learn how GriefPrevention works. Contains answers to most questions.
- [Issue Tracker](https://github.com/castledking/GriefPrevention3D/issues) - Report problems or bugs on the issue tracker. Check if someone else reported your issue before posting.
- [GitHub Discussions](https://github.com/castledking/GriefPrevention3D/discussions) - New ideas, feature requests, or other general discussions.
- [Discord Community](https://discord.com/invite/pCKdCX6nYr)

## Original Plugin
This is a fork of [GriefPrevention](https://www.spigotmc.org/resources/griefprevention.1884/) by RoboMWM.

## GriefPrevention Legacy

GriefPrevention Legacy is the "friendly" name of GriefPrevention version 16. GriefPrevention version 16 will continue to be officially supported with new updates and releases, and is currently the version we recommend for use on production servers.

GriefPrevention Legacy's development exists in the `legacy/v16` branch; be sure to target this branch if you intend to create any pull requests for GriefPrevention Legacy.

## Version 17 and above

Newer major versions of GriefPrevention are developed on the `master` branch. These new versions contain **breaking changes.** Please **do not** use these versions of GriefPrevention on production servers!

---

[![Weird flex but ok](https://bstats.org/signatures/bukkit/GriefPrevention-legacy.svg)](https://bstats.org/plugin/bukkit/GriefPrevention-legacy)
