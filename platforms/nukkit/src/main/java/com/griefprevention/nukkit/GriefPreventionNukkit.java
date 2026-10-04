package com.griefprevention.nukkit;

import cn.nukkit.plugin.PluginBase;

/**
 * GriefPrevention3D's entrypoint on Cloudburst Nukkit, named by the universal jar's nukkit.yml.
 *
 * <p>This is the adapter's scaffold: it loads and reports itself, but reads no claims and protects
 * nothing yet. {@code platforms/nukkit/ROADMAP.md} lists what comes next.
 */
public final class GriefPreventionNukkit extends PluginBase
{
    @Override
    public void onEnable()
    {
        getLogger().warning("This Nukkit build of GriefPrevention3D is a development scaffold: it loads no claims and protects nothing.");
        getLogger().info("GriefPrevention3D Nukkit adapter loaded on Nukkit API " + getServer().getApiVersion() + ".");
    }
}
