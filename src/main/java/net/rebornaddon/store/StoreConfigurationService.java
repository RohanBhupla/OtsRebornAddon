package net.rebornaddon.store;

import com.mojang.authlib.GameProfile;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.rebornaddon.village.LuckPermsBridge;
import net.rebornaddon.village.SingleplayerVillageRoles;
import net.rebornaddon.village.Village;
import net.rebornaddon.village.VillageSelectionHandler;
import net.rebornaddon.village.VillageRoles;
import net.rebornaddon.integration.PluginInvalidationBus;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class StoreConfigurationService {
    public static final StoreConfigurationService INSTANCE = new StoreConfigurationService();
    private static final String LAST_CATALOG_REQUEST = "RebornAddonStoreCatalogRequest";

    private boolean catalogPublished;
    private int pollTicks;
    private long invalidationRevision;

    private StoreConfigurationService() {
    }

    public void initialize() {
        StoreCatalog.clear();
        StoreCatalog.entries();
        catalogPublished = publishCatalog();
        pollTicks = 0;
    }

    public void reset() {
        StoreSettingsCache.INSTANCE.clear();
        StorePreviewSettings.reset();
        StoreCatalog.clear();
        catalogPublished = false;
        pollTicks = 0;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        long changed = PluginInvalidationBus.revision("store");
        int interval = catalogPublished ? 1200 : 100;
        if (++pollTicks < interval && changed == invalidationRevision) return;
        pollTicks = 0;
        invalidationRevision = changed;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || server.isSinglePlayer()) {
            return;
        }
        if (!catalogPublished) {
            catalogPublished = publishCatalog();
            if (catalogPublished) broadcast(server);
            return;
        }
        String[] runtime = StorePluginBridge.runtimeOverrides();
        if (runtime == null) {
            catalogPublished = false;
            StoreSettingsCache.INSTANCE.clear();
            broadcast(server);
        } else if (StoreSettingsCache.INSTANCE.replace(runtime)) {
            broadcast(server);
        }
    }

    public void sendCatalog(final EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        if (SingleplayerVillageRoles.isEnabled(player.getServer())) {
            sendCatalog(player, SingleplayerVillageRoles.groups(player));
            return;
        }
        LuckPermsBridge.INSTANCE.findGroups(player, true, new LuckPermsBridge.GroupsLookupCallback() {
            @Override
            public void onLookup(EntityPlayerMP current, Set<String> groups) {
                sendCatalog(current, groups);
            }
        });
    }

    public void requestCatalog(EntityPlayerMP player) {
        if (player == null || player.world == null) return;
        long now = player.world.getTotalWorldTime();
        long previous = player.getEntityData().getLong(LAST_CATALOG_REQUEST);
        if (previous > 0L && now >= previous && now - previous < 20L) return;
        player.getEntityData().setLong(LAST_CATALOG_REQUEST, now);
        sendCatalog(player);
    }

    public void purchase(final EntityPlayerMP player, final String key) {
        if (player == null) {
            return;
        }
        if (SingleplayerVillageRoles.isEnabled(player.getServer())) {
            Set<String> groups = SingleplayerVillageRoles.groups(player);
            sendPurchaseResult(player, StorePurchaseService.purchase(player, key, groups, catalogPublished), groups);
            return;
        }
        LuckPermsBridge.INSTANCE.findGroups(player, true, new LuckPermsBridge.GroupsLookupCallback() {
            @Override
            public void onLookup(EntityPlayerMP current, Set<String> groups) {
                sendPurchaseResult(current,
                        StorePurchaseService.purchase(current, key, groups, catalogPublished), groups);
            }
        });
    }

    public void refreshForGroups(EntityPlayerMP player, Set<String> groups) {
        if (player != null) sendCatalog(player, groups);
    }

    public String[] update(ICommandSender sender, String key, String property, String value) {
        MinecraftServer server = sender.getServer();
        if (server != null && server.isSinglePlayer()) {
            StoreSettingsCache.INSTANCE.setPreview(key, property, value);
            broadcast(server);
            return new String[] {"true", "Single-player store preview updated. This value is not persisted.", ""};
        }
        String[] response = StorePluginBridge.update(sender, key, property, value);
        if (StorePluginBridge.successful(response)) {
            refreshRuntime();
            if (server != null) broadcast(server);
        }
        return response;
    }

    public String[] updateScope(ICommandSender sender, String key, boolean enabled) {
        MinecraftServer server = sender == null ? null : sender.getServer();
        if (StoreCatalog.findScope(key) == null) {
            return new String[] {"false", "That store section does not exist.", ""};
        }
        if (server != null && server.isSinglePlayer()) {
            StoreSettingsCache.INSTANCE.setScopePreview(key, enabled);
            broadcast(server);
            return new String[] {"true", "Single-player store section preview updated.", ""};
        }
        String[] response = StorePluginBridge.updateScope(sender, key, enabled);
        if (StorePluginBridge.successful(response)) {
            refreshRuntime();
            if (server != null) broadcast(server);
        }
        return response;
    }

    public String[] resetScope(ICommandSender sender, String key) {
        MinecraftServer server = sender == null ? null : sender.getServer();
        if (StoreCatalog.findScope(key) == null) {
            return new String[] {"false", "That store section does not exist.", ""};
        }
        if (server != null && server.isSinglePlayer()) {
            StoreSettingsCache.INSTANCE.resetScopePreview(key);
            broadcast(server);
            return new String[] {"true", "Single-player store section preview reset.", ""};
        }
        String[] response = StorePluginBridge.resetScope(sender, key);
        if (StorePluginBridge.successful(response)) {
            refreshRuntime();
            if (server != null) broadcast(server);
        }
        return response;
    }

    public StoreQuotaService.Result turnIn(EntityPlayerMP player) {
        if (player == null) return StoreQuotaService.Result.failure("A player is required.");
        net.minecraft.util.EnumHand hand = net.minecraft.util.EnumHand.MAIN_HAND;
        net.minecraft.item.ItemStack held = player.getHeldItemMainhand();
        if (held.isEmpty()) {
            hand = net.minecraft.util.EnumHand.OFF_HAND;
            held = player.getHeldItemOffhand();
        }
        if (held.isEmpty() || !held.hasTagCompound()) {
            return StoreQuotaService.Result.failure("Hold a purchased ANBU or Kage item to turn it in.");
        }
        StoreCatalog.Entry entry = StoreCatalog.find(held.getTagCompound().getString("RebornStoreOffer"));
        if (entry == null || entry.quotaKind().isEmpty() || !entry.matches(held)) {
            return StoreQuotaService.Result.failure("That item was not purchased from a limited store collection.");
        }
        if (!player.getUniqueID().toString().equals(
                held.getTagCompound().getString("RebornStoreBuyer"))) {
            return StoreQuotaService.Result.failure("Only the Kage who purchased that item can turn it in.");
        }
        StoreQuotaService.Result adjusted = StoreQuotaService.INSTANCE.adjust(player, entry.quotaKind(), -1);
        if (!adjusted.success()) return adjusted;
        held.shrink(1);
        if (held.isEmpty()) player.setHeldItem(hand, net.minecraft.item.ItemStack.EMPTY);
        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
        sendCatalog(player);
        return StoreQuotaService.Result.success("Returned " + entry.displayName()
                + ". Its purchase slot is available again.", adjusted.snapshot());
    }

    public String[] reload(ICommandSender sender) {
        String[] response = StorePluginBridge.reload(sender);
        if (StorePluginBridge.successful(response)) {
            catalogPublished = publishCatalog();
            if (sender.getServer() != null) broadcast(sender.getServer());
        }
        return response;
    }

    public String[] resetSetting(ICommandSender sender, String key, String property) {
        MinecraftServer server = sender == null ? null : sender.getServer();
        if (server != null && server.isSinglePlayer()) {
            StoreSettingsCache.INSTANCE.resetPreview(key, property);
            broadcast(server);
            return new String[] {"true", "Single-player store preview reset.", ""};
        }
        String[] response = StorePluginBridge.reset(sender, key, property);
        if (StorePluginBridge.successful(response)) {
            refreshRuntime();
            if (server != null) broadcast(server);
        }
        return response;
    }

    public String[] resetAllSettings(ICommandSender sender) {
        MinecraftServer server = sender == null ? null : sender.getServer();
        if (server != null && server.isSinglePlayer()) {
            StoreSettingsCache.INSTANCE.resetAllPreview();
            broadcast(server);
            return new String[] {"true", "All single-player store previews were reset.", ""};
        }
        String[] response = StorePluginBridge.resetAll(sender);
        if (StorePluginBridge.successful(response)) {
            refreshRuntime();
            if (server != null) broadcast(server);
        }
        return response;
    }

    public NBTTagCompound adminSnapshot(EntityPlayerMP administrator, String targetName) {
        NBTTagCompound data = new NBTTagCompound();
        if (administrator == null || !canAdmin(administrator)) {
            data.setBoolean("Available", false);
            data.setString("Message", "You do not have permission to manage the store.");
            return data;
        }
        MinecraftServer server = administrator.getServer();
        boolean available = pluginAvailable(server);
        data.setBoolean("Available", available);
        if (!available) data.setString("Message", "The hosted server store plugin is unavailable.");
        NBTTagList records = new NBTTagList();
        for (StoreCatalog.Entry entry : StoreCatalog.entries()) {
            StoreSettingsCache.Setting setting = StoreSettingsCache.INSTANCE.setting(entry);
            NBTTagCompound record = new NBTTagCompound();
            record.setString("Key", entry.key());
            record.setString("Id", entry.registryName().toString());
            record.setString("Name", entry.displayName());
            record.setString("Category", entry.category());
            record.setString("Subcategory", entry.subcategory());
            record.setInteger("Tier", entry.tier());
            record.setInteger("DefaultPrice", entry.defaultPrice());
            record.setInteger("Price", setting.price());
            record.setBoolean("Purchasable", setting.purchasable());
            records.appendTag(record);
        }
        data.setTag("Records", records);
        NBTTagList scopes = new NBTTagList();
        for (StoreCatalog.Scope scope : StoreCatalog.scopes()) {
            NBTTagCompound record = new NBTTagCompound();
            record.setString("Key", scope.key());
            record.setString("Type", scope.type());
            record.setString("Category", scope.category());
            record.setString("Subcategory", scope.subcategory());
            record.setInteger("Tier", scope.tier());
            record.setBoolean("Enabled", StoreSettingsCache.INSTANCE.scopeEnabled(scope.key()));
            scopes.appendTag(record);
        }
        data.setTag("Scopes", scopes);
        appendQuotaSnapshot(data, administrator, targetName);
        return data;
    }

    public StoreQuotaService.Result setQuota(ICommandSender sender, String playerName,
                                              String kind, int value) {
        GameProfile profile = profile(sender == null ? null : sender.getServer(), playerName);
        if (!canAdmin(sender) || profile == null || profile.getId() == null) {
            return StoreQuotaService.Result.failure("That player could not be resolved.");
        }
        return StoreQuotaService.INSTANCE.set(sender, profile.getId(), profile.getName(), kind, value);
    }

    public StoreQuotaService.Result adjustQuota(ICommandSender sender, String playerName,
                                                 String kind, int delta) {
        GameProfile profile = profile(sender == null ? null : sender.getServer(), playerName);
        if (!canAdmin(sender) || profile == null || profile.getId() == null) {
            return StoreQuotaService.Result.failure("That player could not be resolved.");
        }
        return StoreQuotaService.INSTANCE.adjust(sender, profile.getId(), profile.getName(), kind, delta);
    }

    public StoreQuotaService.Result resetQuota(ICommandSender sender, String playerName, String kind) {
        if (!"all".equals(kind)) return setQuota(sender, playerName, kind, 0);
        StoreQuotaService.Result latest = null;
        for (String quotaKind : StoreQuotaService.KINDS) {
            latest = setQuota(sender, playerName, quotaKind, 0);
            if (!latest.success()) return latest;
        }
        return latest == null ? StoreQuotaService.Result.failure("No purchase limits were reset.")
                : StoreQuotaService.Result.success("All limited purchase counts were reset.", latest.snapshot());
    }

    public boolean canAdmin(ICommandSender sender) {
        MinecraftServer server = sender == null ? null : sender.getServer();
        return sender != null && (sender.canUseCommand(2, "rebornstore")
                || server != null && !server.isSinglePlayer() && StorePluginBridge.canAdmin(sender));
    }

    public boolean pluginAvailable(MinecraftServer server) {
        return server != null && (server.isSinglePlayer() || catalogPublished && StorePluginBridge.isAvailable());
    }

    public void refreshPlayers(MinecraftServer server) {
        broadcast(server);
    }

    private boolean publishCatalog() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null && server.isSinglePlayer()) {
            StoreSettingsCache.INSTANCE.clear();
            return true;
        }
        List<StoreCatalog.Entry> entries = StoreCatalog.entries();
        String[] descriptors = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            descriptors[i] = entries.get(i).descriptor();
        }
        String[] overrides = StorePluginBridge.registerCatalog(descriptors);
        if (overrides == null) {
            StoreSettingsCache.INSTANCE.clear();
            return false;
        }
        StoreSettingsCache.INSTANCE.replace(overrides);
        return true;
    }

    private void refreshRuntime() {
        String[] runtime = StorePluginBridge.runtimeOverrides();
        if (runtime == null) {
            catalogPublished = false;
            StoreSettingsCache.INSTANCE.clear();
        } else {
            StoreSettingsCache.INSTANCE.replace(runtime);
            catalogPublished = true;
        }
    }

    private void sendCatalog(EntityPlayerMP player, Set<String> groups) {
        MinecraftServer server = player.getServer();
        boolean operator = player.canUseCommand(2, "rebornstore");
        boolean buildingLocked = StorePreviewSettings.buildingLocked(server, operator);
        boolean serviceAvailable = pluginAvailable(server);
        Set<String> safeGroups = groups == null ? Collections.<String>emptySet() : new HashSet<String>(groups);
        Village village = VillageRoles.villageForGroups(safeGroups);
        VillageSelectionHandler.INSTANCE.reconcileVillageFromGroups(player, safeGroups);
        boolean kage = village != null && VillageRoles.isKage(safeGroups, village);
        StoreQuotaService.Snapshot quotas = StoreQuotaService.INSTANCE.snapshot(player);

        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("Revision", StoreSettingsCache.INSTANCE.revision());
        root.setBoolean("BuildingLocked", buildingLocked);
        root.setBoolean("ServiceAvailable", serviceAvailable);
        root.setBoolean("Preview", server != null && server.isSinglePlayer());
        root.setString("Village", village == null ? "" : village.group());
        root.setBoolean("Kage", kage);
        root.setInteger("AnbuMasks", quotas.count(StoreCatalog.QUOTA_ANBU_MASK));
        root.setInteger("AnbuCloaks", quotas.count(StoreCatalog.QUOTA_ANBU_CLOAK));
        root.setInteger("KageHat", quotas.count(StoreCatalog.QUOTA_KAGE_HAT));
        root.setInteger("KageRobe", quotas.count(StoreCatalog.QUOTA_KAGE_ROBE));
        root.setInteger("AkatsukiHeadband", quotas.count(StoreCatalog.QUOTA_AKATSUKI_HEADBAND));
        root.setInteger("AkatsukiHat", quotas.count(StoreCatalog.QUOTA_AKATSUKI_HAT));
        root.setInteger("AkatsukiRobe", quotas.count(StoreCatalog.QUOTA_AKATSUKI_ROBE));
        NBTTagList scopeTags = new NBTTagList();
        for (StoreCatalog.Scope scope : StoreCatalog.scopes()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("Key", scope.key());
            tag.setBoolean("Enabled", StoreSettingsCache.INSTANCE.scopeEnabled(scope.key()));
            scopeTags.appendTag(tag);
        }
        root.setTag("Scopes", scopeTags);
        NBTTagList records = new NBTTagList();
        for (StoreCatalog.Entry entry : StoreCatalog.entries()) {
            if (!StoreCatalog.allowedFor(entry, village, safeGroups)) {
                continue;
            }
            if (StoreCatalog.BUILDING.equals(entry.category()) && buildingLocked) {
                continue;
            }
            if (!StoreCatalog.routeEnabled(entry)) {
                continue;
            }
            StoreSettingsCache.Setting setting = StoreSettingsCache.INSTANCE.setting(entry);
            NBTTagCompound record = new NBTTagCompound();
            record.setString("Key", entry.key());
            record.setString("Id", entry.registryName().toString());
            record.setInteger("Meta", entry.metadata());
            record.setString("Category", entry.category());
            record.setString("Subcategory", entry.subcategory());
            record.setInteger("Tier", entry.tier());
            record.setString("Quota", entry.quotaKind());
            record.setInteger("Price", setting.price());
            record.setBoolean("Purchasable", serviceAvailable && setting.purchasable()
                    && quotas.canPurchase(entry.quotaKind()));
            records.appendTag(record);
        }
        root.setTag("Entries", records);
        RebornAddonNetwork.sendStoreCatalog(player, root);
    }

    private static void appendQuotaSnapshot(NBTTagCompound data, EntityPlayerMP administrator,
                                            String targetName) {
        MinecraftServer server = administrator.getServer();
        GameProfile profile = profile(server, targetName);
        if (profile == null || profile.getId() == null) {
            data.setString("QuotaMessage", targetName == null || targetName.trim().isEmpty()
                    ? "Enter a player name to manage purchase limits."
                    : "That player could not be resolved.");
            return;
        }
        StoreQuotaService.Snapshot snapshot = StoreQuotaService.INSTANCE.snapshot(
                server, profile.getId(), profile.getName());
        data.setString("QuotaPlayer", profile.getName());
        data.setString("QuotaUuid", profile.getId().toString());
        data.setBoolean("QuotaAvailable", snapshot.available());
        data.setString("QuotaMessage", snapshot.message());
        for (String kind : StoreQuotaService.KINDS) {
            data.setInteger("Quota_" + kind, snapshot.count(kind));
            data.setInteger("Limit_" + kind, StoreQuotaService.limit(kind));
        }
    }

    private static GameProfile profile(MinecraftServer server, String playerName) {
        if (server == null || playerName == null || playerName.trim().isEmpty()) return null;
        EntityPlayerMP online = server.getPlayerList().getPlayerByUsername(playerName.trim());
        return online != null ? online.getGameProfile()
                : server.getPlayerProfileCache().getGameProfileForUsername(playerName.trim());
    }

    private void broadcast(MinecraftServer server) {
        if (server == null) return;
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            sendCatalog(player);
        }
    }

    private static void sendPurchaseResult(EntityPlayerMP player, StorePurchaseService.Result result,
                                           Set<String> groups) {
        RebornAddonNetwork.sendStorePurchaseResult(player, result);
        INSTANCE.sendCatalog(player, groups);
    }
}
