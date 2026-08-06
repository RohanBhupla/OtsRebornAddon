package net.rebornaddon.substitution;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.rebornaddon.RebornAddonMod;

public final class SubstitutionEntities {
    private static int nextEntityId;

    private SubstitutionEntities() {
    }

    public static void register() {
        EntityRegistry.registerModEntity(
                new ResourceLocation(RebornAddonMod.MODID, "substitution_decoy"),
                EntitySubstitutionDecoy.class,
                "SubstitutionDecoy",
                nextEntityId++,
                RebornAddonMod.instance,
                32,
                1,
                false);
    }
}
