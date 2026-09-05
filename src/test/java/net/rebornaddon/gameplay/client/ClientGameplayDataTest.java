package net.rebornaddon.gameplay.client;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

public class ClientGameplayDataTest {
    @Before
    public void setUp() {
        ClientGameplayData.reset();
    }

    @After
    public void tearDown() {
        ClientGameplayData.reset();
    }

    @Test
    public void revisionsOnlyChangeForTheUpdatedSection() {
        int originalMounts = ClientGameplayData.revision("mounts");
        ClientGameplayData.update("mounts", "{\"available\":true}");
        int loadedMounts = ClientGameplayData.revision("mounts");

        assertNotEquals(originalMounts, loadedMounts);
        ClientGameplayData.update("luckperms", "{\"available\":true}");
        assertEquals(loadedMounts, ClientGameplayData.revision("mounts"));
    }

    @Test
    public void identicalSnapshotsDoNotTriggerAnotherRebuild() {
        ClientGameplayData.update("luckperms", "{\"available\":true,\"players\":[]}");
        int loaded = ClientGameplayData.revision("luckperms");

        ClientGameplayData.update("luckperms", "{\"players\":[],\"available\":true}");
        assertEquals(loaded, ClientGameplayData.revision("luckperms"));
    }

    @Test
    public void resetInvalidatesEverySection() {
        ClientGameplayData.update("mounts", "{\"available\":true}");
        int loaded = ClientGameplayData.revision("mounts");

        ClientGameplayData.reset();
        assertNotEquals(loaded, ClientGameplayData.revision("mounts"));
        assertEquals(0, ClientGameplayData.get("mounts").size());
    }

    @Test
    public void onlyLiveSpectatingKeepsTheFastPollingInterval() {
        assertEquals(3000L, ClientGameplayData.requestInterval("spectating"));
        assertEquals(10000L, ClientGameplayData.requestInterval("training"));
        assertEquals(60000L, ClientGameplayData.requestInterval("mounts"));
        assertEquals(60000L, ClientGameplayData.requestInterval("luckperms"));
        assertEquals(30000L, ClientGameplayData.requestInterval("marketplace"));
    }

    @Test
    public void receivingAPrewarmedSnapshotSuppressesAnImmediateDuplicateRequest() {
        ClientGameplayData.update("mounts", "{\"available\":true}");
        assertFalse(ClientGameplayData.shouldRequest("mounts"));
    }
}
