package net.rebornaddon.mount;

import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.util.ResourceLocation;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MountFormCatalogTest {
    @Test
    public void tailedBeastsSupportBothRolesAndUseRealNames() {
        ResourceLocation id = new ResourceLocation("narutomod", "nine_tails");
        MountFormCatalog.Profile profile = MountFormCatalog.profile(id, EntityZombie.class);
        assertTrue(profile.mount);
        assertTrue(profile.companion);
        assertEquals("Kurama", MountFormCatalog.displayName(id));
    }

    @Test
    public void jutsuEntitiesAreNotIncludedJustBecauseTheyAreLiving() {
        MountFormCatalog.Profile profile = MountFormCatalog.profile(
                new ResourceLocation("narutomod", "rasengan"), EntityZombie.class);
        assertFalse(profile.available());
    }

    @Test
    public void genuineTameablesAndHorseTypesRemainAvailable() {
        MountFormCatalog.Profile wolf = MountFormCatalog.profile(
                new ResourceLocation("example", "ninja_hound"), EntityWolf.class);
        MountFormCatalog.Profile horse = MountFormCatalog.profile(
                new ResourceLocation("example", "war_horse"), EntityHorse.class);
        assertTrue(wolf.companion);
        assertFalse(wolf.mount);
        assertTrue(horse.mount);
    }
}
