package net.rebornaddon.chakra.network;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChakraControlToggleMessageTest {
    @Test
    public void toggleStatusAlwaysUsesReadableText() {
        assertEquals("Chakra Control: Enabled", ChakraControlToggleMessage.statusText(true));
        assertEquals("Chakra Control: Disabled", ChakraControlToggleMessage.statusText(false));
    }
}
