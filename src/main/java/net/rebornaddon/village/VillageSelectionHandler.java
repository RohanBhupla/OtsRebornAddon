package net.rebornaddon.village;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.rebornaddon.compat.MinecraftAccess;
import net.rebornaddon.village.network.RebornAddonNetwork;

public final class VillageSelectionHandler {
    public static final VillageSelectionHandler INSTANCE = new VillageSelectionHandler();

    private static final String SELECTED_KEY = "RebornAddonVillageSelected";
    private static final String VILLAGE_KEY = "RebornAddonVillage";

    private VillageSelectionHandler() {
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP) || MinecraftAccess.isRemote(event.player)) {
            return;
        }

        final EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (hasSelectedVillage(player)) {
            return;
        }

        if (!LuckPermsBridge.INSTANCE.isAvailable(FMLCommonHandler.instance().getMinecraftServerInstance())) {
            return;
        }

        RebornAddonNetwork.openVillageGui(player);
    }

    public void handleSelection(EntityPlayerMP player, int villageId) {
        Village village = Village.byId(villageId);
        if (player == null || village == null) {
            return;
        }

        if (hasSelectedVillage(player)) {
            player.sendMessage(new TextComponentString("Village already selected: " + persisted(player).getString(VILLAGE_KEY)));
            return;
        }

        if (!LuckPermsBridge.INSTANCE.applyVillage(player, village)) {
            player.sendMessage(new TextComponentString("Village selection could not be saved. Ask staff to check LuckPerms."));
            RebornAddonNetwork.openVillageGui(player);
            return;
        }

        rememberVillage(player, village);
        player.sendMessage(new TextComponentString("Village selected: " + village.displayName()));
    }

    public void resetSelection(EntityPlayerMP player, boolean clearLuckPerms, boolean reopenGui) {
        if (player == null) {
            return;
        }

        NBTTagCompound data = persisted(player);
        data.removeTag(SELECTED_KEY);
        data.removeTag(VILLAGE_KEY);

        if (clearLuckPerms) {
            LuckPermsBridge.INSTANCE.clearVillage(player);
        }

        if (reopenGui && LuckPermsBridge.INSTANCE.isAvailable(FMLCommonHandler.instance().getMinecraftServerInstance())) {
            RebornAddonNetwork.openVillageGui(player);
        }
    }

    public void assignLocalVillage(EntityPlayerMP player, Village village) {
        rememberVillage(player, village);
    }

    private static boolean hasSelectedVillage(EntityPlayer player) {
        return persisted(player).getBoolean(SELECTED_KEY);
    }

    private static void rememberVillage(EntityPlayer player, Village village) {
        if (player == null || village == null) {
            return;
        }

        NBTTagCompound data = persisted(player);
        data.setBoolean(SELECTED_KEY, true);
        data.setString(VILLAGE_KEY, village.group());
    }

    private static NBTTagCompound persisted(EntityPlayer player) {
        NBTTagCompound entityData = player.getEntityData();
        if (!entityData.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            entityData.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }
}
