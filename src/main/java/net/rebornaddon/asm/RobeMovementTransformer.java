package net.rebornaddon.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class RobeMovementTransformer implements IClassTransformer {
    private static final String MODEL_BIPED = "net.minecraft.client.model.ModelBiped";
    private static final String HOOK =
            "net/rebornaddon/armor/client/RobeMovementCompatibility";
    private static final String MAPPED_DESCRIPTOR =
            "(FFFFFFLnet/minecraft/entity/Entity;)V";
    private static final String OBFUSCATED_DESCRIPTOR = "(FFFFFFLvg;)V";
    private static final String MAPPED_RENDER_DESCRIPTOR =
            "(Lnet/minecraft/entity/Entity;FFFFFF)V";
    private static final String OBFUSCATED_RENDER_DESCRIPTOR = "(Lvg;FFFFFF)V";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        String className = transformedName == null ? name : transformedName;
        if (!MODEL_BIPED.equals(className) && !MODEL_BIPED.equals(name)) {
            return basicClass;
        }

        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);
        boolean changed = false;
        for (MethodNode method : node.methods) {
            if (containsHook(method)) {
                continue;
            }
            if (isRotationMethod(method)) {
                for (AbstractInsnNode instruction = method.instructions.getFirst();
                     instruction != null; instruction = instruction.getNext()) {
                    if (instruction.getOpcode() == Opcodes.RETURN) {
                        method.instructions.insertBefore(instruction, hook(7));
                        changed = true;
                    }
                }
            } else if (isRenderMethod(method)) {
                changed |= addFinalRenderHook(method);
            }
        }
        if (!changed) {
            return basicClass;
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean isRotationMethod(MethodNode method) {
        boolean nameMatches = "setRotationAngles".equals(method.name)
                || "func_78087_a".equals(method.name)
                || "a".equals(method.name);
        return nameMatches && (MAPPED_DESCRIPTOR.equals(method.desc)
                || OBFUSCATED_DESCRIPTOR.equals(method.desc));
    }

    private static boolean isRenderMethod(MethodNode method) {
        boolean nameMatches = "render".equals(method.name)
                || "func_78088_a".equals(method.name)
                || "a".equals(method.name);
        return nameMatches && (MAPPED_RENDER_DESCRIPTOR.equals(method.desc)
                || OBFUSCATED_RENDER_DESCRIPTOR.equals(method.desc));
    }

    private static boolean addFinalRenderHook(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (isRotationCall(call)) {
                method.instructions.insert(instruction, hook(1));
                return true;
            }
        }
        return false;
    }

    private static boolean isRotationCall(MethodInsnNode call) {
        boolean nameMatches = "setRotationAngles".equals(call.name)
                || "func_78087_a".equals(call.name)
                || "a".equals(call.name);
        return nameMatches && (MAPPED_DESCRIPTOR.equals(call.desc)
                || OBFUSCATED_DESCRIPTOR.equals(call.desc));
    }

    private static InsnList hook(int entityLocal) {
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
        hook.add(new VarInsnNode(Opcodes.ALOAD, entityLocal));
        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK,
                "constrainLegs", "(Lnet/minecraft/client/model/ModelBiped;"
                        + "Lnet/minecraft/entity/Entity;)V", false));
        return hook;
    }

    private static boolean containsHook(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode
                    && HOOK.equals(((MethodInsnNode) instruction).owner)
                    && "constrainLegs".equals(((MethodInsnNode) instruction).name)) {
                return true;
            }
        }
        return false;
    }
}
