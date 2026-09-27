package com.griefprevention.fabric.linkage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricLinkageCheckTest
{
    private static final String METAFACTORY_DESC = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
            + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;"
            + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";

    @TempDir
    Path directory;

    @Test
    void referencesThatStillResolveAreClean() throws IOException
    {
        MethodNode method = method("works");
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, "lib/Api"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "lib/Api", "create", "()Llib/Api;", false));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "lib/Api", "present", "()V", false));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "lib/Api", "CONSTANT", "Ljava/lang/Object;"));
        // Inherited from a superclass, as javac emits for calls through a subtype.
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "lib/Api", "inherited", "()V", false));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "lib/Api", "hashCode", "()I", false));
        method.instructions.add(lambda("run", "(Llib/Api;)V"));

        assertEquals(Set.of(), check(user(method), targetMixin(List.of("level"), List.of("explode"), List.of("explode"))));
    }

    @Test
    void reportsEveryKindOfBrokenReference() throws IOException
    {
        MethodNode method = method("broken");
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "lib/Api", "removed", "()V", false));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "lib/Api", "GONE", "Ljava/lang/Object;"));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "lib/Api", "CONSTANT", "Ljava/lang/Object;"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "lib/Api", "hidden", "()V", false));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "lib/Api", "present", "()V", true));
        method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, "lib/Missing"));
        method.instructions.add(lambda("old", "(Llib/Api;)V"));

        Set<String> problems = check(user(method), targetMixin(List.of("level", "gone"), List.of("explode", "vanished"), List.of("explode", "vanishedToo")));

        assertEquals(Set.of(
                "mod/User.broken()V: missing method lib/Api.removed()V",
                "mod/User.broken()V: missing field lib/Api.GONE:Ljava/lang/Object;",
                "mod/User.broken()V: field lib/Api.CONSTANT:Ljava/lang/Object; became static",
                "mod/User.broken()V: lib/Api.hidden()V is private",
                "mod/User.broken()V: lib/Api is no longer an interface (calling present()V)",
                "mod/User.broken()V: missing class lib/Missing",
                "mod/User.broken()V: lambda implements lib/Callback.old(Llib/Api;)V, which no longer exists",
                "mod/mixin/TargetMixin: @Shadow field gone:Ljava/lang/Object; is missing from lib/Target",
                "mod/mixin/TargetMixin.handler: injector targets lib/Target.vanished, which does not exist",
                "mod/mixin/TargetMixin.call$vanishedToo: @Invoker targets missing method lib/Target.vanishedToo()V"
        ), problems);
    }

    @Test
    void pseudoMixinsNeedOneTargetAndGroupsNeedOneHit() throws IOException
    {
        ClassNode passes = pseudoMixin("mod/mixin/RenamedPass", List.of("renamedAway", "explode"));
        ClassNode fails = pseudoMixin("mod/mixin/RenamedFail", List.of("renamedAway", "goneToo"));

        FabricLinkageCheck.ClassPool pool = new FabricLinkageCheck.ClassPool();
        Path checked = jar("pseudo.jar", passes, fails);
        pool.addJar(checked);
        pool.add(jar("library.jar", base(), api(), callback(), target()));
        pool.namesIn(checked);
        FabricLinkageCheck check = new FabricLinkageCheck(pool, List.of());
        check.checkClass(passes.name);
        check.checkClass(fails.name);

        assertEquals(Set.of(
                "mod/mixin/RenamedFail: injector group trample finds 0 of its 1 required target methods in lib/Target"
        ), check.problems());
    }

    private static ClassNode pseudoMixin(String name, List<String> alternatives)
    {
        ClassNode node = type(name, "java/lang/Object", Opcodes.ACC_ABSTRACT);
        AnnotationNode mixin = new AnnotationNode("Lorg/spongepowered/asm/mixin/Mixin;");
        mixin.values = List.of("targets", List.of("lib.Target", "lib.RenamedTarget"));
        node.invisibleAnnotations = new java.util.ArrayList<>(List.of(
                mixin, new AnnotationNode("Lorg/spongepowered/asm/mixin/Pseudo;")));
        for (String alternative : alternatives)
        {
            MethodNode handler = new MethodNode(Opcodes.ACC_PRIVATE, "handle$" + alternative, "()V", null, null);
            AnnotationNode inject = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Inject;");
            inject.values = List.of("method", List.of(alternative), "require", 0);
            AnnotationNode group = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Group;");
            group.values = List.of("name", "trample", "min", 1);
            handler.invisibleAnnotations = new java.util.ArrayList<>(List.of(inject, group));
            handler.instructions.add(new InsnNode(Opcodes.RETURN));
            node.methods.add(handler);
        }
        return node;
    }

    @Test
    void optionalPackagesMayBeAbsent() throws IOException
    {
        MethodNode method = method("optional");
        method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, "net/luckperms/api/LuckPerms"));

        FabricLinkageCheck.ClassPool pool = pool(user(method), null);
        FabricLinkageCheck check = new FabricLinkageCheck(pool, List.of("net/luckperms/"));
        check.checkClass("mod/User");

        assertTrue(check.problems().isEmpty(), check.problems().toString());
    }

    private Set<String> check(ClassNode user, ClassNode mixin) throws IOException
    {
        FabricLinkageCheck.ClassPool pool = pool(user, mixin);
        FabricLinkageCheck check = new FabricLinkageCheck(pool, List.of());
        check.checkClass(user.name);
        check.checkClass(mixin.name);
        return check.problems();
    }

    private FabricLinkageCheck.ClassPool pool(ClassNode user, ClassNode mixin) throws IOException
    {
        Path library = jar("library.jar", base(), api(), callback(), target());
        Path checked = mixin == null ? jar("checked.jar", user) : jar("checked.jar", user, mixin);
        FabricLinkageCheck.ClassPool pool = new FabricLinkageCheck.ClassPool();
        pool.addJar(checked);
        pool.add(library);
        pool.namesIn(checked);
        return pool;
    }

    private static ClassNode base()
    {
        ClassNode node = type("lib/Base", "java/lang/Object", Opcodes.ACC_PUBLIC);
        node.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "inherited", "()V", null, null));
        return node;
    }

    private static ClassNode api()
    {
        ClassNode node = type("lib/Api", "lib/Base", Opcodes.ACC_PUBLIC);
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "CONSTANT", "Ljava/lang/Object;", null, null));
        node.methods.add(new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create", "()Llib/Api;", null, null));
        node.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "present", "()V", null, null));
        node.methods.add(new MethodNode(Opcodes.ACC_PRIVATE, "hidden", "()V", null, null));
        return node;
    }

    private static ClassNode callback()
    {
        ClassNode node = type("lib/Callback", "java/lang/Object",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT);
        node.methods.add(new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "run", "(Llib/Api;)V", null, null));
        return node;
    }

    private static ClassNode target()
    {
        ClassNode node = type("lib/Target", "java/lang/Object", Opcodes.ACC_PUBLIC);
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "level", "Ljava/lang/Object;", null, null));
        node.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "explode", "()V", null, null));
        return node;
    }

    private static ClassNode user(MethodNode method)
    {
        ClassNode node = type("mod/User", "java/lang/Object", Opcodes.ACC_PUBLIC);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(method);
        // The body the lambdas in these tests point at.
        MethodNode lambdaBody = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "lambda", "(Llib/Api;)V", null, null);
        lambdaBody.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(lambdaBody);
        return node;
    }

    private static ClassNode targetMixin(List<String> shadows, List<String> injectorTargets, List<String> invokerTargets)
    {
        ClassNode node = type("mod/mixin/TargetMixin", "java/lang/Object", Opcodes.ACC_ABSTRACT);
        AnnotationNode mixin = new AnnotationNode("Lorg/spongepowered/asm/mixin/Mixin;");
        mixin.values = List.of("value", List.of(Type.getObjectType("lib/Target")));
        node.invisibleAnnotations = new java.util.ArrayList<>(List.of(mixin));
        for (String shadow : shadows)
        {
            FieldNode field = new FieldNode(Opcodes.ACC_PRIVATE, shadow, "Ljava/lang/Object;", null, null);
            field.invisibleAnnotations = new java.util.ArrayList<>(List.of(new AnnotationNode("Lorg/spongepowered/asm/mixin/Shadow;")));
            node.fields.add(field);
        }
        for (String invoked : invokerTargets)
        {
            MethodNode invoker = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "call$" + invoked, "()V", null, null);
            AnnotationNode annotation = new AnnotationNode("Lorg/spongepowered/asm/mixin/gen/Invoker;");
            annotation.values = List.of("value", invoked);
            invoker.invisibleAnnotations = new java.util.ArrayList<>(List.of(annotation));
            node.methods.add(invoker);
        }
        MethodNode handler = new MethodNode(Opcodes.ACC_PRIVATE, "handler", "()V", null, null);
        AnnotationNode inject = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Inject;");
        inject.values = List.of("method", injectorTargets);
        handler.invisibleAnnotations = new java.util.ArrayList<>(List.of(inject));
        // Reads a shadowed field through the mixin's own name, as compiled mixins do.
        handler.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "level", "Ljava/lang/Object;"));
        handler.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(handler);
        return node;
    }

    private static InvokeDynamicInsnNode lambda(String samName, String samDesc)
    {
        Handle metafactory = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory",
                "metafactory", METAFACTORY_DESC, false);
        Handle implementation = new Handle(Opcodes.H_INVOKESTATIC, "mod/User", "lambda", "(Llib/Api;)V", false);
        return new InvokeDynamicInsnNode(samName, "()Llib/Callback;", metafactory,
                Type.getMethodType(samDesc), implementation, Type.getMethodType(samDesc));
    }

    private static MethodNode method(String name)
    {
        return new MethodNode(Opcodes.ACC_PUBLIC, name, "()V", null, null);
    }

    private static ClassNode type(String name, String superName, int access)
    {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V21;
        node.access = access;
        node.name = name;
        node.superName = superName;
        return node;
    }

    private Path jar(String name, ClassNode... classes) throws IOException
    {
        Path jar = this.directory.resolve(name);
        try (OutputStream file = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(file))
        {
            for (ClassNode node : classes)
            {
                ClassWriter writer = new ClassWriter(0);
                node.accept(writer);
                zip.putNextEntry(new ZipEntry(node.name + ".class"));
                zip.write(writer.toByteArray());
                zip.closeEntry();
            }
        }
        return jar;
    }
}
