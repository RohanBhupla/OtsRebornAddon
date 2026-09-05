package net.rebornaddon.asm;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.Assert.assertEquals;

public class ExplosiveClaySafetyTransformerTest {
    @Test
    public void normalizesTheJutsuStackBeforeNarutoModCastsIt() {
        byte[] original = fixture();
        byte[] transformed = new ExplosiveClaySafetyTransformer().transform(
                ExplosiveClaySafetyTransformer.TARGET,
                ExplosiveClaySafetyTransformer.TARGET,
                original);
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        int hooks = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (ExplosiveClaySafetyTransformer.HOOK.equals(call.owner)
                        && "normalizeExplosiveClayStack".equals(call.name)) {
                    hooks++;
                }
            }
        }
        assertEquals(1, hooks);
    }

    private static byte[] fixture() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC,
                ExplosiveClaySafetyTransformer.TARGET.replace('.', '/'),
                null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC,
                ExplosiveClaySafetyTransformer.METHOD,
                ExplosiveClaySafetyTransformer.DESCRIPTOR, null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 4);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
