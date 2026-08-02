package net.rebornaddon.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class JutsuCooldownGuardTransformer implements IClassTransformer {

    private static final String TARGET = "net.jutsu.cooldown.asm.JutsuCooldownTransformer";
    private static final String TARGET_INTERNAL = "net/jutsu/cooldown/asm/JutsuCooldownTransformer";
    private static final String KG_TWEAKS_TARGET = "net.kgtweaks.asm.ParticleOptimizationTransformer";
    private static final String KG_TWEAKS_TARGET_INTERNAL = "net/kgtweaks/asm/ParticleOptimizationTransformer";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }

        if (!TARGET.equals(name) && !TARGET.equals(transformedName)
                && !KG_TWEAKS_TARGET.equals(name) && !KG_TWEAKS_TARGET.equals(transformedName)) {
            return basicClass;
        }

        try {
            return patchTransformer(basicClass);
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private byte[] patchTransformer(byte[] basicClass) {
        ClassReader reader = new ClassReader(basicClass);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);

        if (TARGET_INTERNAL.equals(classNode.name)) {
            return patchJutsuCooldownTransformer(basicClass, classNode);
        }

        if (KG_TWEAKS_TARGET_INTERNAL.equals(classNode.name)) {
            return patchKgTweaksTransformer(basicClass, classNode);
        }

        return basicClass;
    }

    private byte[] patchJutsuCooldownTransformer(byte[] basicClass, ClassNode classNode) {
        boolean changed = false;
        for (MethodNode method : classNode.methods) {
            if (("transformEntityRaiunkuhaClass".equals(method.name)
                    || "transformItemJutsuClass".equals(method.name)) && "([B)[B".equals(method.desc)) {
                makePassThrough(method);
                changed = true;
            }
        }

        if (!changed) {
            return basicClass;
        }

        ClassWriter writer = new ClassWriter(0);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private byte[] patchKgTweaksTransformer(byte[] basicClass, ClassNode classNode) {
        boolean changed = false;
        for (MethodNode method : classNode.methods) {
            if ("transformKushoProcedure".equals(method.name) && "([B)[B".equals(method.desc)) {
                makePassThrough(method);
                changed = true;
            }
        }

        if (!changed) {
            return basicClass;
        }

        ClassWriter writer = new ClassWriter(0);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private void makePassThrough(MethodNode method) {
        int inputIndex = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;

        method.instructions.clear();
        method.tryCatchBlocks.clear();
        if (method.localVariables != null) {
            method.localVariables.clear();
        }
        if (method.visibleLocalVariableAnnotations != null) {
            method.visibleLocalVariableAnnotations.clear();
        }
        if (method.invisibleLocalVariableAnnotations != null) {
            method.invisibleLocalVariableAnnotations.clear();
        }

        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, inputIndex));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxStack = 1;
        method.maxLocals = inputIndex + 1;
    }
}
