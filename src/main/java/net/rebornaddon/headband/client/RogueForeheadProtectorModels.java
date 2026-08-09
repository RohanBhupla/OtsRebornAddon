package net.rebornaddon.headband.client;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.rebornaddon.RebornAddonMod;
import net.rebornaddon.headband.RogueForeheadProtectorItems;

@Mod.EventBusSubscriber(modid = RebornAddonMod.MODID, value = Side.CLIENT)
public final class RogueForeheadProtectorModels {
    private RogueForeheadProtectorModels() {
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        register(RogueForeheadProtectorItems.LEAF, "rogue_leaf_forehead_protector");
        register(RogueForeheadProtectorItems.LEAF_WRAPPED, "rogue_leaf_wrapped_forehead_protector");
        register(RogueForeheadProtectorItems.RAIN, "rogue_rain_forehead_protector");
        register(RogueForeheadProtectorItems.STONE, "rogue_stone_forehead_protector");
        register(RogueForeheadProtectorItems.MIST, "rogue_mist_forehead_protector");
        register(RogueForeheadProtectorItems.CLOUD, "rogue_cloud_forehead_protector");
        register(RogueForeheadProtectorItems.SAND, "rogue_sand_forehead_protector");
        register(RogueForeheadProtectorItems.SOUND, "rogue_sound_forehead_protector");
        register(RogueForeheadProtectorItems.KABUTO_LEAF, "kabuto_rogue_leaf_headband");
        register(RogueForeheadProtectorItems.KABUTO_RAIN, "kabuto_rogue_rain_headband");
        register(RogueForeheadProtectorItems.KABUTO_SAND, "kabuto_rogue_sand_headband");
        register(RogueForeheadProtectorItems.KABUTO_CLOUD, "kabuto_rogue_cloud_headband");
        register(RogueForeheadProtectorItems.KABUTO_STONE, "kabuto_rogue_stone_headband");
        register(RogueForeheadProtectorItems.KABUTO_SOUND, "kabuto_rogue_sound_headband");
    }

    private static void register(Item item, String name) {
        ModelLoader.setCustomModelResourceLocation(item, 0,
                new ModelResourceLocation(RebornAddonMod.MODID + ":" + name, "inventory"));
    }
}
