package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimRepository;
import com.griefprevention.neoforge.bootstrap.NeoForgePlatformAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GriefPreventionNeoForge implements NeoForgePlatformAdapter
{
    public static final String MOD_ID = ModPlatform.MOD_ID;
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize()
    {
        GriefPreventionModRuntime.initialize(new NeoForgeModPlatform(), LOGGER);
    }

    public static ClaimRepository getClaimRepository()
    {
        return GriefPreventionModRuntime.getClaimRepository();
    }
}
