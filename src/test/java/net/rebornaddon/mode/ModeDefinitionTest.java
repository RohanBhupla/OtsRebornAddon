package net.rebornaddon.mode;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModeDefinitionTest {
    @Test
    public void movementDefaultIsTheNativeEffectiveMultiplier() {
        ModeDefinition mode = new ModeDefinition("narutomod:test", "Test", "narutomod",
                "test", "test", "Native movement is 2.5x.", 2.5D);

        assertEquals("1", mode.defaultValue("movement-speed-multiplier"));
        assertEquals(2.5D, mode.movementTarget(1.0D), 0.000001D);
        assertEquals(1.2D, mode.movementAdjustment(3.0D), 0.000001D);
    }

    @Test
    public void installedModeMovementValuesRoundTripThroughStoredAdjustments() {
        assertEquals(2.5D, ModeDefinition.movementTarget(
                "narutomod:toad_sage_mode", 1.0D), 0.000001D);
        assertEquals(3.0D, ModeDefinition.movementTarget(
                "narutomod:toad_sage_mode",
                ModeDefinition.movementAdjustment("narutomod:toad_sage_mode", 3.0D)),
                0.000001D);
        assertTrue(ModeDefinition.hasFixedNativeMovement("narutomod:toad_sage_mode"));
        assertFalse(ModeDefinition.hasFixedNativeMovement("narutomod:eight_gates"));
    }
}
