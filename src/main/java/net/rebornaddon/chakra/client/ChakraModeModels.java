package net.rebornaddon.chakra.client;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.rebornaddon.RebornAddonMod;
import net.rebornaddon.chakra.ChakraModeItems;

@Mod.EventBusSubscriber(modid = RebornAddonMod.MODID, value = Side.CLIENT)
public final class ChakraModeModels {
    private ChakraModeModels() {
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        register(ChakraModeItems.WATER_SCROLL);
        register(ChakraModeItems.RAIN_SCROLL);
        register(ChakraModeItems.EARTH_SCROLL);
        register(ChakraModeItems.WATER_MODE);
        register(ChakraModeItems.RAIN_MODE);
        register(ChakraModeItems.EARTH_MODE);
    }

    private static void register(Item item) {
        ModelLoader.setCustomModelResourceLocation(item, 0,
                new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }
}
