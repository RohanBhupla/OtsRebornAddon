package net.rebornaddon.mount;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ArmorWolfMountJumpHandlerTest {
    @Test
    public void usesStrongMountJumpVelocityWhenNoCustomHeightIsConfigured() {
        assertEquals(0.80D, ArmorWolfMountJumpHandler.jumpVelocity(-1.0F), 0.000001D);
        assertEquals(0.80D, ArmorWolfMountJumpHandler.jumpVelocity(0.0F), 0.000001D);
    }

    @Test
    public void followsArmorWolfsConfiguredJumpHeightFormula() {
        assertEquals(1.20D, ArmorWolfMountJumpHandler.jumpVelocity(1.0F), 0.000001D);
        assertEquals(1.60D, ArmorWolfMountJumpHandler.jumpVelocity(2.0F), 0.000001D);
    }
}
