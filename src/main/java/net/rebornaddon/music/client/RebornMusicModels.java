package net.rebornaddon.music.client;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.rebornaddon.RebornAddonMod;
import net.rebornaddon.music.RebornMusic;

@Mod.EventBusSubscriber(modid = RebornAddonMod.MODID, value = Side.CLIENT)
public final class RebornMusicModels {

    private RebornMusicModels() {
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        for (RebornMusic.Track track : RebornMusic.TRACKS) {
            ModelLoader.setCustomModelResourceLocation(track.record, 0,
                    new ModelResourceLocation(track.record.getRegistryName(), "inventory"));
        }
    }
}
