package com.griefprevention.fabric.linkage;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Proves the classes of one Fabric adapter link against one Minecraft release.
 *
 * <p>The JVM links lazily. A field or method a newer release removed only fails, with
 * {@code NoSuchFieldError} or {@code NoSuchMethodError}, when the code path first runs, which can be
 * long after a server boots cleanly. This resolves every class, field and method reference in the
 * checked classes the way the JVM would, against the release's server jar, its libraries, Fabric
 * Loader and Fabric API. Mixin classes additionally have their targets, {@code @Shadow} members,
 * injector method selectors and {@code @At} targets checked, since Mixin only resolves those when
 * the target class first loads.
 *
 * <pre>
 * FabricLinkageCheck --label 26.2 --jar universal.jar --package com/griefprevention/fabric/mc26_1/
 *     --classpath server.jar --classpath libraries/ --classpath fabric-api.jar
 *     [--exclude prefix/] [--optional prefix/]
 * </pre>
 */
public final class FabricLinkageCheck
{
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final String PSEUDO = "Lorg/spongepowered/asm/mixin/Pseudo;";
    private static final String GROUP = "Lorg/spongepowered/asm/mixin/injection/Group;";
    private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
    private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    private static final String LAMBDA_METAFACTORY = "java/lang/invoke/LambdaMetafactory";

    private final ClassPool pool;
    private final List<String> optionalPrefixes;
    private final Set<String> problems = new TreeSet<>();
    private final Set<String> mixinClasses = new LinkedHashSet<>();

    FabricLinkageCheck(ClassPool pool, List<String> optionalPrefixes)
    {
        this.pool = pool;
        this.optionalPrefixes = optionalPrefixes;
    }

    public static void main(String[] args) throws IOException
    {
        String label = "linkage";
        Path jar = null;
        List<String> packages = new ArrayList<>();
        List<String> excludes = new ArrayList<>();
        List<String> optional = new ArrayList<>();
        List<Path> classpath = new ArrayList<>();
        for (int i = 0; i < args.length; i++)
        {
            String value = i + 1 < args.length ? args[i + 1] : null;
            switch (args[i])
            {
                case "--label" -> label = requireValue(args[i], value);
                case "--jar" -> jar = Paths.get(requireValue(args[i], value));
                case "--package" -> packages.add(requireValue(args[i], value));
                case "--exclude" -> excludes.add(requireValue(args[i], value));
                case "--optional" -> optional.add(requireValue(args[i], value));
                case "--classpath" -> classpath.add(Paths.get(requireValue(args[i], value)));
                default -> throw new IllegalArgumentException("Unknown argument " + args[i]);
            }
            i++;
        }
        if (jar == null || packages.isEmpty())
        {
            throw new IllegalArgumentException("--jar and at least one --package are required.");
        }

        ClassPool pool = new ClassPool();
        pool.addJar(jar);
        for (Path entry : classpath)
        {
            pool.add(entry);
        }

        List<String> checked = new ArrayList<>();
        for (String name : pool.namesIn(jar))
        {
            if (startsWithAny(name, packages) && !startsWithAny(name, excludes))
            {
                checked.add(name);
            }
        }
        if (checked.isEmpty())
        {
            throw new IllegalStateException("No classes under " + packages + " in " + jar + ".");
        }

        FabricLinkageCheck check = new FabricLinkageCheck(pool, optional);
        for (String name : checked)
        {
            check.checkClass(name);
        }

        if (check.problems.isEmpty())
        {
            System.out.println("[" + label + "] " + checked.size() + " classes link cleanly.");
            return;
        }
        System.err.println("[" + label + "] " + check.problems.size() + " linkage problem(s) in "
                + checked.size() + " classes:");
        for (String problem : check.problems)
        {
            System.err.println("  " + problem);
        }
        System.exit(1);
    }

    Set<String> problems()
    {
        return Collections.unmodifiableSet(this.problems);
    }

    void checkClass(String name)
    {
        ClassNode node = this.pool.node(name);
        String mixinSelf = null;
        List<String> mixinTargets = mixinTargets(node);
        if (mixinTargets != null)
        {
            mixinSelf = node.name;
            this.mixinClasses.add(node.name);
            checkMixin(node, mixinTargets);
        }

        requireClass(node.name, node.superName);
        for (String iface : node.interfaces)
        {
            requireClass(node.name, iface);
        }
        for (FieldNode field : node.fields)
        {
            requireDescriptor(node.name, field.desc);
        }
        for (MethodNode method : node.methods)
        {
            String where = node.name + "." + method.name + method.desc;
            requireDescriptor(where, method.desc);
            for (String exception : method.exceptions)
            {
                requireClass(where, exception);
            }
            for (TryCatchBlockNode tryCatch : method.tryCatchBlocks)
            {
                if (tryCatch.type != null)
                {
                    requireClass(where, tryCatch.type);
                }
            }
            for (AbstractInsnNode insn : method.instructions)
            {
                checkInstruction(node.name, where, insn, mixinSelf);
            }
        }
    }

    private void checkInstruction(String accessor, String where, AbstractInsnNode insn, String mixinSelf)
    {
        if (insn instanceof TypeInsnNode type)
        {
            requireInternalOrArray(where, type.desc);
        }
        else if (insn instanceof MultiANewArrayInsnNode array)
        {
            requireDescriptor(where, array.desc);
        }
        else if (insn instanceof FieldInsnNode field)
        {
            if (field.owner.equals(mixinSelf))
            {
                return;
            }
            boolean isStatic = field.getOpcode() == Opcodes.GETSTATIC || field.getOpcode() == Opcodes.PUTSTATIC;
            checkField(accessor, where, field.owner, field.name, field.desc, isStatic);
        }
        else if (insn instanceof MethodInsnNode method)
        {
            if (method.owner.equals(mixinSelf))
            {
                return;
            }
            checkMethod(accessor, where, method.owner, method.name, method.desc, method.getOpcode(), method.itf);
        }
        else if (insn instanceof InvokeDynamicInsnNode indy)
        {
            checkInvokeDynamic(accessor, where, indy);
        }
        else if (insn instanceof LdcInsnNode ldc)
        {
            if (ldc.cst instanceof Type type)
            {
                requireType(where, type);
            }
            else if (ldc.cst instanceof Handle handle)
            {
                checkHandle(accessor, where, handle);
            }
        }
    }

    private void checkInvokeDynamic(String accessor, String where, InvokeDynamicInsnNode indy)
    {
        requireDescriptor(where, indy.desc);
        checkHandle(accessor, where, indy.bsm);
        for (Object argument : indy.bsmArgs)
        {
            if (argument instanceof Handle handle)
            {
                checkHandle(accessor, where, handle);
            }
            else if (argument instanceof Type type)
            {
                requireType(where, type);
            }
        }

        // A lambda or method reference implements a functional interface by name and erased
        // descriptor. If a new release changed that abstract method, the generated class would no
        // longer implement it and the first call would throw AbstractMethodError.
        if (LAMBDA_METAFACTORY.equals(indy.bsm.getOwner()) && indy.bsmArgs.length > 0
                && indy.bsmArgs[0] instanceof Type samType)
        {
            String functionalInterface = Type.getReturnType(indy.desc).getInternalName();
            if (!isJdk(functionalInterface) && this.pool.info(functionalInterface) != null
                    && findMethod(functionalInterface, indy.name, samType.getDescriptor(), true) == null)
            {
                problem(where, "lambda implements " + functionalInterface + "." + indy.name
                        + samType.getDescriptor() + ", which no longer exists");
            }
        }
    }

    private void checkHandle(String accessor, String where, Handle handle)
    {
        int tag = handle.getTag();
        boolean field = tag <= Opcodes.H_PUTSTATIC;
        if (field)
        {
            boolean isStatic = tag == Opcodes.H_GETSTATIC || tag == Opcodes.H_PUTSTATIC;
            checkField(accessor, where, handle.getOwner(), handle.getName(), handle.getDesc(), isStatic);
            return;
        }

        int opcode = switch (tag)
        {
            case Opcodes.H_INVOKESTATIC -> Opcodes.INVOKESTATIC;
            case Opcodes.H_INVOKEINTERFACE -> Opcodes.INVOKEINTERFACE;
            case Opcodes.H_INVOKESPECIAL, Opcodes.H_NEWINVOKESPECIAL -> Opcodes.INVOKESPECIAL;
            default -> Opcodes.INVOKEVIRTUAL;
        };
        checkMethod(accessor, where, handle.getOwner(), handle.getName(), handle.getDesc(), opcode, handle.isInterface());
    }

    private void checkField(String accessor, String where, String owner, String name, String desc, boolean isStatic)
    {
        requireDescriptor(where, desc);
        if (!requireInternalOrArray(where, owner) || isJdk(owner) || owner.startsWith("["))
        {
            return;
        }

        Member member = findField(owner, name, desc);
        String reference = owner + "." + name + ":" + desc;
        if (member == null)
        {
            problem(where, "missing field " + reference);
            return;
        }
        if (member.isStatic() != isStatic)
        {
            problem(where, "field " + reference + (isStatic ? " is no longer static" : " became static"));
        }
        checkAccess(accessor, where, member, reference);
    }

    private void checkMethod(
            String accessor,
            String where,
            String owner,
            String name,
            String desc,
            int opcode,
            boolean itf)
    {
        requireDescriptor(where, desc);
        if (!requireInternalOrArray(where, owner) || isJdk(owner) || owner.startsWith("["))
        {
            return;
        }

        ClassInfo ownerInfo = this.pool.info(owner);
        if (ownerInfo == null)
        {
            return;
        }
        String reference = owner + "." + name + desc;
        if (opcode != Opcodes.INVOKESPECIAL || !"<init>".equals(name))
        {
            if (ownerInfo.isInterface() != itf)
            {
                problem(where, owner + (ownerInfo.isInterface() ? " became an interface" : " is no longer an interface")
                        + " (calling " + name + desc + ")");
                return;
            }
        }

        Member member = findMethod(owner, name, desc, ownerInfo.isInterface());
        if (member == null)
        {
            problem(where, "missing method " + reference);
            return;
        }
        boolean isStatic = opcode == Opcodes.INVOKESTATIC;
        if (member.isStatic() != isStatic)
        {
            problem(where, "method " + reference + (isStatic ? " is no longer static" : " became static"));
        }
        checkAccess(accessor, where, member, reference);
    }

    private void checkAccess(String accessor, String where, Member member, String reference)
    {
        // Mixin code runs inside its target class, so the target's access rules apply, not ours.
        if (this.pool.isChecked(member.owner()) || member.owner().equals(accessor)
                || this.mixinClasses.contains(accessor))
        {
            return;
        }
        boolean samePackage = packageOf(member.owner()).equals(packageOf(accessor));
        if ((member.access() & Opcodes.ACC_PRIVATE) != 0)
        {
            problem(where, reference + " is private");
        }
        else if ((member.access() & Opcodes.ACC_PROTECTED) != 0)
        {
            if (!samePackage && !isSubclass(accessor, member.owner()))
            {
                problem(where, reference + " is protected");
            }
        }
        else if ((member.access() & Opcodes.ACC_PUBLIC) == 0 && !samePackage)
        {
            problem(where, reference + " is package-private");
        }
    }

    private boolean isSubclass(String name, String ancestor)
    {
        for (ClassInfo current = this.pool.info(name); current != null;
                current = current.superName() == null ? null : this.pool.info(current.superName()))
        {
            if (current.name().equals(ancestor))
            {
                return true;
            }
        }
        return false;
    }

    private void checkMixin(ClassNode mixin, List<String> targets)
    {
        String where = mixin.name;
        // A @Pseudo mixin names targets that may not all exist; one of them must.
        boolean pseudo = hasAnnotation(mixin.invisibleAnnotations, PSEUDO) || hasAnnotation(mixin.visibleAnnotations, PSEUDO);
        List<String> resolved = new ArrayList<>();
        for (String target : targets)
        {
            if (pseudo ? this.pool.info(target) != null : requireClass(where, target))
            {
                resolved.add(target);
            }
        }
        if (resolved.isEmpty())
        {
            if (pseudo)
            {
                problem(where, "none of the @Pseudo targets " + targets + " exist");
            }
            return;
        }
        Map<String, Integer> groupMinimums = new HashMap<>();
        Map<String, Map<String, Integer>> groupHits = new HashMap<>();

        for (FieldNode field : mixin.fields)
        {
            if (hasAnnotation(field.invisibleAnnotations, SHADOW) || hasAnnotation(field.visibleAnnotations, SHADOW))
            {
                for (String target : resolved)
                {
                    if (findField(target, field.name, field.desc) == null)
                    {
                        problem(where, "@Shadow field " + field.name + ":" + field.desc + " is missing from " + target);
                    }
                }
            }
        }

        for (MethodNode method : mixin.methods)
        {
            if (hasAnnotation(method.invisibleAnnotations, SHADOW) || hasAnnotation(method.visibleAnnotations, SHADOW))
            {
                for (String target : resolved)
                {
                    if (findMethod(target, method.name, method.desc, this.pool.info(target).isInterface()) == null)
                    {
                        problem(where, "@Shadow method " + method.name + method.desc + " is missing from " + target);
                    }
                }
            }
            for (AnnotationNode annotation : annotations(method))
            {
                if (INVOKER.equals(annotation.desc) || ACCESSOR.equals(annotation.desc))
                {
                    for (String target : resolved)
                    {
                        checkGeneratedAccessor(where + "." + method.name, target, method, annotation);
                    }
                    continue;
                }
                if (!isInjector(annotation.desc))
                {
                    continue;
                }
                AnnotationNode group = find(method.invisibleAnnotations, GROUP);
                if (group == null)
                {
                    group = find(method.visibleAnnotations, GROUP);
                }
                Object require = value(annotation, "require");
                boolean optional = require instanceof Integer count && count == 0;
                if (optional || group != null)
                {
                    // An optional or grouped injection may miss on some releases; its group decides.
                    if (group != null && value(group, "name") instanceof String name)
                    {
                        Object min = value(group, "min");
                        groupMinimums.merge(name, min instanceof Integer count ? count : 1, Math::max);
                        for (String target : resolved)
                        {
                            boolean hit = false;
                            for (String selector : stringList(annotation, "method"))
                            {
                                hit |= selectorExists(target, selector);
                            }
                            groupHits.computeIfAbsent(name, ignored -> new HashMap<>())
                                    .merge(target, hit ? 1 : 0, Integer::sum);
                        }
                    }
                    continue;
                }
                for (String selector : stringList(annotation, "method"))
                {
                    for (String target : resolved)
                    {
                        checkSelector(where + "." + method.name, target, selector);
                    }
                }
                for (AnnotationNode at : atAnnotations(annotation))
                {
                    Object atTarget = value(at, "target");
                    if (atTarget instanceof String reference && !reference.isEmpty())
                    {
                        checkAtTarget(where + "." + method.name, reference);
                    }
                }
            }
        }

        for (Map.Entry<String, Integer> entry : groupMinimums.entrySet())
        {
            for (String target : resolved)
            {
                int hits = groupHits.getOrDefault(entry.getKey(), Collections.emptyMap()).getOrDefault(target, 0);
                if (hits < entry.getValue())
                {
                    problem(where, "injector group " + entry.getKey() + " finds " + hits + " of its "
                            + entry.getValue() + " required target methods in " + target);
                }
            }
        }
    }

    /**
     * An {@code @Invoker} names a target method with the invoker's own descriptor; an
     * {@code @Accessor} names a field whose type is the getter's return or the setter's parameter.
     */
    private void checkGeneratedAccessor(String where, String target, MethodNode method, AnnotationNode annotation)
    {
        Object value = value(annotation, "value");
        String name = value instanceof String string && !string.isEmpty() ? string : null;
        boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
        if (INVOKER.equals(annotation.desc))
        {
            if (name == null)
            {
                return;
            }
            Member member = findMethod(target, name, method.desc, this.pool.info(target).isInterface());
            if (member == null)
            {
                problem(where, "@Invoker targets missing method " + target + "." + name + method.desc);
            }
            else if (member.isStatic() != isStatic)
            {
                problem(where, "@Invoker target " + target + "." + name + method.desc + " changed static-ness");
            }
            return;
        }

        Type methodType = Type.getMethodType(method.desc);
        boolean getter = methodType.getArgumentTypes().length == 0;
        String fieldDesc = getter ? methodType.getReturnType().getDescriptor() : methodType.getArgumentTypes()[0].getDescriptor();
        if (name == null)
        {
            return;
        }
        Member member = findField(target, name, fieldDesc);
        if (member == null)
        {
            problem(where, "@Accessor targets missing field " + target + "." + name + ":" + fieldDesc);
        }
    }

    private void checkSelector(String where, String target, String selector)
    {
        if (!selectorExists(target, selector))
        {
            problem(where, "injector targets " + target + "." + selector + ", which does not exist");
        }
    }

    private boolean selectorExists(String target, String selector)
    {
        String name = selector;
        String desc = null;
        int paren = selector.indexOf('(');
        if (paren >= 0)
        {
            name = selector.substring(0, paren);
            desc = selector.substring(paren);
        }
        if (name.startsWith("L") && name.contains(";"))
        {
            name = name.substring(name.indexOf(';') + 1);
        }
        if (name.contains("*") || name.isEmpty())
        {
            return true;
        }

        ClassInfo info = this.pool.info(target);
        for (Map.Entry<String, Member> entry : info.methods().entrySet())
        {
            Member member = entry.getValue();
            if (member.name().equals(name) && (desc == null || member.desc().equals(desc)))
            {
                return true;
            }
        }
        return false;
    }

    private void checkAtTarget(String where, String reference)
    {
        // Mixin member references: Lowner;name(desc)ret for methods, Lowner;name:desc for fields.
        if (!reference.startsWith("L") || !reference.contains(";"))
        {
            return;
        }
        String owner = reference.substring(1, reference.indexOf(';'));
        String rest = reference.substring(reference.indexOf(';') + 1);
        if (!requireClass(where, owner) || isJdk(owner))
        {
            return;
        }
        int colon = rest.indexOf(':');
        int paren = rest.indexOf('(');
        if (colon >= 0)
        {
            String name = rest.substring(0, colon);
            String desc = rest.substring(colon + 1);
            if (findField(owner, name, desc) == null)
            {
                problem(where, "@At targets missing field " + owner + "." + name + ":" + desc);
            }
        }
        else if (paren >= 0)
        {
            String name = rest.substring(0, paren);
            String desc = rest.substring(paren);
            if (findMethod(owner, name, desc, this.pool.info(owner).isInterface()) == null)
            {
                problem(where, "@At targets missing method " + owner + "." + name + desc);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Resolution, following JVMS 5.4.3.2 (fields) and 5.4.3.3/5.4.3.4 (methods)
    // ---------------------------------------------------------------------------------------------

    Member findField(String owner, String name, String desc)
    {
        ClassInfo info = this.pool.info(owner);
        if (info == null)
        {
            return null;
        }
        Member own = info.fields().get(name + ":" + desc);
        if (own != null)
        {
            return own;
        }
        for (String iface : info.interfaces())
        {
            Member inherited = findField(iface, name, desc);
            if (inherited != null)
            {
                return inherited;
            }
        }
        return info.superName() == null ? null : findField(info.superName(), name, desc);
    }

    Member findMethod(String owner, String name, String desc, boolean interfaceResolution)
    {
        ClassInfo info = this.pool.info(owner);
        if (info == null)
        {
            return null;
        }
        if (!interfaceResolution)
        {
            for (ClassInfo current = info; current != null;
                    current = current.superName() == null ? null : this.pool.info(current.superName()))
            {
                Member member = current.methods().get(name + desc);
                if (member != null)
                {
                    return member;
                }
            }
        }
        else
        {
            Member member = info.methods().get(name + desc);
            if (member != null)
            {
                return member;
            }
            ClassInfo object = this.pool.info("java/lang/Object");
            Member objectMember = object == null ? null : object.methods().get(name + desc);
            if (objectMember != null && (objectMember.access() & Opcodes.ACC_PUBLIC) != 0)
            {
                return objectMember;
            }
        }
        return findInSuperinterfaces(info, name, desc, new LinkedHashSet<>());
    }

    private Member findInSuperinterfaces(ClassInfo info, String name, String desc, Set<String> seen)
    {
        List<String> interfaces = new ArrayList<>(info.interfaces());
        if (info.superName() != null)
        {
            ClassInfo parent = this.pool.info(info.superName());
            if (parent != null)
            {
                Member viaParent = findInSuperinterfaces(parent, name, desc, seen);
                if (viaParent != null)
                {
                    return viaParent;
                }
            }
        }
        for (String iface : interfaces)
        {
            if (!seen.add(iface))
            {
                continue;
            }
            ClassInfo ifaceInfo = this.pool.info(iface);
            if (ifaceInfo == null)
            {
                continue;
            }
            Member member = ifaceInfo.methods().get(name + desc);
            if (member != null && (member.access() & Opcodes.ACC_PRIVATE) == 0)
            {
                return member;
            }
            Member deeper = findInSuperinterfaces(ifaceInfo, name, desc, seen);
            if (deeper != null)
            {
                return deeper;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Class existence
    // ---------------------------------------------------------------------------------------------

    private boolean requireInternalOrArray(String where, String internalOrArray)
    {
        if (internalOrArray.startsWith("["))
        {
            return requireDescriptor(where, internalOrArray);
        }
        return requireClass(where, internalOrArray);
    }

    private boolean requireDescriptor(String where, String descriptor)
    {
        boolean ok = true;
        Type type = Type.getType(descriptor);
        if (type.getSort() == Type.METHOD)
        {
            for (Type argument : type.getArgumentTypes())
            {
                ok &= requireType(where, argument);
            }
            return ok & requireType(where, type.getReturnType());
        }
        return requireType(where, type);
    }

    private boolean requireType(String where, Type type)
    {
        if (type.getSort() == Type.METHOD)
        {
            return requireDescriptor(where, type.getDescriptor());
        }
        Type element = type.getSort() == Type.ARRAY ? type.getElementType() : type;
        if (element.getSort() != Type.OBJECT)
        {
            return true;
        }
        return requireClass(where, element.getInternalName());
    }

    private boolean requireClass(String where, String internalName)
    {
        if (internalName == null)
        {
            return true;
        }
        if (this.pool.info(internalName) != null)
        {
            return true;
        }
        if (!startsWithAny(internalName, this.optionalPrefixes))
        {
            problem(where, "missing class " + internalName);
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private void problem(String where, String message)
    {
        this.problems.add(where + ": " + message);
    }

    private static List<String> mixinTargets(ClassNode node)
    {
        AnnotationNode mixin = find(node.invisibleAnnotations, MIXIN);
        if (mixin == null)
        {
            mixin = find(node.visibleAnnotations, MIXIN);
        }
        if (mixin == null)
        {
            return null;
        }
        List<String> targets = new ArrayList<>();
        Object value = value(mixin, "value");
        if (value instanceof List<?> types)
        {
            for (Object type : types)
            {
                targets.add(((Type) type).getInternalName());
            }
        }
        for (String target : stringList(mixin, "targets"))
        {
            targets.add(target.replace('.', '/'));
        }
        return targets;
    }

    private static boolean isInjector(String desc)
    {
        return desc.startsWith("Lorg/spongepowered/asm/mixin/injection/")
                && !desc.equals("Lorg/spongepowered/asm/mixin/injection/At;")
                && !desc.equals("Lorg/spongepowered/asm/mixin/injection/Slice;")
                || desc.startsWith("Lcom/llamalad7/mixinextras/injector/");
    }

    private static List<AnnotationNode> annotations(MethodNode method)
    {
        List<AnnotationNode> result = new ArrayList<>();
        if (method.invisibleAnnotations != null)
        {
            result.addAll(method.invisibleAnnotations);
        }
        if (method.visibleAnnotations != null)
        {
            result.addAll(method.visibleAnnotations);
        }
        return result;
    }

    private static List<AnnotationNode> atAnnotations(AnnotationNode injector)
    {
        List<AnnotationNode> result = new ArrayList<>();
        Object at = value(injector, "at");
        if (at instanceof AnnotationNode single)
        {
            result.add(single);
        }
        else if (at instanceof List<?> many)
        {
            for (Object entry : many)
            {
                if (entry instanceof AnnotationNode node)
                {
                    result.add(node);
                }
            }
        }
        return result;
    }

    private static List<String> stringList(AnnotationNode annotation, String key)
    {
        Object value = value(annotation, key);
        List<String> result = new ArrayList<>();
        if (value instanceof String single)
        {
            result.add(single);
        }
        else if (value instanceof List<?> many)
        {
            for (Object entry : many)
            {
                if (entry instanceof String string)
                {
                    result.add(string);
                }
            }
        }
        return result;
    }

    private static Object value(AnnotationNode annotation, String key)
    {
        if (annotation.values == null)
        {
            return null;
        }
        for (int i = 0; i + 1 < annotation.values.size(); i += 2)
        {
            if (key.equals(annotation.values.get(i)))
            {
                return annotation.values.get(i + 1);
            }
        }
        return null;
    }

    private static boolean hasAnnotation(List<AnnotationNode> annotations, String desc)
    {
        return find(annotations, desc) != null;
    }

    private static AnnotationNode find(List<AnnotationNode> annotations, String desc)
    {
        if (annotations == null)
        {
            return null;
        }
        for (AnnotationNode annotation : annotations)
        {
            if (desc.equals(annotation.desc))
            {
                return annotation;
            }
        }
        return null;
    }

    private static boolean isJdk(String internalName)
    {
        return internalName.startsWith("java/") || internalName.startsWith("javax/")
                || internalName.startsWith("jdk/") || internalName.startsWith("sun/");
    }

    private static String packageOf(String internalName)
    {
        int slash = internalName.lastIndexOf('/');
        return slash < 0 ? "" : internalName.substring(0, slash);
    }

    private static boolean startsWithAny(String value, List<String> prefixes)
    {
        for (String prefix : prefixes)
        {
            if (value.startsWith(prefix))
            {
                return true;
            }
        }
        return false;
    }

    private static String requireValue(String flag, String value)
    {
        if (value == null)
        {
            throw new IllegalArgumentException(flag + " needs a value.");
        }
        return value;
    }

    // ---------------------------------------------------------------------------------------------
    // Class pool
    // ---------------------------------------------------------------------------------------------

    record Member(String owner, String name, String desc, int access)
    {
        boolean isStatic()
        {
            return (this.access & Opcodes.ACC_STATIC) != 0;
        }
    }

    record ClassInfo(
            String name,
            int access,
            String superName,
            List<String> interfaces,
            Map<String, Member> fields,
            Map<String, Member> methods)
    {
        boolean isInterface()
        {
            return (this.access & Opcodes.ACC_INTERFACE) != 0;
        }
    }

    /** Class bytes from jars, nested jars, library directories and the running JDK. */
    static final class ClassPool
    {
        private final Map<String, byte[]> bytesByName = new HashMap<>();
        private final Map<String, Path> sourceByName = new HashMap<>();
        private final Map<Path, List<String>> namesBySource = new HashMap<>();
        private final Map<String, ClassInfo> infoCache = new HashMap<>();
        private final Set<String> missing = new LinkedHashSet<>();
        private final Set<String> checkedNames = new LinkedHashSet<>();

        void add(Path entry) throws IOException
        {
            if (Files.isDirectory(entry))
            {
                try (Stream<Path> files = Files.walk(entry))
                {
                    for (Path jar : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".jar")).sorted()::iterator)
                    {
                        addJar(jar);
                    }
                }
            }
            else
            {
                addJar(entry);
            }
        }

        void addJar(Path jar) throws IOException
        {
            List<String> names = new ArrayList<>();
            try (ZipFile zip = new ZipFile(jar.toFile()))
            {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements())
                {
                    ZipEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    if (entryName.startsWith("META-INF/jars/") && entryName.endsWith(".jar"))
                    {
                        try (InputStream nested = zip.getInputStream(entry))
                        {
                            addNested(jar, nested);
                        }
                    }
                    else if (isClassEntry(entryName))
                    {
                        String name = entryName.substring(0, entryName.length() - ".class".length());
                        if (!this.bytesByName.containsKey(name))
                        {
                            try (InputStream input = zip.getInputStream(entry))
                            {
                                this.bytesByName.put(name, input.readAllBytes());
                                this.sourceByName.put(name, jar);
                            }
                        }
                        names.add(name);
                    }
                }
            }
            this.namesBySource.computeIfAbsent(jar, ignored -> new ArrayList<>()).addAll(names);
        }

        private void addNested(Path outer, InputStream stream) throws IOException
        {
            try (ZipInputStream zip = new ZipInputStream(stream))
            {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null)
                {
                    String entryName = entry.getName();
                    if (entryName.startsWith("META-INF/jars/") && entryName.endsWith(".jar"))
                    {
                        addNested(outer, new java.io.ByteArrayInputStream(readAll(zip)));
                    }
                    else if (isClassEntry(entryName))
                    {
                        String name = entryName.substring(0, entryName.length() - ".class".length());
                        this.bytesByName.putIfAbsent(name, readAll(zip));
                        this.sourceByName.putIfAbsent(name, outer);
                    }
                }
            }
        }

        List<String> namesIn(Path jar)
        {
            List<String> names = this.namesBySource.getOrDefault(jar, Collections.emptyList());
            this.checkedNames.addAll(names);
            return names;
        }

        boolean isChecked(String name)
        {
            return this.checkedNames.contains(name);
        }

        ClassNode node(String name)
        {
            byte[] bytes = bytes(name);
            if (bytes == null)
            {
                throw new IllegalStateException("No bytes for " + name);
            }
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES);
            return node;
        }

        ClassInfo info(String name)
        {
            if (name.startsWith("["))
            {
                return info("java/lang/Object");
            }
            ClassInfo cached = this.infoCache.get(name);
            if (cached != null || this.missing.contains(name))
            {
                return cached;
            }
            byte[] bytes = bytes(name);
            if (bytes == null)
            {
                this.missing.add(name);
                return null;
            }
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            Map<String, Member> fields = new HashMap<>();
            for (FieldNode field : node.fields)
            {
                fields.put(field.name + ":" + field.desc, new Member(node.name, field.name, field.desc, field.access));
            }
            Map<String, Member> methods = new HashMap<>();
            for (MethodNode method : node.methods)
            {
                methods.put(method.name + method.desc, new Member(node.name, method.name, method.desc, method.access));
            }
            ClassInfo info = new ClassInfo(node.name, node.access, node.superName,
                    List.copyOf(node.interfaces), fields, methods);
            this.infoCache.put(name, info);
            return info;
        }

        private byte[] bytes(String name)
        {
            byte[] bytes = this.bytesByName.get(name);
            if (bytes != null)
            {
                return bytes;
            }
            try (InputStream jdk = ClassLoader.getSystemResourceAsStream(name + ".class"))
            {
                if (jdk != null && isJdk(name))
                {
                    bytes = jdk.readAllBytes();
                    this.bytesByName.put(name, bytes);
                    return bytes;
                }
            }
            catch (IOException exception)
            {
                throw new UncheckedIOException(exception);
            }
            return null;
        }

        private static boolean isClassEntry(String entryName)
        {
            return entryName.endsWith(".class")
                    && !entryName.startsWith("META-INF/")
                    && !entryName.endsWith("module-info.class")
                    && !entryName.endsWith("package-info.class");
        }

        private static byte[] readAll(InputStream input) throws IOException
        {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            input.transferTo(output);
            return output.toByteArray();
        }
    }
}
