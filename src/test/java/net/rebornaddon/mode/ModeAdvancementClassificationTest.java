package net.rebornaddon.mode;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ModeAdvancementClassificationTest {
    @Before
    public void discoverModes() {
        ModeRegistry.INSTANCE.discover();
    }

    @After
    public void clearModes() {
        ModeRegistry.INSTANCE.clear();
    }

    @Test
    public void resolvesModeActivationJutsuByRealNameOrRegistryPath() {
        assertMode("Curse Mark", "ahznbcursemarkaddon:cursemark_jutsu",
                "ahznbcursemarkaddon:cursemark_mode");
        assertMode("Strength of a Hundred Seal (Byakugou)",
                "ahznbcursemarkaddon:byakugou_jutsu",
                "ahznbcursemarkaddon:byakugou_mode");
        assertMode("Unlocalized Activation", "narutomod:lightning_chakra_mode_jutsu",
                "narutomod:lightning_chakra_mode");
    }

    private static void assertMode(String name, String id, String expected) {
        ModeDefinition mode = ModeRegistry.INSTANCE.modeForAdvancement(id, name);
        assertNotNull(name, mode);
        assertEquals(expected, mode.id());
    }
}
