package com.griefprevention.fabric.bootstrap;

/**
 * One Minecraft-version-specific adapter packaged in the jar, as described by the adapter index.
 */
final class FabricAdapter
{
    private final String tag;
    private final String minecraft;
    private final String entrypoint;
    private final String mixinPackage;

    FabricAdapter(String tag, String minecraft, String entrypoint, String mixinPackage)
    {
        this.tag = tag;
        this.minecraft = minecraft;
        this.entrypoint = entrypoint;
        this.mixinPackage = stripTrailingDots(mixinPackage);
    }

    /**
     * @return the short name the build gave this adapter, such as {@code mc26_1}
     */
    String tag()
    {
        return this.tag;
    }

    /**
     * @return the Fabric version predicate of the Minecraft releases this adapter was verified on
     */
    String minecraft()
    {
        return this.minecraft;
    }

    /**
     * @return the {@link FabricPlatformAdapter} implementation to instantiate
     */
    String entrypoint()
    {
        return this.entrypoint;
    }

    /**
     * @param mixinPackage a mixin config's package, as Mixin reports it (possibly dot-terminated)
     * @return whether that config belongs to this adapter
     */
    boolean ownsMixinPackage(String mixinPackage)
    {
        return this.mixinPackage.equals(stripTrailingDots(mixinPackage));
    }

    private static String stripTrailingDots(String packageName)
    {
        String result = packageName.trim();
        while (result.endsWith("."))
        {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    @Override
    public String toString()
    {
        return this.tag + " (Minecraft " + this.minecraft + ")";
    }
}
