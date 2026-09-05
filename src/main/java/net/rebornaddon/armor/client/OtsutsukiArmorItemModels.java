package net.rebornaddon.armor.client;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.rebornaddon.RebornAddonMod;
import net.rebornaddon.armor.ItemOtsutsukiCosmeticArmor;
import net.rebornaddon.armor.OtsutsukiArmorItems;

@Mod.EventBusSubscriber(modid = RebornAddonMod.MODID, value = Side.CLIENT)
public final class OtsutsukiArmorItemModels {
    private OtsutsukiArmorItemModels() {
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        for (ItemOtsutsukiCosmeticArmor item : OtsutsukiArmorItems.all()) {
            ModelLoader.setCustomModelResourceLocation(item, 0,
                    new ModelResourceLocation(item.getRegistryName(), "inventory"));
        }
    }
}
