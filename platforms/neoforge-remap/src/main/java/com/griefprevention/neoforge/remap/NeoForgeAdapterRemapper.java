package com.griefprevention.neoforge.remap;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.format.proguard.ProGuardFileReader;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Rewrites a NeoForge adapter compiled against one Minecraft release to the Mojang names of
 * another.
 *
 * <p>NeoForge runs Minecraft under Mojang's names, and Mojang renames classes and members between
 * releases ({@code ResourceLocation} became {@code Identifier} in 1.21.11) while the code behind
 * them stays the same. Fabric's intermediary names do not change, so they pair a source-release
 * name with its target-release name: source Mojang name, to intermediary, to target Mojang name.
 * Members either release lacks are left alone; the linkage check reports any the adapter calls.
 * Mixin annotations are remapped too, since NeoForge applies mixins by name without a refmap.
 *
 * <pre>
 * NeoForgeAdapterRemapper --input adapter.jar --output adapter-1.21.4.jar
 *     --source-mojang 1.21.11.txt --source-intermediary intermediary-1.21.11-v2.jar
 *     --target-mojang 1.21.4.txt --target-intermediary intermediary-1.21.4-v2.jar
 *     --classpath minecraft-1.21.11-named.jar [--classpath ...]
 * </pre>
 */
public final class NeoForgeAdapterRemapper
{
    private static final String OFFICIAL = "official";
    private static final String INTERMEDIARY = "intermediary";
    private static final String NAMED = "named";

    private NeoForgeAdapterRemapper()
    {
    }

    public static void main(String[] args) throws IOException
    {
        Path input = null;
        Path output = null;
        Path sourceMojang = null;
        Path sourceIntermediary = null;
        Path targetMojang = null;
        Path targetIntermediary = null;
        List<Path> classpath = new ArrayList<>();
        for (int i = 0; i < args.length; i += 2)
        {
            if (i + 1 >= args.length)
            {
                throw new IllegalArgumentException("Missing value for " + args[i]);
            }
            Path value = Paths.get(args[i + 1]);
            switch (args[i])
            {
                case "--input" -> input = value;
                case "--output" -> output = value;
                case "--source-mojang" -> sourceMojang = value;
                case "--source-intermediary" -> sourceIntermediary = value;
                case "--target-mojang" -> targetMojang = value;
                case "--target-intermediary" -> targetIntermediary = value;
                case "--classpath" -> classpath.add(value);
                default -> throw new IllegalArgumentException("Unknown option " + args[i]);
            }
        }
        if (input == null || output == null || sourceMojang == null || sourceIntermediary == null
                || targetMojang == null || targetIntermediary == null)
        {
            throw new IllegalArgumentException("--input, --output, --source-mojang, --source-intermediary, "
                    + "--target-mojang and --target-intermediary are required.");
        }

        MemoryMappingTree source = load(sourceMojang, sourceIntermediary);
        MemoryMappingTree target = load(targetMojang, targetIntermediary);
        int[] renamed = new int[1];
        IMappingProvider mappings = acceptor -> renamed[0] = bridge(source, target, acceptor);

        TinyRemapper remapper = TinyRemapper.newRemapper()
                .withMappings(mappings)
                .ignoreConflicts(true)
                .extension(new MixinExtension())
                .build();
        Files.deleteIfExists(output);
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (OutputConsumerPath consumer = new OutputConsumerPath.Builder(output).build())
        {
            consumer.addNonClassFiles(input, NonClassCopyMode.UNCHANGED, remapper);
            remapper.readInputs(input);
            remapper.readClassPath(classpath.toArray(new Path[0]));
            remapper.apply(consumer);
        }
        finally
        {
            remapper.finish();
        }
        System.out.println("Remapped " + input.getFileName() + " to " + output.getFileName()
                + " through " + renamed[0] + " renamed Minecraft name(s).");
    }

    /** @return one release's names in the official, intermediary and named (Mojang) namespaces */
    private static MemoryMappingTree load(Path mojang, Path intermediary) throws IOException
    {
        MemoryMappingTree tree = new MemoryMappingTree();
        if (intermediary.getFileName().toString().endsWith(".jar"))
        {
            try (ZipFile jar = new ZipFile(intermediary.toFile()))
            {
                ZipEntry entry = jar.getEntry("mappings/mappings.tiny");
                if (entry == null)
                {
                    throw new IOException(intermediary + " has no mappings/mappings.tiny.");
                }
                try (Reader reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8))
                {
                    MappingReader.read(reader, MappingFormat.TINY_2_FILE, tree);
                }
            }
        }
        else
        {
            MappingReader.read(intermediary, MappingFormat.TINY_2_FILE, tree);
        }

        // Mojang's files map named to official; flip them onto the official names the tree shares.
        MemoryMappingTree named = new MemoryMappingTree();
        try (Reader reader = Files.newBufferedReader(mojang, StandardCharsets.UTF_8))
        {
            ProGuardFileReader.read(reader, NAMED, OFFICIAL, named);
        }
        named.accept(new MappingSourceNsSwitch(tree, OFFICIAL));
        return tree;
    }

    /** Feeds every source Mojang name whose intermediary twin the target names differently. */
    private static int bridge(MappingTree source, MappingTree target, IMappingProvider.MappingAcceptor acceptor)
    {
        int sourceNamed = source.getNamespaceId(NAMED);
        int sourceIntermediary = source.getNamespaceId(INTERMEDIARY);
        int targetNamed = target.getNamespaceId(NAMED);
        int targetIntermediary = target.getNamespaceId(INTERMEDIARY);
        int renamed = 0;

        for (MappingTree.ClassMapping sourceClass : source.getClasses())
        {
            String owner = sourceClass.getName(sourceNamed);
            String bridgeName = sourceClass.getName(sourceIntermediary);
            if (owner == null || bridgeName == null)
            {
                continue;
            }
            MappingTree.ClassMapping targetClass = target.getClass(bridgeName, targetIntermediary);
            if (targetClass == null)
            {
                continue;
            }
            String targetOwner = targetClass.getName(targetNamed);
            if (targetOwner != null && !targetOwner.equals(owner))
            {
                acceptor.acceptClass(owner, targetOwner);
                renamed++;
            }

            for (MappingTree.MethodMapping method : sourceClass.getMethods())
            {
                String name = method.getName(sourceNamed);
                String desc = method.getDesc(sourceNamed);
                String bridgeMethod = method.getName(sourceIntermediary);
                // Lambda bodies are private to their class and renumbered between releases.
                if (name == null || desc == null || bridgeMethod == null || name.startsWith("lambda$"))
                {
                    continue;
                }
                MappingTree.MethodMapping targetMethod = targetClass.getMethod(
                        bridgeMethod, method.getDesc(sourceIntermediary), targetIntermediary);
                String targetName = targetMethod == null ? null : targetMethod.getName(targetNamed);
                if (targetName != null && !targetName.equals(name))
                {
                    acceptor.acceptMethod(new IMappingProvider.Member(owner, name, desc), targetName);
                    renamed++;
                }
            }

            for (MappingTree.FieldMapping field : sourceClass.getFields())
            {
                String name = field.getName(sourceNamed);
                String desc = field.getDesc(sourceNamed);
                String bridgeField = field.getName(sourceIntermediary);
                if (name == null || desc == null || bridgeField == null)
                {
                    continue;
                }
                MappingTree.FieldMapping targetField = targetClass.getField(
                        bridgeField, field.getDesc(sourceIntermediary), targetIntermediary);
                String targetName = targetField == null ? null : targetField.getName(targetNamed);
                if (targetName != null && !targetName.equals(name))
                {
                    acceptor.acceptField(new IMappingProvider.Member(owner, name, desc), targetName);
                    renamed++;
                }
            }
        }
        return renamed;
    }
}
