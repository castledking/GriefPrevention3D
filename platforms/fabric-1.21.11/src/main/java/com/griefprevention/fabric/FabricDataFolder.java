package com.griefprevention.fabric;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class FabricDataFolder
{
    private static final String DEFAULT_CONFIG = """
            # GriefPrevention3D Fabric uses the same data folder name and top-level config shape as the Paper plugin.
            # Only the options wired by the Fabric port are active right now.
            GriefPrevention:
              ConfigVersion: 1
              PvP:
                AllowContainerAccess: false
                AllowRespawnAnchor: false
              Claims:
                Mode:
                  world: Survival
                  world_nether: Disabled
                  world_the_end: Disabled
                InvestigationTool: STICK
                ModificationTool: GOLDEN_SHOVEL
                MinimumWidth: 5
                MinimumArea: 100
                AllowNestedSubClaims: false
                AllowShapedClaims: false
                UseClaimSelectSessions: true
                UseClaimSelectedMessages: false
                FireSpreadsInClaims: false
                FireDamagesInClaims: false
              VisualizationGlow: false
              FireSpreads: false
              FireDestroys: false
            """;

    private static final String DEFAULT_MESSAGES = """
            # GriefPrevention3D Fabric keeps message keys under the same Messages.* root as the Paper plugin.
            # This file currently seeds the native Fabric messages; more Paper messages will be added as features port.
            Messages:
              BlockNotClaimed: "No one has claimed this block."
              BlockClaimed: "That block has been claimed by {0}."
              NoCreateClaimPermission: "You don't have permission to claim land."
              ResizeStart: "Resizing claim.  Use your shovel again at the new location for this corner."
              ClaimStart: "Claim corner set!  Use the shovel again at the opposite corner to claim a rectangle of land.  To cancel, put your shovel away."
              NewClaimTooNarrow: "This claim would be too small.  Any claim must be at least {0} blocks wide."
              ResizeClaimTooNarrow: "This new size would be too small.  Claims must be at least {0} blocks wide."
              CreateClaimFailOverlapShort: "Your selected area overlaps an existing claim."
              CreateClaimSuccess: "Claim created!  Use /trust to share it with friends."
              ClaimResizeSuccess: "Claim resized.  {0} available claim blocks remaining."
              OnlyOwnersModifyClaims: "Only {0} can modify this claim."
              NotYourClaim: "This isn't your claim."
              DeleteClaimMissing: "There's no claim here."
              DeleteSuccess: "Claim deleted."
              NoAccessPermission: "You don't have {0}'s permission to use that."
              NoBuildPermission: "You don't have {0}'s permission to build here."
              NoContainersPermission: "You don't have {0}'s permission to use that."
              OwnerNameForAdminClaims: "an administrator"
            """;

    private FabricDataFolder()
    {
    }

    static void ensureDefaults(@NotNull Path dataFolder, @NotNull Logger logger)
    {
        try
        {
            Files.createDirectories(dataFolder);
            createIfMissing(dataFolder.resolve("config.yml"), DEFAULT_CONFIG);
            createIfMissing(dataFolder.resolve("messages.yml"), DEFAULT_MESSAGES);
        }
        catch (IOException e)
        {
            logger.warn("Could not initialize Fabric data defaults in {}.", dataFolder, e);
        }
    }

    private static void createIfMissing(@NotNull Path file, @NotNull String contents) throws IOException
    {
        if (Files.exists(file))
        {
            return;
        }

        Files.writeString(file, contents, StandardCharsets.UTF_8);
    }
}
