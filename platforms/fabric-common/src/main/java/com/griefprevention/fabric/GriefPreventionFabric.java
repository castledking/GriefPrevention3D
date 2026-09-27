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
        FabricWorldProtection.install(claims, settings, feedback);
        FabricFakeBlockVisualization visualization = new FabricFakeBlockVisualization(claims, settings);
        visualization.register();
        new FabricClaimToolHooks(claims, visualization, feedback, settings, modes, lastSeen, LOGGER).register();
        new FabricProtectionHooks(claims, feedback).register();

        FabricClaimCommands claimCommands = new FabricClaimCommands(claims, feedback, settings, modes, visualization, LOGGER);
        claimCommands.register();
        new FabricCommandRegistrar(
                claims,
                messages,
                settings,
                feedback,
                new FabricTrustCommands(claims, feedback),
                claimCommands,
                new FabricClaimBlockCommands(claims.claimBlockService(), LOGGER),
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
