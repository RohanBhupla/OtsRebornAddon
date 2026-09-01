package net.rebornaddon.compat;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ChakraControlCompatibilityTest {
    @Test
    public void wallMultiplierScalesNormalAndLedgeSpeeds() {
        assertEquals(0.17D, ChakraControlCompatibility.scaledWallClimbSpeed(false, 1.0D), 0.00001D);
        assertEquals(0.255D, ChakraControlCompatibility.scaledWallClimbSpeed(false, 1.5D), 0.00001D);
        assertEquals(0.345D, ChakraControlCompatibility.scaledWallClimbSpeed(true, 1.5D), 0.00001D);
    }

    @Test
    public void crouchingDoesNotRemoveTheWaterSurfaceCollision() throws Exception {
        InputStream bytes = ChakraControlCompatibility.class.getResourceAsStream(
                "/net/rebornaddon/compat/ChakraControlCompatibility.class");
        ClassNode type = new ClassNode();
        new ClassReader(bytes).accept(type, 0);
        boolean checksSneaking = false;
        for (MethodNode method : type.methods) {
            if (!"canUseWaterWalking".equals(method.name)) continue;
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode
                        && ("func_70093_af".equals(((MethodInsnNode) instruction).name)
                        || "isSneaking".equals(((MethodInsnNode) instruction).name))) {
                    checksSneaking = true;
                }
            }
        }
        assertFalse(checksSneaking);
    }
}
