package net.rebornaddon.compat;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void waterSurfaceActsAsAOneWayPlatform() {
        assertTrue(ChakraControlCompatibility.approachingSurfaceFromAbove(64.0D, 64.0D));
        assertTrue(ChakraControlCompatibility.approachingSurfaceFromAbove(64.4D, 64.0D));
        assertFalse(ChakraControlCompatibility.approachingSurfaceFromAbove(63.8D, 64.0D));
    }

    @Test
    public void submergedPlayersRecoverToTheSurfaceInOneTick() {
        assertEquals(0.2D, ChakraControlCompatibility.recoveryLift(63.8D, 64.0D), 0.00001D);
        assertEquals(0.8D, ChakraControlCompatibility.recoveryLift(63.2D, 64.0D), 0.00001D);
        assertEquals(1.25D, ChakraControlCompatibility.recoveryLift(62.75D, 64.0D), 0.00001D);
        assertEquals(0.0D, ChakraControlCompatibility.recoveryLift(62.7D, 64.0D), 0.00001D);
        assertEquals(0.0D, ChakraControlCompatibility.recoveryLift(64.1D, 64.0D), 0.00001D);
    }

    @Test
    public void mountedWaterWalkingLocksToTheSurfaceWithoutBlockingJumps() {
        assertEquals(0.202D, ChakraControlCompatibility.mountSurfaceCorrection(
                63.8D, 64.0D, -0.1D), 0.00001D);
        assertEquals(-0.998D, ChakraControlCompatibility.mountSurfaceCorrection(
                65.0D, 64.0D, 0.0D), 0.00001D);
        assertTrue(Double.isNaN(ChakraControlCompatibility.mountSurfaceCorrection(
                64.0D, 64.0D, 0.8D)));
    }

    @Test
    public void upwardChakraJumpVelocityDoesNotDisableSurfaceCollision() throws Exception {
        InputStream bytes = ChakraControlCompatibility.class.getResourceAsStream(
                "/net/rebornaddon/compat/ChakraControlCompatibility.class");
        ClassNode type = new ClassNode();
        new ClassReader(bytes).accept(type, 0);
        boolean readsVerticalVelocity = false;
        for (MethodNode method : type.methods) {
            if (!"addWaterSurfaceCollisions".equals(method.name)) continue;
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof FieldInsnNode
                        && ("field_70181_x".equals(((FieldInsnNode) instruction).name)
                        || "motionY".equals(((FieldInsnNode) instruction).name))) {
                    readsVerticalVelocity = true;
                }
            }
        }
        assertFalse(readsVerticalVelocity);
    }
}
