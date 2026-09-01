package net.rebornaddon.gameplay;

import org.junit.Test;

import java.lang.reflect.InvocationTargetException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GameplaySystemsPluginBridgeTest {
    @Test
    public void unsupportedOptionalSectionIsAQuietCapabilityRejection() {
        String message = GameplaySystemsPluginBridge.expectedRejection(
                new InvocationTargetException(
                        new IllegalArgumentException("Unsupported gameplay section.")));
        assertEquals("The hosted gameplay plugin needs an update for this section.", message);
    }

    @Test
    public void realPluginFailuresAreNotHidden() {
        assertNull(GameplaySystemsPluginBridge.expectedRejection(
                new InvocationTargetException(new NullPointerException("broken"))));
    }
}
