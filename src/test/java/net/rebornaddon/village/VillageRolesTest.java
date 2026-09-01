package net.rebornaddon.village;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class VillageRolesTest {
    @Test
    public void resolvesOrdinaryVillageMembership() {
        assertEquals(Village.LEAF, VillageRoles.villageForGroups(Collections.singleton("leaf")));
        assertNull(VillageRoles.villageForGroups(Collections.<String>emptySet()));
    }

    @Test
    public void leadershipRoleTakesPriorityOverOrdinaryVillageGroup() {
        assertEquals(Village.MIST,
                VillageRoles.villageForGroups(Arrays.asList("leaf", "mizukage")));
        assertEquals(Village.STONE,
                VillageRoles.villageForGroups(Arrays.asList("leaf", "stoneadvisor")));
    }

    @Test
    public void anbuRoleTakesPriorityOverOrdinaryVillageGroup() {
        assertEquals(Village.CLOUD,
                VillageRoles.villageForGroups(Arrays.asList("leaf", "cloudcaptain")));
        assertEquals(Village.SAND,
                VillageRoles.villageForGroups(Arrays.asList("leaf", "sandanbu")));
    }
}
