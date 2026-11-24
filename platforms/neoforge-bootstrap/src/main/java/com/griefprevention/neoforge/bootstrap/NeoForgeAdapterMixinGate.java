package com.griefprevention.neoforge.bootstrap;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Enables only the NeoForge mixin config belonging to the adapter selected for the running
 * Minecraft version, and skips {@code @Pseudo} targets absent on that release.
 */
public final class NeoForgeAdapterMixinGate implements IMixinConfigPlugin
{
    private static final Set<String> APPLIED_TARGETS = Collections.synchronizedSet(new LinkedHashSet<>());

    private boolean enabled;

    @Override
    public void onLoad(String mixinPackage)
    {
        NeoForgeAdapter adapter = NeoForgeAdapters.current().selected();
        this.enabled = adapter != null && adapter.ownsMixinPackage(mixinPackage);
    }

    @Override
    public String getRefMapperConfig()
    {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName)
    {
        return this.enabled && classExists(targetClassName);
    }

    private static boolean classExists(String className)
    {
        String resource = className.replace('.', '/') + ".class";
        try (InputStream stream = MixinService.getService().getResourceAsStream(resource))
        {
            if (stream != null)
            {
                return true;
            }
        }
        catch (IOException | RuntimeException ignored) {}

        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader != null && loader.getResource(resource) != null)
        {
            return true;
        }
        return NeoForgeAdapterMixinGate.class.getClassLoader().getResource(resource) != null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets)
    {
        if (this.enabled)
        {
            APPLIED_TARGETS.addAll(myTargets);
        }
    }

    @Override
    public List<String> getMixins()
    {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
    {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
    {
    }

    static List<String> appliedTargets()
    {
        synchronized (APPLIED_TARGETS)
        {
            return new ArrayList<>(APPLIED_TARGETS);
        }
    }
}
