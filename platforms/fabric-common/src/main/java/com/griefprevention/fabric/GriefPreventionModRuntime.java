package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimRepository;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * Shared entrypoint body wired by both {@code GriefPreventionFabric} and
 * {@code GriefPreventionNeoForge}.
 */
public final class GriefPreventionModRuntime
{
    private static volatile ClaimRepository claimRepository;

    private GriefPreventionModRuntime()
    {
    }

    public static @NotNull ClaimRepository initialize(@NotNull ModPlatform platform, @NotNull Logger logger)
    {
        ModPlatform.install(platform);
        // Resolve the calls that differ between the releases this adapter serves before anything uses them.
        FabricVersionCompat.verify();

        Path dataFolder = FabricDataFolder.resolveSharedDataFolder(
                platform.gameDir(),
                platform.configDir(),
                logger
        );
        FabricDataFolder.ensureDefaults(dataFolder, logger);
        FabricClaimRepository claims = new FabricClaimRepository(dataFolder, logger);
        new FabricClaimBlockAccrual(claims.claimBlockService(), logger).register();
        FabricExplosionProtection.install(dataFolder.resolve("config.yml"), claims);
        claimRepository = claims;
        FabricMessages messages = new FabricMessages(dataFolder, logger);
        FabricSettings settings = new FabricSettings(dataFolder, logger);
        FabricDenialFeedback feedback = new FabricDenialFeedback(claims, messages);
        feedback.register();
        new FabricChatEducator(messages, settings, claims, logger).register();
        FabricClaimModes modes = new FabricClaimModes();
        modes.register();
        FabricLastSeenStore lastSeen = new FabricLastSeenStore(dataFolder, logger);
        lastSeen.register();
        FabricClaimAlerts alerts = new FabricClaimAlerts(claims, feedback);
        alerts.register();
        FabricWorldProtection.install(claims, settings, feedback, alerts);
        FabricBlockPlaceClimbProtection.install(claims, settings);
        FabricFakeBlockVisualization visualization = new FabricFakeBlockVisualization(claims, settings);
        FabricClaimResizer resizer = new FabricClaimResizer(claims, visualization, feedback, settings);
        FabricRestoreNature restoreNature = new FabricRestoreNature(claims, visualization, feedback, modes, settings, logger);
        restoreNature.register();
        FabricClaimToolHooks toolHooks =
                new FabricClaimToolHooks(claims, visualization, feedback, settings, modes, lastSeen, resizer, restoreNature, logger);
        FabricScrollResize scrollResize =
                new FabricScrollResize(claims, visualization, feedback, settings, resizer, toolHooks, logger);
        // A scroll resize session answers clicks before the visualization and the claim tool do.
        scrollResize.register();
        visualization.register();
        toolHooks.register();
        new FabricProtectionHooks(claims, feedback, settings).register();
        FabricPlayerRescue rescue = new FabricPlayerRescue(claims, feedback, settings, logger);
        rescue.register();
        FabricClaimExpiration expiration = new FabricClaimExpiration(claims, settings, lastSeen, restoreNature, logger);
        expiration.register();

        FabricClaimCommands claimCommands = new FabricClaimCommands(
                claims, feedback, settings, modes, toolHooks, visualization, restoreNature, logger);
        claimCommands.register();
        FabricEconomy economy = new FabricEconomy(logger);
        FabricAdminClaimCommands staffCommands = new FabricAdminClaimCommands(claims, feedback, settings, restoreNature, economy, logger);
        staffCommands.register();
        FabricEconomyCommands economyCommands = new FabricEconomyCommands(settings, claims.claimBlockService(), claims, economy, logger);
        new FabricCommandRegistrar(
                claims,
                messages,
                settings,
                feedback,
                new FabricTrustCommands(claims, feedback),
                claimCommands,
                new FabricClaimBlockCommands(claims.claimBlockService(), logger),
                staffCommands,
                scrollResize,
                rescue,
                expiration,
                restoreNature,
                economyCommands,
                dataFolder.resolve("alias.yml"),
                logger
        ).register();

        logger.info("GriefPrevention3D {} adapter loaded with native protection hooks.", platform.loaderName());
        return claims;
    }

    public static ClaimRepository getClaimRepository()
    {
        return claimRepository;
    }
}
