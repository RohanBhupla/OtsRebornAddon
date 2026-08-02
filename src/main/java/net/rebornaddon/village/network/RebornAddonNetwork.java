package net.rebornaddon.village.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.rebornaddon.village.Village;

public final class RebornAddonNetwork {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("rebornaddon_vlg");

    private RebornAddonNetwork() {
    }

    public static void init(Side side) {
        CHANNEL.registerMessage(VillageSelectMessage.Handler.class, VillageSelectMessage.class, 0, Side.SERVER);
        CHANNEL.registerMessage(OpenVillageGuiMessageHandler.class, OpenVillageGuiMessage.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(OpenCreatorCreditsGuiMessageHandler.class, OpenCreatorCreditsGuiMessage.class, 2, Side.CLIENT);
    }

    public static void openVillageGui(EntityPlayerMP player) {
        CHANNEL.sendTo(new OpenVillageGuiMessage(), player);
    }

    public static void openCreatorCreditsGui(EntityPlayerMP player) {
        CHANNEL.sendTo(new OpenCreatorCreditsGuiMessage(), player);
    }

    public static void sendVillageSelection(Village village) {
        if (village != null) {
            CHANNEL.sendToServer(new VillageSelectMessage(village.id()));
        }
    }

}
