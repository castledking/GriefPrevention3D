package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimRepository;
import com.griefprevention.fabric.bootstrap.FabricPlatformAdapter;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class GriefPreventionFabric implements FabricPlatformAdapter
{
    public static final String MOD_ID = "griefprevention3d";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ClaimRepository claimRepository;

    @Override
    public void onInitialize()
    {
        // Resolve the calls that differ between the releases this adapter serves before anything uses them.
        FabricVersionCompat.verify();
        FabricLoader loader = FabricLoader.getInstance();
        Path dataFolder = FabricDataFolder.resolveSharedDataFolder(
                loader.getGameDir(),
                loader.getConfigDir(),
                LOGGER
        );
        FabricDataFolder.ensureDefaults(dataFolder, LOGGER);
        FabricClaimRepository claims = new FabricClaimRepository(dataFolder, LOGGER);
        new FabricClaimBlockAccrual(claims.claimBlockService(), LOGGER).register();
        FabricExplosionProtection.install(dataFolder.resolve("config.yml"), claims);
        claimRepository = claims;
        FabricMessages messages = new FabricMessages(dataFolder, LOGGER);
        FabricSettings settings = new FabricSettings(dataFolder, LOGGER);
        FabricDenialFeedback feedback = new FabricDenialFeedback(claims, messages);
        feedback.register();
        FabricClaimModes modes = new FabricClaimModes();
        modes.register();
        FabricLastSeenStore lastSeen = new FabricLastSeenStore(dataFolder, LOGGER);
        lastSeen.register();
        FabricClaimAlerts alerts = new FabricClaimAlerts(claims, feedback);
        alerts.register();
        FabricWorldProtection.install(claims, settings, feedback, alerts);
        FabricFakeBlockVisualization visualization = new FabricFakeBlockVisualization(claims, settings);
        FabricClaimResizer resizer = new FabricClaimResizer(claims, visualization, feedback, settings);
        FabricClaimToolHooks toolHooks =
                new FabricClaimToolHooks(claims, visualization, feedback, settings, modes, lastSeen, resizer, LOGGER);
        FabricScrollResize scrollResize =
                new FabricScrollResize(claims, visualization, feedback, settings, resizer, toolHooks, LOGGER);
        // A scroll resize session answers clicks before the visualization and the claim tool do.
        scrollResize.register();
        visualization.register();
        toolHooks.register();
        new FabricProtectionHooks(claims, feedback, settings).register();
        FabricPlayerRescue rescue = new FabricPlayerRescue(claims, feedback, settings, LOGGER);
        rescue.register();

        FabricClaimCommands claimCommands = new FabricClaimCommands(claims, feedback, settings, modes, visualization, LOGGER);
        claimCommands.register();
        FabricAdminClaimCommands staffCommands = new FabricAdminClaimCommands(claims, feedback, settings, LOGGER);
        staffCommands.register();
        new FabricCommandRegistrar(
                claims,
                messages,
                settings,
                feedback,
                new FabricTrustCommands(claims, feedback),
                claimCommands,
                new FabricClaimBlockCommands(claims.claimBlockService(), LOGGER),
                staffCommands,
                scrollResize,
                rescue,
                dataFolder.resolve("alias.yml"),
                LOGGER
        ).register();

        LOGGER.info("GriefPrevention3D Fabric adapter loaded with native protection hooks.");
    }

    public static ClaimRepository getClaimRepository()
    {
        return claimRepository;
    }
}
