package net.rebornaddon.jutsu;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.rebornaddon.content.NativeContentService;
import net.rebornaddon.content.entity.EntityMissionNpc;

public final class NativeNpcJutsuExecutor {
    private NativeNpcJutsuExecutor() {
    }

    public static boolean execute(EntityMissionNpc npc, EntityLivingBase target,
                                  JutsuDefinition definition, float power,
                                  double damage, int durationTicks) {
        if (npc == null || target == null || definition == null) {
            return false;
        }
        if (definition.item() == null || definition.jutsu() == null
                || definition.jutsu().jutsu == null) {
            NativeContentService.INSTANCE.beginNpcJutsu(npc, damage);
            try {
                return JutsuRegistry.INSTANCE.activateExternal(definition, npc, durationTicks);
            } finally {
                NativeContentService.INSTANCE.endNpcJutsu();
            }
        }
        ItemStack stack = new ItemStack(definition.item());
        if (definition.item() instanceof net.narutomod.item.ItemJutsu.Base) {
            ((net.narutomod.item.ItemJutsu.Base) definition.item())
                    .setCurrentJutsu(stack, definition.jutsu());
        }
        NativeContentService.INSTANCE.beginNpcJutsu(npc, damage);
        try {
            return definition.jutsu().jutsu.createJutsu(stack, npc, power);
        } catch (Throwable ignored) {
            return false;
        } finally {
            NativeContentService.INSTANCE.endNpcJutsu();
        }
    }
}
