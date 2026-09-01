package net.rebornaddon.proxy;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.rebornaddon.data.RankedClientData;
import net.rebornaddon.keybind.KeyBindings;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.keybind.BlockedKeyBindingHandler;
import net.rebornaddon.music.client.RebornMusicController;
import net.rebornaddon.music.client.RebornMusicPauseMenu;
import net.rebornaddon.music.client.AkatsukiBellLimiter;
import net.rebornaddon.quest.client.ClientQuestData;
import net.rebornaddon.quest.network.QuestSyncMessage;
import net.rebornaddon.ranked.network.RankedSyncMessage;
import net.rebornaddon.substitution.client.SubstitutionClientRegistration;
import net.rebornaddon.substitution.SubstitutionEffectMessage;
import net.rebornaddon.substitution.client.ClientSubstitutionParticles;
import net.rebornaddon.chakra.ChakraMode;
import net.rebornaddon.chakra.client.ClientChakraModeOverlay;
import net.rebornaddon.chakra.client.GuiChakraModeLearn;
import net.rebornaddon.chakra.network.ChakraModeEffectMessage;
import net.rebornaddon.chakra.network.ChakraControlStateMessage;
import net.rebornaddon.chakra.ChakraControlConfigurationService;
import net.rebornaddon.compat.ChakraControlCompatibility;
import net.narutomod.item.ItemNinjaArmor;
import net.rebornaddon.headband.client.ClientRogueArmorData;
import net.rebornaddon.village.client.ClientVillageLeadershipData;
import net.rebornaddon.village.network.VillageLeadershipSyncMessage;
import net.rebornaddon.compat.client.NarutoAddonOptionsGuard;
import net.rebornaddon.client.ClientDataResetHandler;
import net.rebornaddon.client.RebornGuiScale;
import net.rebornaddon.client.RebornGuiScaleOptionsHandler;
import net.rebornaddon.discord.client.DiscordClientHooks;
import net.rebornaddon.jutsu.client.JutsuAdminClientHooks;
import net.rebornaddon.jutsu.client.ClientAdminAccess;
import net.rebornaddon.jutsu.network.AdminAccessSyncMessage;
import net.rebornaddon.jutsu.network.JutsuAdminSnapshotMessage;
import net.rebornaddon.store.client.ClientStoreData;
import net.rebornaddon.store.network.StorePurchaseResultMessage;
import net.rebornaddon.store.network.StoreCatalogSyncMessage;
import net.rebornaddon.trade.client.ClientTradeData;
import net.rebornaddon.trade.network.TradeSyncMessage;
import net.rebornaddon.content.client.NativeContentClientHooks;
import net.rebornaddon.content.client.NativeContentClientRegistration;
import net.rebornaddon.content.network.NativeContentSyncMessage;
import net.rebornaddon.exam.client.ClientExamData;
import net.rebornaddon.exam.network.ExamSyncMessage;
import net.rebornaddon.performance.client.ClientPerformanceData;
import net.rebornaddon.performance.network.PerformanceSnapshotMessage;
import net.rebornaddon.performance.client.ClientPerformanceOverlay;
import net.rebornaddon.diagnostic.client.CompatibilityClientHandler;
import net.rebornaddon.diagnostic.network.CompatibilityRequestMessage;
import net.minecraft.nbt.NBTTagCompound;
import net.rebornaddon.policy.ContentPolicyService;
import net.rebornaddon.content.client.ClientNpcSkinCache;
import net.rebornaddon.content.network.NpcSkinDataMessage;
import net.rebornaddon.gameplay.client.ClientGameplayData;
import net.rebornaddon.gameplay.network.GameplaySnapshotMessage;
import net.rebornaddon.advancement.client.AdvancementClientGuard;

import java.io.File;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit() {
        RebornGuiScale.load(new File(Minecraft.getMinecraft().mcDataDir,
                "config/rebornaddon-client.cfg"));
        KeyBindings.register();
        SubstitutionClientRegistration.registerRenderers();
        NativeContentClientRegistration.registerRenderers();
        MinecraftForge.EVENT_BUS.register(ClientSubstitutionParticles.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ClientChakraModeOverlay.INSTANCE);
    }

    @Override
    public void init() {
        MinecraftForge.EVENT_BUS.register(new KeyBindings());
        MinecraftForge.EVENT_BUS.register(RebornMusicController.INSTANCE);
        MinecraftForge.EVENT_BUS.register(RebornMusicPauseMenu.INSTANCE);
        MinecraftForge.EVENT_BUS.register(AkatsukiBellLimiter.INSTANCE);
        MinecraftForge.EVENT_BUS.register(BlockedKeyBindingHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(NarutoAddonOptionsGuard.INSTANCE);
        MinecraftForge.EVENT_BUS.register(AdvancementClientGuard.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ClientDataResetHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(RebornGuiScaleOptionsHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(NativeContentClientHooks.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ClientPerformanceOverlay.INSTANCE);
        BlockedKeyBindingHandler.INSTANCE.suppress();
        ClientLocalization.install();
    }

    @Override
    public void handleRankedSync(RankedSyncMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> RankedClientData.update(message));
    }

    @Override
    public void handleSubstitutionEffect(final SubstitutionEffectMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> ClientSubstitutionParticles.INSTANCE.spawn(message));
    }

    @Override
    public void handleQuestSync(final QuestSyncMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> ClientQuestData.update(message.getData()));
    }

    @Override
    public void handleVillageLeadershipSync(final VillageLeadershipSyncMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> ClientVillageLeadershipData.update(message.getData()));
    }

    @Override
    public void handleJutsuAdminSnapshot(final JutsuAdminSnapshotMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> JutsuAdminClientHooks.openOrUpdate(message.data()));
    }

    @Override
    public void handleAdminAccessSync(final AdminAccessSyncMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> ClientAdminAccess.update(message.operator()));
    }

    @Override
    public void handleStorePurchaseResult(final StorePurchaseResultMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> ClientStoreData.update(message));
    }

    @Override
    public void handleStoreCatalogSync(final StoreCatalogSyncMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> ClientStoreData.updateCatalog(message.data()));
    }

    @Override
    public void handleTradeSync(final TradeSyncMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> ClientTradeData.update(message.data()));
    }

    @Override
    public void handleNativeContentSync(final NativeContentSyncMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> NativeContentClientHooks.INSTANCE
                .handle(message));
    }

    @Override
    public void handleExamSync(final ExamSyncMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> ClientExamData.handle(message));
    }

    @Override
    public void handlePerformanceSnapshot(final PerformanceSnapshotMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> ClientPerformanceData.update(message.data()));
    }

    @Override
    public void handlePerformanceOverlay(final boolean enabled) {
        Minecraft.getMinecraft().addScheduledTask(() -> ClientPerformanceOverlay.INSTANCE.setEnabled(enabled));
    }

    @Override
    public void handleGameplaySnapshot(final GameplaySnapshotMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> ClientGameplayData.update(
                message.section(), message.json()));
    }

    @Override
    public void handleCompatibilityRequest(final CompatibilityRequestMessage message) {
        CompatibilityClientHandler.handle(message.manifest());
    }

    @Override
    public void handleContentPolicySync(final NBTTagCompound data) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            ContentPolicyService.applyClientSnapshot(data);
            try {
                Class<?> plugin = Class.forName("net.rebornaddon.compat.jei.ShinobiAddonJeiVisibilityPlugin");
                plugin.getMethod("refreshPolicy").invoke(null);
            } catch (Throwable ignored) {
            }
        });
    }

    @Override
    public void handleNpcSkinData(final NpcSkinDataMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> ClientNpcSkinCache.receive(message));
    }

    @Override
    public void openDiscordLinkPrompt(final boolean linked) {
        Minecraft.getMinecraft().addScheduledTask(() -> DiscordClientHooks.openDiscordLinkPrompt(linked));
    }

    @Override
    public void openDiscordLinkCode(final String code, final String command,
                                    final String instruction) {
        Minecraft.getMinecraft().addScheduledTask(
                () -> DiscordClientHooks.openDiscordLinkCode(code, command, instruction));
    }

    @Override
    public void openChakraLearnGui(final ChakraMode mode) {
        Minecraft.getMinecraft().addScheduledTask(() -> Minecraft.getMinecraft()
                .displayGuiScreen(new GuiChakraModeLearn(mode)));
    }

    @Override
    public void handleChakraModeEffect(final ChakraModeEffectMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> ClientChakraModeOverlay.INSTANCE.update(message));
    }

    @Override
    public void handleChakraControlState(final ChakraControlStateMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            ChakraControlConfigurationService.INSTANCE.applyClientRuntime(
                    message.systemEnabled(), message.waterWalkingEnabled(),
                    message.wallClimbingEnabled(), message.wallClimbSpeedMultiplier());
            if (Minecraft.getMinecraft().player != null) {
                ChakraControlCompatibility.setEnabled(Minecraft.getMinecraft().player,
                        message.enabled());
            }
        });
    }

    @Override
    public ItemNinjaArmor.ArmorData createRogueArmorData(ItemNinjaArmor.Type type,
                                                          String texture,
                                                          boolean hideHeadwear) {
        return new ClientRogueArmorData(type, texture, hideHeadwear);
    }
}
