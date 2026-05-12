<p align="center">
<img alt="GriefPrevention3D" width=100% height=auto src="https://repository-images.githubusercontent.com/1022939485/98eef4fa-4d0c-47c8-83f6-78f2e2112927">
</p>

<h1 align="center">The self-service anti-griefing plugin for Minecraft servers — now with full 3D subdivisions</h1>

<p align="center">
  <a href="https://modrinth.com/plugin/griefprevention-3d-subdivisions"><img alt="Download on Modrinth" src="https://img.shields.io/badge/Download%20on-Modrinth-1bd96a?style=for-the-badge&logo=modrinth&logoColor=white">
  <a href="https://github.com/castledking/GriefPrevention3D"><img src="https://img.shields.io/badge/GitHub-Repository-181717?style=for-the-badge&logo=github" alt="GitHub Repository"></a>
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
  `/3dsubdivideclaims`
  Use this command to switch to 3D subdivision mode.

- **3D Admin Claims**
  Create free, height-bounded administrative claims with exact Y coordinates.
  `/3dadminclaims`
  Use this command or `/aclaim mode admin3d` to switch to 3D admin claim mode.
  Requires the `griefprevention.adminclaims` permission.

- **Shaped Claims**
  ```
  AllowShapedClaims: false
  ```
  Set to **true** to enable non-rectangular claims.
  `/shapedclaims`
  Use this command to switch to the shaped claims mode.

- **Nested Subclaims**
  ```
  AllowNestedSubClaims: false
  ```
  Set to **true** to allow subdivisions inside other subdivisions.

- **Visualization Glow**
  ```
  VisualizationGlow: false
  ```
  Set to **true** to enable glowing claim boundary visualization. (Requires 1.19.3+)

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

- **Wither Explosion Toggle**
  `/witherexplosions`
  Use this command to toggle wither explosions inside your claim.

- **Subtle Changes**
  - Resizing a claim now selects it and is accessible using common commands like `/claim abandon` or `/claim trust` during that resize session
  - `/restrictsubclaim` while standing in main claims now instantly restricts all subdivisions inside
  - `/trustlist` now shows inherited permissions
  - Split the `griefprevention.eavesdrop` permission to `griefprevention.eavesdrop.pm` & `griefprevention.eavesdrop.softmute` for more granular permission control
  - Various bug fixes and quality-of-life improvements
  - Full compatibility with original GriefPrevention features
  - Works with Spigot, Paper, Purpur, and Folia
  - Maintains all anti-grief protections

## Supported Platforms: Spigot, Paper, Purpur, and Folia.
### GriefPrevention3D targets and supports the latest available version of these platforms. Older versions of GriefPrevention can be found on [BukkitDev](https://dev.bukkit.org/projects/grief-prevention/files). These older versions are not supported.
Other server implementations of the Bukkit API _should_ work, but are untested.

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
