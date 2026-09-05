package net.rebornaddon.asm;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RobeMovementTransformerTest {
    @Test
    public void patchesForge112ModelBipedExactlyOnce() throws Exception {
        byte[] original = resource("net/minecraft/client/model/ModelBiped.class");
        RobeMovementTransformer transformer = new RobeMovementTransformer();
        byte[] transformed = transformer.transform("bpx",
                "net.minecraft.client.model.ModelBiped", original);
        byte[] transformedAgain = transformer.transform("bpx",
                "net.minecraft.client.model.ModelBiped", transformed);

        assertEquals(2, hookCalls(transformed));
        assertEquals(2, hookCalls(transformedAgain));
        assertTrue(renderHookFollowsFinalPoseCalculation(transformed));
    }

    private static boolean renderHookFollowsFinalPoseCalculation(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (MethodNode method : node.methods) {
            if (!("render".equals(method.name) || "func_78088_a".equals(method.name)
                    || "a".equals(method.name))) {
                continue;
            }
            boolean sawRotationCall = false;
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) {
                    continue;
                }
                MethodInsnNode call = (MethodInsnNode) instruction;
                if ("setRotationAngles".equals(call.name)
                        || "func_78087_a".equals(call.name)) {
                    sawRotationCall = true;
                } else if (sawRotationCall
                        && "net/rebornaddon/armor/client/RobeMovementCompatibility"
                        .equals(call.owner)
                        && "constrainLegs".equals(call.name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int hookCalls(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int calls = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) {
                    continue;
                }
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.getOpcode() == Opcodes.INVOKESTATIC
                        && "net/rebornaddon/armor/client/RobeMovementCompatibility".equals(call.owner)
                        && "constrainLegs".equals(call.name)) {
                    calls++;
                }
            }
        }
        return calls;
    }

    private static byte[] resource(String path) throws Exception {
        InputStream input = RobeMovementTransformerTest.class.getClassLoader()
                .getResourceAsStream(path);
        if (input == null) {
            throw new AssertionError("Missing class resource " + path);
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }
}
