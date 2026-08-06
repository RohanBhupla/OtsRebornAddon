package net.rebornaddon.village.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.rebornaddon.substitution.SubstitutionUseMessage;
import net.rebornaddon.substitution.SubstitutionEffectMessage;
import net.rebornaddon.village.Village;

public final class RebornAddonNetwork {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("rebornaddon_vlg");

    private RebornAddonNetwork() {
    }

    public static void init(Side side) {
        CHANNEL.registerMessage(VillageSelectMessage.Handler.class, VillageSelectMessage.class, 0, Side.SERVER);
        CHANNEL.registerMessage(OpenVillageGuiMessageHandler.class, OpenVillageGuiMessage.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(OpenCreatorCreditsGuiMessageHandler.class, OpenCreatorCreditsGuiMessage.class, 2, Side.CLIENT);
        CHANNEL.registerMessage(SubstitutionUseMessage.Handler.class, SubstitutionUseMessage.class, 3, Side.SERVER);
        CHANNEL.registerMessage(SubstitutionEffectMessage.Handler.class, SubstitutionEffectMessage.class, 4, Side.CLIENT);
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

    public static void sendSubstitutionUse() {
        CHANNEL.sendToServer(new SubstitutionUseMessage());
    }

    public static void sendSubstitutionEffect(int dimension, Village village, int phase,
                                              double x, double y, double z) {
        if (village == null) {
            return;
        }
        CHANNEL.sendToAllAround(new SubstitutionEffectMessage(village.id(), phase, x, y, z),
                new NetworkRegistry.TargetPoint(dimension, x, y, z, 48.0D));
    }

}
