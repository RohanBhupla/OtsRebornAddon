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

public final class ExplosiveClaySafetyTransformer implements IClassTransformer {
    static final String TARGET = "net.narutomod.item.ItemBakuton$ExplosiveClay$Jutsu";
    static final String METHOD = "createJutsu";
    static final String DESCRIPTOR = "(Lnet/minecraft/item/ItemStack;"
            + "Lnet/minecraft/entity/EntityLivingBase;F)Z";
    static final String HOOK = "net/rebornaddon/compat/ExplosiveClayCompatibility";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        String className = transformedName == null ? name : transformedName;
        if (!TARGET.equals(className) && !TARGET.equals(name)) {
            return basicClass;
        }
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!METHOD.equals(method.name) || !DESCRIPTOR.equals(method.desc)
                        || hasNormalizationHook(method)) {
                    continue;
                }
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK,
                        "normalizeExplosiveClayStack",
                        "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)"
                                + "Lnet/minecraft/item/ItemStack;", false));
                hook.add(new VarInsnNode(Opcodes.ASTORE, 1));
                method.instructions.insert(hook);
                changed = true;
            }
            if (!changed) {
                return basicClass;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private static boolean hasNormalizationHook(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (HOOK.equals(call.owner) && "normalizeExplosiveClayStack".equals(call.name)) {
                return true;
            }
        }
        return false;
    }
}
