package com.griefprevention.neoforge.bootstrap;

/**
 * One versioned NeoForge adapter bundled in the jar.
 */
final class NeoForgeAdapter
{
    private final String tag;
    private final String minecraft;
    private final String entrypoint;
    private final String mixins;

    NeoForgeAdapter(String tag, String minecraft, String entrypoint, String mixins)
    {
        this.tag = tag;
        this.minecraft = minecraft;
        this.entrypoint = entrypoint;
        this.mixins = mixins;
    }

    String tag()
    {
        return this.tag;
    }

    String minecraft()
    {
        return this.minecraft;
    }

    String entrypoint()
    {
        return this.entrypoint;
    }

    String mixins()
    {
        return this.mixins;
    }

    boolean ownsMixinPackage(String mixinPackage)
    {
        return this.mixins.equals(mixinPackage);
    }

    @Override
    public String toString()
    {
        return this.tag + " (" + this.minecraft + ")";
    }
}
