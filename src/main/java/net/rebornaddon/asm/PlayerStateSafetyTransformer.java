package net.rebornaddon.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class PlayerStateSafetyTransformer implements IClassTransformer {
    private static final Logger LOGGER = LogManager.getLogger("RebornAddon Player State");
    private static final String LIVING_BASE = "net.minecraft.entity.EntityLivingBase";
    private static final String PARALYSIS_EXPIRY =
            "net.narutomod.procedure.ProcedureParalysisPotionExpires";
    private static final String HOOKS = "net/rebornaddon/compat/PlayerStateSafetyHooks";
    private static final String NBT_DESCRIPTOR = "(Lnet/minecraft/nbt/NBTTagCompound;)V";
    private static final String OBFUSCATED_NBT_DESCRIPTOR = "(Lfy;)V";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        String className = transformedName == null ? name : transformedName;
        try {
            if (LIVING_BASE.equals(className) || LIVING_BASE.equals(name)) {
                return patchLivingBase(basicClass);
            }
            if (PARALYSIS_EXPIRY.equals(className) || PARALYSIS_EXPIRY.equals(name)) {
                return patchParalysisExpiry(basicClass);
            }
        } catch (Throwable throwable) {
            LOGGER.error("Could not apply player state safety hooks to {}.", className, throwable);
        }
        return basicClass;
    }

    private byte[] patchLivingBase(byte[] basicClass) {
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);
        boolean setterPatched = false;
        boolean readPatched = false;
        boolean writePatched = false;
        for (MethodNode method : node.methods) {
            if (isAbsorptionSetter(method)) {
                InsnList guard = new InsnList();
                guard.add(new VarInsnNode(Opcodes.FLOAD, 1));
                guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                        "validateAbsorptionWrite", "(F)F", false));
                guard.add(new VarInsnNode(Opcodes.FSTORE, 1));
                method.instructions.insert(guard);
                setterPatched = true;
            } else if (isNbtMethod(method, "readEntityFromNBT", "func_70037_a", "a")) {
                insertReconcileBeforeReturns(method);
                readPatched = true;
            } else if (isNbtMethod(method, "writeEntityToNBT", "func_70014_b", "b")) {
                InsnList reconcile = new InsnList();
                reconcile.add(new VarInsnNode(Opcodes.ALOAD, 0));
                reconcile.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                        "reconcileAbsorption", "(Lnet/minecraft/entity/EntityLivingBase;)Z", false));
                reconcile.add(new InsnNode(Opcodes.POP));
                method.instructions.insert(reconcile);
                writePatched = true;
            }
        }
        if (!setterPatched || !readPatched || !writePatched) {
            LOGGER.error("EntityLivingBase absorption hooks were incomplete: setter={}, read={}, write={}.",
                    setterPatched, readPatched, writePatched);
            return basicClass;
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private byte[] patchParalysisExpiry(byte[] basicClass) {
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);
        for (MethodNode method : node.methods) {
            if (!"executeProcedure".equals(method.name)
                    || !"(Ljava/util/Map;)V".equals(method.desc)) {
                continue;
            }
            method.instructions.clear();
            method.tryCatchBlocks.clear();
            if (method.localVariables != null) {
                method.localVariables.clear();
            }
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                    "expireNarutoParalysis", "(Ljava/util/Map;)V", false));
            method.instructions.add(new InsnNode(Opcodes.RETURN));
            method.maxStack = 1;
            method.maxLocals = 1;
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            return writer.toByteArray();
        }
        LOGGER.error("NarutoMod paralysis expiry procedure did not contain executeProcedure(Map).");
        return basicClass;
    }

    private static void insertReconcileBeforeReturns(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; ) {
            AbstractInsnNode next = instruction.getNext();
            if (instruction.getOpcode() == Opcodes.RETURN) {
                InsnList reconcile = new InsnList();
                reconcile.add(new VarInsnNode(Opcodes.ALOAD, 0));
                reconcile.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                        "reconcileAbsorption", "(Lnet/minecraft/entity/EntityLivingBase;)Z", false));
                reconcile.add(new InsnNode(Opcodes.POP));
                method.instructions.insertBefore(instruction, reconcile);
            }
            instruction = next;
        }
    }

    private static boolean isAbsorptionSetter(MethodNode method) {
        return "(F)V".equals(method.desc)
                && ("setAbsorptionAmount".equals(method.name)
                || "func_110149_m".equals(method.name)
                || "m".equals(method.name));
    }

    private static boolean isNbtMethod(MethodNode method, String deobfuscated, String srg,
            String obfuscated) {
        boolean descriptorMatches = NBT_DESCRIPTOR.equals(method.desc)
                || OBFUSCATED_NBT_DESCRIPTOR.equals(method.desc);
        return descriptorMatches && (deobfuscated.equals(method.name)
                || srg.equals(method.name)
                || obfuscated.equals(method.name));
    }
}
