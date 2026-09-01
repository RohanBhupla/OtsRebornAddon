package net.rebornaddon.jutsu;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class JutsuRuntimeHooksTest {
    @Test
    public void cooldownDisplayUsesWholeNecessaryUnits() {
        assertEquals("56 seconds", JutsuRuntimeHooks.formatDuration(1111L));
        assertEquals("2 minutes", JutsuRuntimeHooks.formatDuration(2400L));
        assertEquals("1 hour, 2 minutes, 3 seconds",
                JutsuRuntimeHooks.formatDuration(74460L));
    }
}
