package net.rebornaddon.proxy;

import net.rebornaddon.ranked.network.RankedSyncMessage;
import net.rebornaddon.substitution.SubstitutionEffectMessage;
import net.rebornaddon.quest.network.QuestSyncMessage;
import net.rebornaddon.chakra.ChakraMode;
import net.rebornaddon.chakra.network.ChakraModeEffectMessage;
import net.rebornaddon.chakra.network.ChakraControlStateMessage;
import net.rebornaddon.village.network.VillageLeadershipSyncMessage;
import net.rebornaddon.jutsu.network.JutsuAdminSnapshotMessage;
import net.rebornaddon.jutsu.network.AdminAccessSyncMessage;
import net.rebornaddon.store.network.StorePurchaseResultMessage;
import net.rebornaddon.store.network.StoreCatalogSyncMessage;
import net.rebornaddon.trade.network.TradeSyncMessage;
import net.rebornaddon.content.network.NativeContentSyncMessage;
import net.rebornaddon.exam.network.ExamSyncMessage;
import net.rebornaddon.performance.network.PerformanceSnapshotMessage;
import net.rebornaddon.diagnostic.network.CompatibilityRequestMessage;
import net.minecraft.nbt.NBTTagCompound;
import net.rebornaddon.content.network.NpcSkinDataMessage;
import net.rebornaddon.gameplay.network.GameplaySnapshotMessage;
import net.narutomod.item.ItemNinjaArmor;

public class CommonProxy {

    public void preInit() {
    }

    public void init() {
    }

    public void handleRankedSync(RankedSyncMessage message) {
    }

    public void handleSubstitutionEffect(SubstitutionEffectMessage message) {
    }

    public void handleQuestSync(QuestSyncMessage message) {
    }

    public void handleVillageLeadershipSync(VillageLeadershipSyncMessage message) {
    }

    public void handleJutsuAdminSnapshot(JutsuAdminSnapshotMessage message) {
    }

    public void handleAdminAccessSync(AdminAccessSyncMessage message) {
    }

    public void handleStorePurchaseResult(StorePurchaseResultMessage message) {
    }

    public void handleStoreCatalogSync(StoreCatalogSyncMessage message) {
    }

    public void handleTradeSync(TradeSyncMessage message) {
    }

    public void handleNativeContentSync(NativeContentSyncMessage message) {
    }

    public void handleExamSync(ExamSyncMessage message) {
    }

    public void handlePerformanceSnapshot(PerformanceSnapshotMessage message) {
    }

    public void handlePerformanceOverlay(boolean enabled) {
    }

    public void handleGameplaySnapshot(GameplaySnapshotMessage message) {
    }

    public void handleCompatibilityRequest(CompatibilityRequestMessage message) {
    }

    public void handleContentPolicySync(NBTTagCompound data) {
    }

    public void handleNpcSkinData(NpcSkinDataMessage message) {
    }

    public void openDiscordLinkPrompt(boolean linked) {
    }

    public void openDiscordLinkCode(String code, String command, String instruction) {
    }

    public void openChakraLearnGui(ChakraMode mode) {
    }

    public void handleChakraModeEffect(ChakraModeEffectMessage message) {
    }

    public void handleChakraControlState(ChakraControlStateMessage message) {
    }

    public ItemNinjaArmor.ArmorData createRogueArmorData(ItemNinjaArmor.Type type,
                                                          String texture,
                                                          boolean hideHeadwear) {
        return new ServerRogueArmorData(texture);
    }

    private static final class ServerRogueArmorData extends ItemNinjaArmor.ArmorData {
        private ServerRogueArmorData(String texture) {
            this.texture = texture;
        }
    }
}
