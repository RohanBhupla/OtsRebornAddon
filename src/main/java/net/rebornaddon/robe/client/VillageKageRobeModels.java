package net.rebornaddon.robe.client;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.rebornaddon.RebornAddonMod;
import net.rebornaddon.robe.VillageKageRobeItems;

@Mod.EventBusSubscriber(modid = RebornAddonMod.MODID, value = Side.CLIENT)
public final class VillageKageRobeModels {
    private VillageKageRobeModels() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void registerModels(ModelRegistryEvent event) {
        register(VillageKageRobeItems.MIZUKAGE_ROBE, "mizukage_robe");
        register(VillageKageRobeItems.RAIKAGE_ROBE, "raikage_robe");
        register(VillageKageRobeItems.TSUCHIKAGE_ROBE, "tsuchikage_robe");
        replace("narutomod:clothes_hokagebody", "clothes_hokagebody_fixed");
        replace("narutomod:clothes_kazekagebody", "clothes_kazekagebody_fixed");
        replace("narutomod:white_akatsuki_robebody", "white_akatsuki_robebody_icon");
        replace("narutomod:cosmic_akatsuki_robehelmet", "cosmic_akatsuki_robehelmet_fixed");
    }

    private static void replace(String itemId, String modelName) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
        if (item != null) {
            register(item, modelName);
        }
    }

    private static void register(Item item, String modelName) {
        ModelLoader.setCustomModelResourceLocation(item, 0,
                new ModelResourceLocation(RebornAddonMod.MODID + ":" + modelName, "inventory"));
    }
}
