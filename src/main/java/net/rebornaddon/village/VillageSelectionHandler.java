package net.rebornaddon.village;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.rebornaddon.compat.MinecraftAccess;
import net.rebornaddon.discord.DiscordPluginBridge;
import net.rebornaddon.store.StoreConfigurationService;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VillageSelectionHandler {
    public static final VillageSelectionHandler INSTANCE = new VillageSelectionHandler();

    private static final String SELECTED_KEY = "RebornAddonVillageSelected";
    private static final String VILLAGE_KEY = "RebornAddonVillage";
    private static final String DISCORD_SKIPPED_KEY = "RebornAddonDiscordPromptSkipped";
    private static final String DISCORD_REQUEST_TICK_KEY = "RebornAddonDiscordPromptRequestTick";
    private static final int ROLE_SYNC_INTERVAL_TICKS = 100;
    private final Set<UUID> pendingSelections = new HashSet<UUID>();
    private final Set<UUID> pendingRoleChecks = new HashSet<UUID>();
    private final Map<UUID, Set<String>> observedRoleGroups = new HashMap<UUID, Set<String>>();

    private VillageSelectionHandler() {
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP) || MinecraftAccess.isRemote(event.player)) {
            return;
        }

        final EntityPlayerMP player = (EntityPlayerMP) event.player;
        pendingSelections.remove(player.getUniqueID());
        pendingRoleChecks.remove(player.getUniqueID());
        observedRoleGroups.remove(player.getUniqueID());
        final boolean rememberedVillage = hasSelectedVillage(player);
        if (!LuckPermsBridge.INSTANCE.isAvailable(FMLCommonHandler.instance().getMinecraftServerInstance())) {
            if (rememberedVillage) {
                openDiscordPromptIfNeeded(player, false);
            }
            return;
        }

        LuckPermsBridge.INSTANCE.invalidateUser(player.getUniqueID());
        LuckPermsBridge.INSTANCE.findGroups(player, true, new LuckPermsBridge.GroupsLookupCallback() {
            @Override
            public void onLookup(EntityPlayerMP current, Set<String> groups) {
                synchronizeRoleState(current, groups, false);
                Village village = VillageRoles.villageForGroups(groups);
                if (village != null) {
                    openDiscordPromptIfNeeded(current, false);
                } else {
                    RebornAddonNetwork.openVillageGui(current);
                }
            }
        });
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)
                || MinecraftAccess.isRemote(event.player) || event.player.world == null) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        long tick = player.world.getTotalWorldTime();
        int offset = Math.floorMod(player.getUniqueID().hashCode(), ROLE_SYNC_INTERVAL_TICKS);
        if (Math.floorMod(tick, ROLE_SYNC_INTERVAL_TICKS) != offset
                || !LuckPermsBridge.INSTANCE.isAvailable(player.getServer())
                || !pendingRoleChecks.add(player.getUniqueID())) {
            return;
        }
        LuckPermsBridge.INSTANCE.findGroups(player, true, new LuckPermsBridge.GroupsLookupCallback() {
            @Override
            public void onLookup(EntityPlayerMP current, Set<String> groups) {
                pendingRoleChecks.remove(current.getUniqueID());
                synchronizeRoleState(current, groups, true);
            }
        });
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.player == null ? null : event.player.getUniqueID();
        if (id != null) {
            pendingSelections.remove(id);
            pendingRoleChecks.remove(id);
            observedRoleGroups.remove(id);
            LuckPermsBridge.INSTANCE.invalidateUser(id);
        }
    }

    public void handleSelection(EntityPlayerMP player, int villageId) {
        Village village = Village.byId(villageId);
        if (player == null || village == null) {
            return;
        }

        if (isVillageSelected(player)) {
            player.sendMessage(new TextComponentString("Village already selected: " + persisted(player).getString(VILLAGE_KEY)));
            return;
        }
        if (!pendingSelections.add(player.getUniqueID())) {
            player.sendMessage(new TextComponentString("Village selection is already being saved."));
            return;
        }

        LuckPermsBridge.INSTANCE.applyVillageAsync(player, village, new LuckPermsBridge.ChangeCallback() {
            @Override
            public void onComplete(boolean success) {
                pendingSelections.remove(player.getUniqueID());
                if (!success) {
                    player.sendMessage(new TextComponentString(
                            "Village selection could not be saved. Ask staff to check LuckPerms."));
                    RebornAddonNetwork.openVillageGui(player);
                    return;
                }
                rememberVillage(player, village);
                player.sendMessage(new TextComponentString("Village selected: " + village.displayName()));
                openDiscordPromptIfNeeded(player, false);
                LuckPermsBridge.INSTANCE.findGroups(player, true, new LuckPermsBridge.GroupsLookupCallback() {
                    @Override
                    public void onLookup(EntityPlayerMP current, Set<String> groups) {
                        synchronizeRoleState(current, groups, true);
                    }
                });
            }
        });
    }

    public void resetSelection(EntityPlayerMP player, boolean clearLuckPerms, boolean reopenGui) {
        if (player == null) {
            return;
        }

        clearRememberedVillage(player);
        pendingSelections.remove(player.getUniqueID());

        if (clearLuckPerms) {
            LuckPermsBridge.INSTANCE.clearVillageAsync(player, null);
        }

        if (reopenGui && LuckPermsBridge.INSTANCE.isAvailable(FMLCommonHandler.instance().getMinecraftServerInstance())) {
            RebornAddonNetwork.openVillageGui(player);
        }
    }

    public void assignLocalVillage(EntityPlayerMP player, Village village) {
        rememberVillage(player, village);
    }

    public Village getAssignedVillage(EntityPlayerMP player) {
        if (player == null) {
            return null;
        }
        Village persistedVillage = Village.byGroup(persisted(player).getString(VILLAGE_KEY));
        return persistedVillage != null ? persistedVillage : LuckPermsBridge.INSTANCE.findKnownVillage(player);
    }

    public boolean hasSelectedVillage(EntityPlayerMP player) {
        return player != null && isVillageSelected(player);
    }

    public void reconcileVillageFromGroups(EntityPlayerMP player, Set<String> groups) {
        Village village = VillageRoles.villageForGroups(groups);
        if (village == null) clearRememberedVillage(player);
        else rememberVillage(player, village);
    }

    public void requestDiscordPrompt(EntityPlayerMP player) {
        if (player == null || player.world == null) return;
        long now = player.world.getTotalWorldTime();
        long previous = player.getEntityData().getLong(DISCORD_REQUEST_TICK_KEY);
        if (previous > 0L && now >= previous && now - previous < 20L) return;
        player.getEntityData().setLong(DISCORD_REQUEST_TICK_KEY, now);
        openDiscordPromptIfNeeded(player, true);
    }

    public void markDiscordPromptSkipped(EntityPlayerMP player, boolean skipped) {
        if (player != null) {
            persisted(player).setBoolean(DISCORD_SKIPPED_KEY, skipped);
        }
    }

    private void openDiscordPromptIfNeeded(EntityPlayerMP player, boolean manual) {
        if (player == null || !manual && !hasSelectedVillage(player)) {
            return;
        }
        if (!manual && persisted(player).getBoolean(DISCORD_SKIPPED_KEY)) {
            return;
        }
        if (!DiscordPluginBridge.isAvailable()) {
            if (manual) {
                player.sendStatusMessage(new TextComponentString(TextFormatting.RED
                        + DiscordPluginBridge.unavailableReason()), true);
            }
            return;
        }
        DiscordPluginBridge.LinkStatus status = DiscordPluginBridge.linkStatus(player);
        if (status.available() && (manual || !status.linked())) {
            RebornAddonNetwork.openDiscordLinkPrompt(player, status.linked());
        } else if (manual && !status.available()) {
            player.sendStatusMessage(new TextComponentString(TextFormatting.RED
                    + (status.message().isEmpty() ? DiscordPluginBridge.unavailableReason()
                    : status.message())), true);
        }
    }

    private static boolean isVillageSelected(EntityPlayer player) {
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

    private static void clearRememberedVillage(EntityPlayer player) {
        NBTTagCompound data = persisted(player);
        data.removeTag(SELECTED_KEY);
        data.removeTag(VILLAGE_KEY);
    }

    private void synchronizeRoleState(EntityPlayerMP player, Set<String> groups, boolean notifyChanges) {
        if (player == null) return;
        Set<String> current = relevantRoleGroups(groups);
        Set<String> previous = observedRoleGroups.put(player.getUniqueID(), current);
        Village before = Village.byGroup(persisted(player).getString(VILLAGE_KEY));
        Village after = VillageRoles.villageForGroups(current);
        if (after == null) clearRememberedVillage(player);
        else rememberVillage(player, after);
        if (!notifyChanges || previous == null || previous.equals(current)) return;

        Set<String> changed = new HashSet<String>(previous);
        changed.addAll(current);
        Set<String> unchanged = new HashSet<String>(previous);
        unchanged.retainAll(current);
        changed.removeAll(unchanged);
        for (String group : changed) LuckPermsBridge.INSTANCE.invalidateGroup(group);

        StoreConfigurationService.INSTANCE.refreshForGroups(player, current);
        VillageLeadershipService.INSTANCE.refreshRoleAccess(player, current);
        if (before != null && after == null) {
            RebornAddonNetwork.openVillageGui(player);
        }
    }

    private static Set<String> relevantRoleGroups(Set<String> groups) {
        if (groups == null || groups.isEmpty()) return Collections.emptySet();
        Set<String> relevant = new HashSet<String>();
        for (String value : groups) {
            String group = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            if ("akatsuki".equals(group) || isVillageRole(group)) relevant.add(group);
        }
        return Collections.unmodifiableSet(relevant);
    }

    private static boolean isVillageRole(String group) {
        for (Village village : Village.all()) {
            if (village.group().equals(group) || VillageRoles.kage(village).equals(group)
                    || VillageRoles.advisor(village).equals(group)
                    || VillageRoles.anbu(village).equals(group)
                    || VillageRoles.captain(village).equals(group)
                    || VillageRoles.legacyCaptain(village).equals(group)) return true;
        }
        return false;
    }

    private static NBTTagCompound persisted(EntityPlayer player) {
        NBTTagCompound entityData = player.getEntityData();
        if (!entityData.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            entityData.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }
}
