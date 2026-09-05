package net.rebornaddon.advancement.client;

import net.minecraft.util.ResourceLocation;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class AdvancementClientGuardTest {
    @Test
    public void rebuildsRebornRootsInCommissionedOrderOnEveryOpen() {
        ResourceLocation external = new ResourceLocation("example", "root");
        assertEquals(Arrays.asList(
                new ResourceLocation("rebornaddon", "ninja"),
                new ResourceLocation("rebornaddon", "natures"),
                new ResourceLocation("rebornaddon", "kekkei_genkai"),
                new ResourceLocation("rebornaddon", "clan"),
                new ResourceLocation("rebornaddon", "modes"),
                new ResourceLocation("rebornaddon", "store"),
                external), AdvancementClientGuard.orderedRootIds(Arrays.asList(
                new ResourceLocation("rebornaddon", "store"),
                new ResourceLocation("rebornaddon", "modes"),
                external,
                new ResourceLocation("rebornaddon", "clan"),
                new ResourceLocation("rebornaddon", "kekkei_genkai"),
                new ResourceLocation("rebornaddon", "natures"),
                new ResourceLocation("rebornaddon", "ninja"))));
    }
}
