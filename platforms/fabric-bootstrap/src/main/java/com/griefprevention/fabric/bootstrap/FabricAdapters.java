package com.griefprevention.fabric.bootstrap;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

import java.util.Collections;
import java.util.List;

/**
 * Resolves, once per game launch, which packaged adapter serves the running Minecraft version.
 *
 * <p>Both the mixin gate and the mod initializer ask this class, so a jar can never apply one
 * adapter's mixins and start another adapter's code.
 */
final class FabricAdapters
{
    private static volatile Selection selection;

    private FabricAdapters()
    {
    }

    static Selection current()
    {
        Selection result = selection;
        if (result == null)
        {
            synchronized (FabricAdapters.class)
            {
                result = selection;
                if (result == null)
                {
                    result = resolve();
                    selection = result;
                }
            }
        }
        return result;
    }

    private static Selection resolve()
    {
        String minecraftVersion = "unknown";
        try
        {
            ModContainer minecraft = FabricLoader.getInstance()
                    .getModContainer("minecraft")
                    .orElseThrow(() -> new IllegalStateException(
                            "Fabric Loader did not expose the Minecraft version."));
            Version version = minecraft.getMetadata().getVersion();
            minecraftVersion = version.getFriendlyString();

            List<FabricAdapter> adapters = FabricAdapterSelector.readIndex(FabricAdapters.class.getClassLoader());
            FabricAdapter selected = FabricAdapterSelector.select(
                    adapters,
                    minecraftVersion,
                    (ignored, predicate) -> matches(version, predicate)
            );
            return new Selection(minecraftVersion, adapters, selected, null);
        }
        catch (Exception | LinkageError exception)
        {
            return new Selection(minecraftVersion, Collections.<FabricAdapter>emptyList(), null, exception);
        }
    }

    private static boolean matches(Version version, String predicate)
    {
        try
        {
            return VersionPredicate.parse(predicate).test(version);
        }
        catch (VersionParsingException exception)
        {
            throw new IllegalStateException(
                    "The Fabric adapter index has an unreadable Minecraft range: " + predicate,
                    exception
            );
        }
    }

    static final class Selection
    {
        private final String minecraftVersion;
        private final List<FabricAdapter> adapters;
        private final FabricAdapter selected;
        private final Throwable failure;

        private Selection(
                String minecraftVersion,
                List<FabricAdapter> adapters,
                FabricAdapter selected,
                Throwable failure)
        {
            this.minecraftVersion = minecraftVersion;
            this.adapters = adapters;
            this.selected = selected;
            this.failure = failure;
        }

        String minecraftVersion()
        {
            return this.minecraftVersion;
        }

        /**
         * @return the adapter for the running game, or null when none can serve it
         */
        FabricAdapter selected()
        {
            return this.selected;
        }

        /**
         * @return an exception explaining why no adapter can start
         */
        IllegalStateException unavailable()
        {
            if (this.failure != null)
            {
                return new IllegalStateException(
                        "GriefPrevention3D could not choose a Fabric adapter for Minecraft "
                                + this.minecraftVersion + ".",
                        this.failure
                );
            }
            return new IllegalStateException(
                    "This GriefPrevention3D jar does not support Minecraft " + this.minecraftVersion
                            + " on Fabric. It supports: " + FabricAdapterSelector.describe(this.adapters) + "."
            );
        }
    }
}
