package net.rebornaddon.compat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProtectedNarutoMountHandlerTest {
    @Test
    public void onlyTheTwoRideableNarutoEntitiesAreProtected() {
        assertTrue(ProtectedNarutoMountHandler.isProtectedMountClassName(
                "net.narutomod.entity.EntityC2$EC"));
        assertTrue(ProtectedNarutoMountHandler.isProtectedMountClassName(
                "net.narutomod.entity.EntitySuitonShark$EC"));
        assertFalse(ProtectedNarutoMountHandler.isProtectedMountClassName(
                "net.narutomod.entity.EntityC2"));
        assertFalse(ProtectedNarutoMountHandler.isProtectedMountClassName(
                "net.narutomod.entity.EntitySuitonShark$SharkBullet"));
    }

    @Test
    public void pathSamplingStaysSubBlockAndRejectsHugeTickJumps() {
        assertEquals(1, ProtectedNarutoMountHandler.sampleCount(0.2D, 0.0D, 0.0D));
        assertEquals(2, ProtectedNarutoMountHandler.sampleCount(1.0D, 0.0D, 0.0D));
        assertEquals(64, ProtectedNarutoMountHandler.sampleCount(48.0D, 0.0D, 0.0D));
        assertTrue(ProtectedNarutoMountHandler.sampleCount(48.1D, 0.0D, 0.0D)
                > ProtectedNarutoMountHandler.MAX_REGION_SAMPLES);
    }

    @Test
    public void onlyAllowedToDeniedEntryIsBlocked() {
        assertTrue(ProtectedNarutoMountHandler.blocksEntryTransition(
                WorldGuardEntryBridge.Access.ALLOWED, WorldGuardEntryBridge.Access.DENIED));
        assertFalse(ProtectedNarutoMountHandler.blocksEntryTransition(
                WorldGuardEntryBridge.Access.DENIED, WorldGuardEntryBridge.Access.ALLOWED));
        assertFalse(ProtectedNarutoMountHandler.blocksEntryTransition(
                WorldGuardEntryBridge.Access.DENIED, WorldGuardEntryBridge.Access.DENIED));
        assertFalse(ProtectedNarutoMountHandler.blocksEntryTransition(
                WorldGuardEntryBridge.Access.UNAVAILABLE, WorldGuardEntryBridge.Access.DENIED));
    }
}
