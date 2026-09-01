package net.rebornaddon.content.entity;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.rebornaddon.RebornAddonMod;

public final class NativeContentEntities {
    private NativeContentEntities() {
    }

    public static void register() {
        EntityRegistry.registerModEntity(new ResourceLocation(RebornAddonMod.MODID, "mission_npc"),
                EntityMissionNpc.class, "MissionNpc", 31, RebornAddonMod.instance,
                64, 2, true);
    }
}
