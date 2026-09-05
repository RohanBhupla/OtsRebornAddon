package net.rebornaddon.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.nio.charset.StandardCharsets;

public final class AdvancementSafetyTransformer implements IClassTransformer {
    private static final String PLAYER_ADVANCEMENTS =
            "net/minecraft/advancements/PlayerAdvancements";
    private static final byte[] PLAYER_ADVANCEMENTS_BYTES =
            PLAYER_ADVANCEMENTS.getBytes(StandardCharsets.US_ASCII);
    private static final String HELPER =
            "net/rebornaddon/compat/NarutoAddonAdvancementCompatibility";
    private static final String GUI_TOAST = "net.minecraft.client.gui.toasts.GuiToast";
    private static final String ADVANCEMENT_REWARDS =
            "net.minecraft.advancements.AdvancementRewards";
    private static final String ENTITY_PLAYER_MP = "net/minecraft/entity/player/EntityPlayerMP";
    private static final String CLIENT_HELPER =
            "net/rebornaddon/advancement/client/AdvancementClientGuard";
    private static final String PROGRESS_DESC =
            "(Lnet/minecraft/advancements/Advancement;)"
                    + "Lnet/minecraft/advancements/AdvancementProgress;";
    private static final String CRITERION_DESC =
            "(Lnet/minecraft/advancements/Advancement;Ljava/lang/String;)Z";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }

        String className = transformedName == null ? name : transformedName;
        boolean toastClass = GUI_TOAST.equals(className);
        boolean rewardsClass = ADVANCEMENT_REWARDS.equals(className);
        if (className == null
                || (!toastClass && !rewardsClass && (className.startsWith("net.minecraft.")
                || className.startsWith("net.rebornaddon.")
                || !containsPlayerAdvancementsReference(basicClass)))) {
            return basicClass;
        }

        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (toastClass) {
                    changed |= injectToastGuard(method);
                } else if (rewardsClass) {
                    changed |= replaceUnsafeRecipeUnlocks(method);
                } else {
                    changed |= replaceUnsafeCalls(method);
                }
            }
            if (!changed) {
                return basicClass;
            }

            // Direct call replacements preserve the original stack shape. Recomputing frames for
            // arbitrary addon classes can fail when optional hierarchy types are not loadable yet.
            int flags = toastClass ? ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES : 0;
            ClassWriter writer = new ClassWriter(flags);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private boolean injectToastGuard(MethodNode method) {
        if (!("func_192988_a".equals(method.name) || "addToast".equals(method.name))
                || !"(Lnet/minecraft/client/gui/toasts/IToast;)V".equals(method.desc)
                || method.instructions.getFirst() == null) {
            return false;
        }

        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode
                    && CLIENT_HELPER.equals(((MethodInsnNode) instruction).owner)
                    && "shouldDisplayToast".equals(((MethodInsnNode) instruction).name)) {
                return false;
            }
        }

        LabelNode allowed = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(Opcodes.ALOAD, 1));
        guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CLIENT_HELPER,
                "shouldDisplayToast", "(Ljava/lang/Object;)Z", false));
        guard.add(new JumpInsnNode(Opcodes.IFNE, allowed));
        guard.add(new InsnNode(Opcodes.RETURN));
        guard.add(allowed);
        method.instructions.insertBefore(method.instructions.getFirst(), guard);
        return true;
    }

    private boolean replaceUnsafeCalls(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }

            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !PLAYER_ADVANCEMENTS.equals(call.owner)) {
                continue;
            }

            String helperMethod = helperMethod(call.name, call.desc);
            if (helperMethod == null) {
                continue;
            }

            call.setOpcode(Opcodes.INVOKESTATIC);
            call.owner = HELPER;
            call.name = helperMethod;
            call.desc = withPlayerAdvancements(call.desc);
            call.itf = false;
            changed = true;
        }
        return changed;
    }

    private boolean replaceUnsafeRecipeUnlocks(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !ENTITY_PLAYER_MP.equals(call.owner)
                    || !("func_193102_a".equals(call.name) || "unlockRecipes".equals(call.name))
                    || !"([Lnet/minecraft/util/ResourceLocation;)V".equals(call.desc)) {
                continue;
            }
            call.setOpcode(Opcodes.INVOKESTATIC);
            call.owner = HELPER;
            call.name = "safeUnlockRecipes";
            call.desc = "(Lnet/minecraft/entity/player/EntityPlayerMP;"
                    + "[Lnet/minecraft/util/ResourceLocation;)V";
            call.itf = false;
            changed = true;
        }
        return changed;
    }

    private String helperMethod(String methodName, String descriptor) {
        if (PROGRESS_DESC.equals(descriptor)
                && ("func_192747_a".equals(methodName) || "getProgress".equals(methodName))) {
            return "safeProgress";
        }
        if (!CRITERION_DESC.equals(descriptor)) {
            return null;
        }
        if ("func_192750_a".equals(methodName) || "grantCriterion".equals(methodName)) {
            return "safeGrantCriterion";
        }
        if ("func_192744_b".equals(methodName) || "revokeCriterion".equals(methodName)) {
            return "safeRevokeCriterion";
        }
        return null;
    }

    private String withPlayerAdvancements(String descriptor) {
        return "(Lnet/minecraft/advancements/PlayerAdvancements;" + descriptor.substring(1);
    }

    private boolean containsPlayerAdvancementsReference(byte[] classBytes) {
        int lastStart = classBytes.length - PLAYER_ADVANCEMENTS_BYTES.length;
        for (int i = 0; i <= lastStart; i++) {
            if (classBytes[i] != PLAYER_ADVANCEMENTS_BYTES[0]) {
                continue;
            }
            int j = 1;
            while (j < PLAYER_ADVANCEMENTS_BYTES.length
                    && classBytes[i + j] == PLAYER_ADVANCEMENTS_BYTES[j]) {
                j++;
            }
            if (j == PLAYER_ADVANCEMENTS_BYTES.length) {
                return true;
            }
        }
        return false;
    }
}
