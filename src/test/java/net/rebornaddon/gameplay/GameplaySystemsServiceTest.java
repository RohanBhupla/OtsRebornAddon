package net.rebornaddon.gameplay;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GameplaySystemsServiceTest {
    @Test
    public void recognizesTemporaryPermissionRefreshResponses() {
        assertTrue(GameplaySystemsService.isRefreshingSnapshot(
                "{\"available\":false,\"message\":\"Mount permissions are refreshing. Please try again.\"}"));
        assertTrue(GameplaySystemsService.isRefreshingSnapshot(
                "{\"available\":false,\"message\":\"Loading private LuckPerms data...\"}"));
        assertFalse(GameplaySystemsService.isRefreshingSnapshot(
                "{\"available\":true,\"message\":\"Ready\",\"players\":[]}"));
    }

    @Test
    public void expensiveAdminCatalogsUseTheLongerCache() {
        assertEquals(60000L, GameplaySystemsService.cacheDuration("mounts"));
        assertEquals(60000L, GameplaySystemsService.cacheDuration("luckperms"));
        assertEquals(60000L, GameplaySystemsService.cacheDuration("moderation"));
        assertEquals(10000L, GameplaySystemsService.cacheDuration("missions"));
    }
}
