package net.rebornaddon.compat;

import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry;

public final class VariedCommoditiesRecipePatch {
    public static final VariedCommoditiesRecipePatch INSTANCE = new VariedCommoditiesRecipePatch();

    private VariedCommoditiesRecipePatch() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRegisterRecipes(RegistryEvent.Register<IRecipe> event) {
        if (!Loader.isModLoaded("variedcommodities")) {
            return;
        }

        for (int i = 0; i < 5; i++) {
            remove(event.getRegistry(), new ResourceLocation("variedcommodities", "wall_banner_" + i));
            remove(event.getRegistry(), new ResourceLocation("variedcommodities", "banner_" + i));
        }
    }

    @SuppressWarnings("unchecked")
    private static void remove(IForgeRegistry<IRecipe> registry, ResourceLocation name) {
        if (!registry.containsKey(name)) {
            return;
        }

        try {
            if (registry instanceof ForgeRegistry) {
                ((ForgeRegistry<IRecipe>) registry).remove(name);
            }
        } catch (Throwable ignored) {
        }
    }
}
