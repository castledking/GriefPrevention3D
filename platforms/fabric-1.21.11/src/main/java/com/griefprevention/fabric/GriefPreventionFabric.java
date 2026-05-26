package com.griefprevention.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class GriefPreventionFabric implements ModInitializer
{
    public static final String MOD_ID = "griefprevention3d";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize()
    {
        Path dataFolder = FabricLoader.getInstance()
                .getConfigDir()
                .resolve("GriefPreventionData");
        FabricDataFolder.ensureDefaults(dataFolder, LOGGER);
        FabricClaimRepository claims = new FabricClaimRepository(dataFolder, LOGGER);
        FabricFakeBlockVisualization visualization = new FabricFakeBlockVisualization();
        visualization.register();
        new FabricClaimToolHooks(claims, visualization).register();
        new FabricProtectionHooks(claims).register();
        FabricCommands.register(claims);
        LOGGER.info("GriefPrevention3D Fabric adapter loaded with native protection hooks.");
    }
}
