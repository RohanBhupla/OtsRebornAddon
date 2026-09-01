package net.rebornaddon.asm;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AdvancementSafetyTransformerTest {
    @Test
    public void guardsAdvancementToastQueue() {
        ClassNode input = new ClassNode();
        input.version = Opcodes.V1_8;
        input.access = Opcodes.ACC_PUBLIC;
        input.name = "net/minecraft/client/gui/toasts/GuiToast";
        input.superName = "java/lang/Object";
        MethodNode addToast = new MethodNode(Opcodes.ACC_PUBLIC, "func_192988_a",
                "(Lnet/minecraft/client/gui/toasts/IToast;)V", null, null);
        addToast.instructions.add(new InsnNode(Opcodes.RETURN));
        addToast.maxLocals = 2;
        input.methods.add(addToast);

        ClassNode output = read(new AdvancementSafetyTransformer().transform(
                "vp", "net.minecraft.client.gui.toasts.GuiToast", write(input)));

        boolean guardFound = false;
        for (MethodNode method : output.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if ("net/rebornaddon/advancement/client/AdvancementClientGuard".equals(call.owner)
                        && "shouldDisplayToast".equals(call.name)) {
                    assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
                    assertEquals("(Ljava/lang/Object;)Z", call.desc);
                    guardFound = true;
                }
            }
        }
        assertTrue(guardFound);
    }

    @Test
    public void rewritesAddonProgressCallsWithoutRecomputingFrames() {
        ClassNode input = new ClassNode();
        input.version = Opcodes.V1_8;
        input.access = Opcodes.ACC_PUBLIC;
        input.name = "example/addon/AdvancementConsumer";
        input.superName = "missing/optional/AddonBase";
        MethodNode progress = new MethodNode(Opcodes.ACC_PUBLIC, "progress",
                "(Lnet/minecraft/advancements/PlayerAdvancements;"
                        + "Lnet/minecraft/advancements/Advancement;)"
                        + "Lnet/minecraft/advancements/AdvancementProgress;", null, null);
        progress.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        progress.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        progress.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "net/minecraft/advancements/PlayerAdvancements", "getProgress",
                "(Lnet/minecraft/advancements/Advancement;)"
                        + "Lnet/minecraft/advancements/AdvancementProgress;", false));
        progress.instructions.add(new InsnNode(Opcodes.ARETURN));
        progress.maxStack = 2;
        progress.maxLocals = 3;
        input.methods.add(progress);

        ClassNode output = read(new AdvancementSafetyTransformer().transform(
                "example.addon.AdvancementConsumer", "example.addon.AdvancementConsumer", write(input)));

        MethodInsnNode rewritten = firstCall(output.methods.get(0));
        assertEquals(Opcodes.INVOKESTATIC, rewritten.getOpcode());
        assertEquals("net/rebornaddon/compat/NarutoAddonAdvancementCompatibility", rewritten.owner);
        assertEquals("safeProgress", rewritten.name);
        assertEquals("(Lnet/minecraft/advancements/PlayerAdvancements;"
                + "Lnet/minecraft/advancements/Advancement;)"
                + "Lnet/minecraft/advancements/AdvancementProgress;", rewritten.desc);
    }

    private static MethodInsnNode firstCall(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) return (MethodInsnNode) instruction;
        }
        throw new AssertionError("Expected a method call");
    }

    private static byte[] write(ClassNode node) {
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }
}
