package net.rebornaddon.content.client;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.rebornaddon.RebornAddonMod;
import net.rebornaddon.content.NativeContentObjects;

@Mod.EventBusSubscriber(modid = RebornAddonMod.MODID, value = Side.CLIENT)
public final class NativeContentModels {
    private NativeContentModels() {
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(NativeContentObjects.MAILBOX_ITEM, 0,
                new ModelResourceLocation("rebornaddon:mailbox", "inventory"));
        ModelLoader.setCustomModelResourceLocation(NativeContentObjects.PATH_TOOL, 0,
                new ModelResourceLocation("rebornaddon:npc_path_tool", "inventory"));
    }
}
