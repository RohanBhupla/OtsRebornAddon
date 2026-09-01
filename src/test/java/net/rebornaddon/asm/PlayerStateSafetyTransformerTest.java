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

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PlayerStateSafetyTransformerTest {
    @Test
    public void patchesFullyObfuscatedMohistLivingBaseMethods() {
        byte[] transformed = new PlayerStateSafetyTransformer().transform(
                "vp", "net.minecraft.entity.EntityLivingBase", livingBaseFixture(true));

        ClassNode node = read(transformed);
        assertHookCalls(node);
    }

    @Test
    public void stillPatchesDevelopmentLivingBaseMethods() {
        byte[] transformed = new PlayerStateSafetyTransformer().transform(
                "net.minecraft.entity.EntityLivingBase",
                "net.minecraft.entity.EntityLivingBase", livingBaseFixture(false));

        ClassNode node = read(transformed);
        assertHookCalls(node);
    }

    private static void assertHookCalls(ClassNode node) {
        Set<String> hooks = new HashSet<>();
        int reconcileCalls = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                 instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (!"net/rebornaddon/compat/PlayerStateSafetyHooks".equals(call.owner)) continue;
                hooks.add(call.name);
                if ("reconcileAbsorption".equals(call.name)) reconcileCalls++;
            }
        }
        assertTrue(hooks.contains("validateAbsorptionWrite"));
        assertTrue(hooks.contains("reconcileAbsorption"));
        assertEquals(2, reconcileCalls);
    }

    private static byte[] livingBaseFixture(boolean obfuscated) {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = obfuscated ? "vp" : "net/minecraft/entity/EntityLivingBase";
        node.superName = "java/lang/Object";

        addVoidMethod(node, obfuscated ? "m" : "setAbsorptionAmount", "(F)V");
        String nbtDescriptor = obfuscated
                ? "(Lfy;)V" : "(Lnet/minecraft/nbt/NBTTagCompound;)V";
        addVoidMethod(node, obfuscated ? "a" : "readEntityFromNBT", nbtDescriptor);
        addVoidMethod(node, obfuscated ? "b" : "writeEntityToNBT", nbtDescriptor);

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void addVoidMethod(ClassNode node, String name, String descriptor) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 0;
        method.maxLocals = 2;
        node.methods.add(method);
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }
}
