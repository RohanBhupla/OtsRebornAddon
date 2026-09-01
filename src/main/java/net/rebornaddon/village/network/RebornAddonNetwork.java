package net.rebornaddon.village.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraft.nbt.NBTTagCompound;
import net.rebornaddon.quest.network.QuestRequestMessage;
import net.rebornaddon.quest.network.QuestClaimMessage;
import net.rebornaddon.quest.network.QuestSyncMessage;
import net.rebornaddon.substitution.SubstitutionUseMessage;
import net.rebornaddon.substitution.SubstitutionEffectMessage;
import net.rebornaddon.village.Village;
import net.rebornaddon.chakra.ChakraMode;
import net.rebornaddon.chakra.network.ChakraLearnMessage;
import net.rebornaddon.chakra.network.ChakraModeEffectMessage;
import net.rebornaddon.chakra.network.ChakraControlToggleMessage;
import net.rebornaddon.chakra.network.ChakraControlStateMessage;
import net.rebornaddon.jutsu.network.JutsuAdminActionMessage;
import net.rebornaddon.jutsu.network.JutsuAdminSnapshotMessage;
import net.rebornaddon.jutsu.network.AdminAccessRequestMessage;
import net.rebornaddon.jutsu.network.AdminAccessSyncMessage;
import net.rebornaddon.store.StorePurchaseService;
import net.rebornaddon.store.network.StorePurchaseMessage;
import net.rebornaddon.store.network.StorePurchaseResultMessage;
import net.rebornaddon.store.network.StoreCatalogRequestMessage;
import net.rebornaddon.store.network.StoreCatalogSyncMessage;
import net.rebornaddon.trade.network.TradeActionMessage;
import net.rebornaddon.trade.network.TradeSyncMessage;
import net.rebornaddon.content.network.NativeContentActionMessage;
import net.rebornaddon.content.network.NativeContentSyncMessage;
import net.rebornaddon.exam.network.ExamActionMessage;
import net.rebornaddon.exam.network.ExamSyncMessage;
import net.rebornaddon.performance.PerformanceMonitor;
import net.rebornaddon.performance.network.PerformanceRequestMessage;
import net.rebornaddon.performance.network.PerformanceSnapshotMessage;
import net.rebornaddon.performance.network.PerformanceOverlayMessage;
import net.rebornaddon.diagnostic.network.CompatibilityRequestMessage;
import net.rebornaddon.diagnostic.network.CompatibilityReportMessage;
import net.rebornaddon.policy.network.ContentPolicySyncMessage;
import net.rebornaddon.content.network.NpcSkinRequestMessage;
import net.rebornaddon.content.network.NpcSkinDataMessage;
import net.rebornaddon.gameplay.network.GameplayActionMessage;
import net.rebornaddon.gameplay.network.GameplaySnapshotMessage;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public final class RebornAddonNetwork {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("rebornaddon_vlg");
    private static final int NATIVE_CONTENT_FRAGMENT_BYTES = 24576;
    private static final AtomicInteger NATIVE_CONTENT_TRANSFER_ID = new AtomicInteger();
    private static final AtomicInteger EXAM_TRANSFER_ID = new AtomicInteger();
    private static final AtomicInteger NPC_SKIN_TRANSFER_ID = new AtomicInteger();

    private RebornAddonNetwork() {
    }

    public static void init(Side side) {
        CHANNEL.registerMessage(VillageSelectMessage.Handler.class, VillageSelectMessage.class, 0, Side.SERVER);
        CHANNEL.registerMessage(OpenVillageGuiMessageHandler.class, OpenVillageGuiMessage.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(OpenCreatorCreditsGuiMessageHandler.class, OpenCreatorCreditsGuiMessage.class, 2, Side.CLIENT);
        CHANNEL.registerMessage(SubstitutionUseMessage.Handler.class, SubstitutionUseMessage.class, 3, Side.SERVER);
        CHANNEL.registerMessage(SubstitutionEffectMessage.Handler.class, SubstitutionEffectMessage.class, 4, Side.CLIENT);
        CHANNEL.registerMessage(QuestRequestMessage.Handler.class, QuestRequestMessage.class, 5, Side.SERVER);
        CHANNEL.registerMessage(QuestSyncMessage.Handler.class, QuestSyncMessage.class, 6, Side.CLIENT);
        CHANNEL.registerMessage(ChakraLearnMessage.Handler.class, ChakraLearnMessage.class, 7, Side.SERVER);
        CHANNEL.registerMessage(ChakraModeEffectMessage.Handler.class, ChakraModeEffectMessage.class, 8, Side.CLIENT);
        CHANNEL.registerMessage(QuestClaimMessage.Handler.class, QuestClaimMessage.class, 9, Side.SERVER);
        CHANNEL.registerMessage(VillageLeadershipRequestMessage.Handler.class,
                VillageLeadershipRequestMessage.class, 10, Side.SERVER);
        CHANNEL.registerMessage(VillageLeadershipSyncMessage.Handler.class,
                VillageLeadershipSyncMessage.class, 11, Side.CLIENT);
        CHANNEL.registerMessage(VillageLeadershipActionMessage.Handler.class,
                VillageLeadershipActionMessage.class, 12, Side.SERVER);
        CHANNEL.registerMessage(OpenDiscordLinkPromptMessageHandler.class,
                OpenDiscordLinkPromptMessage.class, 13, Side.CLIENT);
        CHANNEL.registerMessage(DiscordLinkPromptChoiceMessage.Handler.class,
                DiscordLinkPromptChoiceMessage.class, 14, Side.SERVER);
        CHANNEL.registerMessage(OpenDiscordLinkCodeMessageHandler.class,
                OpenDiscordLinkCodeMessage.class, 15, Side.CLIENT);
        CHANNEL.registerMessage(JutsuAdminSnapshotMessage.Handler.class,
                JutsuAdminSnapshotMessage.class, 16, Side.CLIENT);
        CHANNEL.registerMessage(JutsuAdminActionMessage.Handler.class,
                JutsuAdminActionMessage.class, 17, Side.SERVER);
        CHANNEL.registerMessage(StorePurchaseMessage.Handler.class,
                StorePurchaseMessage.class, 18, Side.SERVER);
        CHANNEL.registerMessage(StorePurchaseResultMessage.Handler.class,
                StorePurchaseResultMessage.class, 19, Side.CLIENT);
        CHANNEL.registerMessage(StoreCatalogRequestMessage.Handler.class,
                StoreCatalogRequestMessage.class, 20, Side.SERVER);
        CHANNEL.registerMessage(StoreCatalogSyncMessage.Handler.class,
                StoreCatalogSyncMessage.class, 21, Side.CLIENT);
        CHANNEL.registerMessage(DiscordLinkPromptRequestMessage.Handler.class,
                DiscordLinkPromptRequestMessage.class, 22, Side.SERVER);
        CHANNEL.registerMessage(AdminAccessRequestMessage.Handler.class,
                AdminAccessRequestMessage.class, 23, Side.SERVER);
        CHANNEL.registerMessage(AdminAccessSyncMessage.Handler.class,
                AdminAccessSyncMessage.class, 24, Side.CLIENT);
        CHANNEL.registerMessage(TradeActionMessage.Handler.class,
                TradeActionMessage.class, 25, Side.SERVER);
        CHANNEL.registerMessage(TradeSyncMessage.Handler.class,
                TradeSyncMessage.class, 26, Side.CLIENT);
        CHANNEL.registerMessage(NativeContentActionMessage.Handler.class,
                NativeContentActionMessage.class, 27, Side.SERVER);
        CHANNEL.registerMessage(NativeContentSyncMessage.Handler.class,
                NativeContentSyncMessage.class, 28, Side.CLIENT);
        CHANNEL.registerMessage(ExamActionMessage.Handler.class,
                ExamActionMessage.class, 29, Side.SERVER);
        CHANNEL.registerMessage(ExamSyncMessage.Handler.class,
                ExamSyncMessage.class, 30, Side.CLIENT);
        CHANNEL.registerMessage(PerformanceRequestMessage.Handler.class,
                PerformanceRequestMessage.class, 31, Side.SERVER);
        CHANNEL.registerMessage(PerformanceSnapshotMessage.Handler.class,
                PerformanceSnapshotMessage.class, 32, Side.CLIENT);
        CHANNEL.registerMessage(CompatibilityRequestMessage.Handler.class,
                CompatibilityRequestMessage.class, 33, Side.CLIENT);
        CHANNEL.registerMessage(CompatibilityReportMessage.Handler.class,
                CompatibilityReportMessage.class, 34, Side.SERVER);
        CHANNEL.registerMessage(ContentPolicySyncMessage.Handler.class,
                ContentPolicySyncMessage.class, 35, Side.CLIENT);
        CHANNEL.registerMessage(NpcSkinRequestMessage.Handler.class,
                NpcSkinRequestMessage.class, 36, Side.SERVER);
        CHANNEL.registerMessage(NpcSkinDataMessage.Handler.class,
                NpcSkinDataMessage.class, 37, Side.CLIENT);
        CHANNEL.registerMessage(PerformanceOverlayMessage.Handler.class,
                PerformanceOverlayMessage.class, 38, Side.CLIENT);
        CHANNEL.registerMessage(GameplayActionMessage.Handler.class,
                GameplayActionMessage.class, 39, Side.SERVER);
        CHANNEL.registerMessage(GameplaySnapshotMessage.Handler.class,
                GameplaySnapshotMessage.class, 40, Side.CLIENT);
        CHANNEL.registerMessage(ChakraControlToggleMessage.Handler.class,
                ChakraControlToggleMessage.class, 41, Side.SERVER);
        CHANNEL.registerMessage(ChakraControlStateMessage.Handler.class,
                ChakraControlStateMessage.class, 42, Side.CLIENT);
    }

    public static void openVillageGui(EntityPlayerMP player) {
        CHANNEL.sendTo(new OpenVillageGuiMessage(), player);
    }

    public static void openCreatorCreditsGui(EntityPlayerMP player) {
        CHANNEL.sendTo(new OpenCreatorCreditsGuiMessage(), player);
    }

    public static void openDiscordLinkPrompt(EntityPlayerMP player) {
        openDiscordLinkPrompt(player, false);
    }

    public static void openDiscordLinkPrompt(EntityPlayerMP player, boolean linked) {
        if (player != null) {
            CHANNEL.sendTo(new OpenDiscordLinkPromptMessage(linked), player);
        }
    }

    public static void openDiscordLinkCode(EntityPlayerMP player, String code,
                                           String command, String instruction) {
        if (player != null) {
            CHANNEL.sendTo(new OpenDiscordLinkCodeMessage(code, command, instruction), player);
        }
    }

    public static void sendVillageSelection(Village village) {
        if (village != null) {
            CHANNEL.sendToServer(new VillageSelectMessage(village.id()));
        }
    }

    public static void sendSubstitutionUse() {
        CHANNEL.sendToServer(new SubstitutionUseMessage());
    }

    public static void sendChakraLearn(ChakraMode mode) {
        if (mode != null) {
            CHANNEL.sendToServer(new ChakraLearnMessage(mode));
        }
    }

    public static void sendChakraModeEffect(EntityPlayerMP player, ChakraMode mode, boolean active) {
        if (player == null || mode == null) {
            return;
        }
        CHANNEL.sendToAllAround(new ChakraModeEffectMessage(player.getEntityId(), mode.id(), active),
                new NetworkRegistry.TargetPoint(player.dimension, player.posX, player.posY, player.posZ, 48.0D));
    }

    public static void requestQuestSync() {
        requestQuestSync(-1);
    }

    public static void setChakraControl(boolean enabled) {
        CHANNEL.sendToServer(new ChakraControlToggleMessage(enabled));
    }

    public static void sendChakraControlState(EntityPlayerMP player, boolean enabled) {
        if (player != null) CHANNEL.sendTo(new ChakraControlStateMessage(enabled), player);
    }

    public static void sendDiscordLinkPromptChoice(boolean accepted) {
        CHANNEL.sendToServer(new DiscordLinkPromptChoiceMessage(accepted));
    }

    public static void sendDiscordLinkPromptChoice(int action) {
        CHANNEL.sendToServer(new DiscordLinkPromptChoiceMessage(action));
    }

    public static void requestDiscordLinkPrompt() {
        CHANNEL.sendToServer(new DiscordLinkPromptRequestMessage());
    }

    public static void requestQuestSync(int catalogVersion) {
        CHANNEL.sendToServer(new QuestRequestMessage(catalogVersion));
    }

    public static void claimQuest(String questId) {
        CHANNEL.sendToServer(new QuestClaimMessage(questId));
    }

    public static void sendQuestSnapshot(EntityPlayerMP player, NBTTagCompound data) {
        CHANNEL.sendTo(new QuestSyncMessage(data), player);
    }

    public static void requestVillageLeadership(int villageId) {
        CHANNEL.sendToServer(new VillageLeadershipRequestMessage(villageId));
    }

    public static void sendVillageLeadershipAction(int action, int villageId,
                                                   java.util.UUID target, String value) {
        CHANNEL.sendToServer(new VillageLeadershipActionMessage(action, villageId, target, value));
    }

    public static void sendVillageLeadershipSnapshot(EntityPlayerMP player, NBTTagCompound data) {
        CHANNEL.sendTo(new VillageLeadershipSyncMessage(data), player);
    }

    public static void sendJutsuAdminSnapshot(EntityPlayerMP player, NBTTagCompound data) {
        if (player != null) {
            CHANNEL.sendTo(new JutsuAdminSnapshotMessage(data), player);
        }
    }

    public static void sendJutsuAdminAction(int action, String id, String property, String value) {
        CHANNEL.sendToServer(new JutsuAdminActionMessage(action, id, property, value));
    }

    public static void requestAdminAccess() {
        CHANNEL.sendToServer(new AdminAccessRequestMessage());
    }

    public static void sendAdminAccess(EntityPlayerMP player, boolean operator) {
        if (player != null) {
            CHANNEL.sendTo(new AdminAccessSyncMessage(operator), player);
        }
    }

    public static void purchaseStoreItem(String registryName) {
        if (registryName != null && !registryName.isEmpty()) {
            CHANNEL.sendToServer(new StorePurchaseMessage(registryName));
        }
    }

    public static void requestStoreCatalog() {
        CHANNEL.sendToServer(new StoreCatalogRequestMessage());
    }

    public static void sendStoreCatalog(EntityPlayerMP player, NBTTagCompound data) {
        if (player != null && data != null) {
            CHANNEL.sendTo(new StoreCatalogSyncMessage(data), player);
        }
    }

    public static void sendStorePurchaseResult(EntityPlayerMP player,
                                               StorePurchaseService.Result result) {
        if (player != null && result != null) {
            CHANNEL.sendTo(new StorePurchaseResultMessage(result), player);
        }
    }

    public static void requestTradeSync() {
        sendTradeAction(net.rebornaddon.trade.TradeService.SNAPSHOT, null, -1, 0);
    }

    public static void sendTradeAction(int action, java.util.UUID target, int slot, int amount) {
        CHANNEL.sendToServer(new TradeActionMessage(action, target, slot, amount));
    }

    public static void sendTradeSync(EntityPlayerMP player, NBTTagCompound data) {
        if (player != null && data != null) {
            CHANNEL.sendTo(new TradeSyncMessage(data), player);
        }
    }

    public static void sendNativeContentAction(String action, String json) {
        CHANNEL.sendToServer(new NativeContentActionMessage(action, json));
    }

    public static void sendNativeContentSync(EntityPlayerMP player, String kind, String json) {
        if (player != null) {
            byte[] bytes = (json == null ? "" : json).getBytes(StandardCharsets.UTF_8);
            int partCount = Math.max(1, (bytes.length + NATIVE_CONTENT_FRAGMENT_BYTES - 1)
                    / NATIVE_CONTENT_FRAGMENT_BYTES);
            int transferId = NATIVE_CONTENT_TRANSFER_ID.incrementAndGet();
            for (int part = 0; part < partCount; part++) {
                int start = part * NATIVE_CONTENT_FRAGMENT_BYTES;
                int end = Math.min(bytes.length, start + NATIVE_CONTENT_FRAGMENT_BYTES);
                CHANNEL.sendTo(new NativeContentSyncMessage(kind, transferId, part, partCount,
                        Arrays.copyOfRange(bytes, start, end)), player);
                PerformanceMonitor.recordOutbound(end - start);
            }
        }
    }

    public static void requestExamSnapshot() {
        requestExamSnapshot(false);
    }

    public static void requestExamSnapshot(boolean force) {
        sendExamAction("snapshot", force ? "{\"force\":true}" : "{}");
    }

    public static void requestExamRankCatalog() {
        sendExamAction("rank.catalog", "{}");
    }

    public static void sendExamAction(String action, String json) {
        CHANNEL.sendToServer(new ExamActionMessage(action, json));
    }

    public static void sendExamSync(EntityPlayerMP player, String kind, String json) {
        if (player == null) return;
        byte[] bytes = (json == null ? "" : json).getBytes(StandardCharsets.UTF_8);
        int partCount = Math.max(1, (bytes.length + NATIVE_CONTENT_FRAGMENT_BYTES - 1)
                / NATIVE_CONTENT_FRAGMENT_BYTES);
        int transferId = EXAM_TRANSFER_ID.incrementAndGet();
        for (int part = 0; part < partCount; part++) {
            int start = part * NATIVE_CONTENT_FRAGMENT_BYTES;
            int end = Math.min(bytes.length, start + NATIVE_CONTENT_FRAGMENT_BYTES);
            CHANNEL.sendTo(new ExamSyncMessage(kind, transferId, part, partCount,
                    Arrays.copyOfRange(bytes, start, end)), player);
            PerformanceMonitor.recordOutbound(end - start);
        }
    }

    public static void requestPerformanceSnapshot(boolean profile) {
        CHANNEL.sendToServer(new PerformanceRequestMessage(profile
                ? PerformanceRequestMessage.PROFILE : PerformanceRequestMessage.SNAPSHOT,
                PerformanceMonitor.DEFAULT_PROFILE_MILLIS, -1));
    }

    public static void requestPerformanceProfile(long durationMillis) {
        CHANNEL.sendToServer(new PerformanceRequestMessage(PerformanceRequestMessage.PROFILE,
                durationMillis, -1));
    }

    public static void teleportPerformanceResult(boolean chunk, int index) {
        CHANNEL.sendToServer(new PerformanceRequestMessage(chunk
                ? PerformanceRequestMessage.TELEPORT_CHUNK
                : PerformanceRequestMessage.TELEPORT_HOTSPOT, 0L, index));
    }

    public static void sendPerformanceSnapshot(EntityPlayerMP player, NBTTagCompound data) {
        if (player != null) CHANNEL.sendTo(new PerformanceSnapshotMessage(data), player);
    }

    public static void setPerformanceOverlay(EntityPlayerMP player, boolean enabled) {
        if (player != null) CHANNEL.sendTo(new PerformanceOverlayMessage(enabled), player);
    }

    public static void sendGameplayAction(String section, String action, String payload) {
        CHANNEL.sendToServer(new GameplayActionMessage(section, action, payload));
    }

    public static void requestGameplaySnapshot(String section) {
        sendGameplayAction(section, "snapshot", "{}");
    }

    public static void sendGameplaySnapshot(EntityPlayerMP player, String section, String json) {
        if (player != null) CHANNEL.sendTo(new GameplaySnapshotMessage(section, json), player);
    }

    public static void sendCompatibilityRequest(EntityPlayerMP player, NBTTagCompound manifest) {
        if (player != null) CHANNEL.sendTo(new CompatibilityRequestMessage(manifest), player);
    }

    public static void sendCompatibilityReport(NBTTagCompound report) {
        CHANNEL.sendToServer(new CompatibilityReportMessage(report));
    }

    public static void sendContentPolicy(EntityPlayerMP player, NBTTagCompound data) {
        if (player != null) CHANNEL.sendTo(new ContentPolicySyncMessage(data), player);
    }

    public static void requestNpcSkin(String url) {
        if (url != null && !url.isEmpty()) CHANNEL.sendToServer(new NpcSkinRequestMessage(url));
    }

    public static void sendNpcSkin(EntityPlayerMP player, String url, String hash, byte[] bytes) {
        if (player == null || bytes == null) return;
        int fragmentBytes = 24576;
        int parts = Math.max(1, (bytes.length + fragmentBytes - 1) / fragmentBytes);
        int transfer = NPC_SKIN_TRANSFER_ID.incrementAndGet();
        for (int part = 0; part < parts; part++) {
            int start = part * fragmentBytes;
            int end = Math.min(bytes.length, start + fragmentBytes);
            CHANNEL.sendTo(new NpcSkinDataMessage(url, hash, transfer, part, parts,
                    Arrays.copyOfRange(bytes, start, end)), player);
        }
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
