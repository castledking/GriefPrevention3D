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
        FabricExplosionProtection.install(dataFolder.resolve("config.yml"), claims);
        claimRepository = claims;
        FabricFakeBlockVisualization visualization = new FabricFakeBlockVisualization();
        visualization.register();
        new FabricClaimToolHooks(claims, visualization).register();
        new FabricProtectionHooks(claims).register();
        FabricCommands.register(claims);
        LOGGER.info("GriefPrevention3D Fabric adapter loaded with native protection hooks.");
    }

    public static ClaimRepository getClaimRepository()
    {
        return claimRepository;
    }
}
