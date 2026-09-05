package net.rebornaddon.client;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClientZoomHandlerTest {
    @Test
    public void matchesOptifinesFourTimesZoomLevel() {
        assertEquals(17.5F, ClientZoomHandler.zoomedFov(70.0F), 0.0001F);
        assertEquals(22.5F, ClientZoomHandler.zoomedFov(90.0F), 0.0001F);
    }
}
