package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimRepository;
import com.griefprevention.commands.CommandAliasConfiguration;
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
    private static FabricCommandRegistrar commandRegistrar;

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
        FabricDenialFeedback feedback = new FabricDenialFeedback(claims, messages);
        feedback.register();
        FabricFakeBlockVisualization visualization = new FabricFakeBlockVisualization();
        visualization.register();
        new FabricClaimToolHooks(claims, visualization, feedback).register();
        new FabricProtectionHooks(claims, feedback).register();

        // Load alias.yml configuration
        Path aliasFile = dataFolder.resolve("alias.yml");
        CommandAliasConfiguration aliasConfig = CommandAliasConfiguration.load(aliasFile, new CommandAliasConfiguration.Logger()
        {
            @Override
            public void info(String message) { LOGGER.info(message); }

            @Override
            public void warning(String message) { LOGGER.warn(message); }

            @Override
            public void severe(String message) { LOGGER.error(message); }
        });

        // Register commands from alias config (standalone + subcommands)
        commandRegistrar = new FabricCommandRegistrar(claims, messages, feedback);
        commandRegistrar.register(aliasConfig);

        // Also register hardcoded commands (for commands not in alias.yml)
        FabricCommands.register(claims, messages, feedback);

        LOGGER.info("GriefPrevention3D Fabric adapter loaded with native protection hooks.");
    }

    public static ClaimRepository getClaimRepository()
    {
        return claimRepository;
    }

    public static FabricCommandRegistrar getCommandRegistrar()
    {
        return commandRegistrar;
    }
}
