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
import net.narutomod.item.ItemNinjaArmor;
import net.rebornaddon.headband.client.ClientRogueArmorData;

/**
 * Only ever loaded on the physical client - Forge resolves the actual class to use for
 * RebornAddonMod.proxy via reflection (@SidedProxy), by name, at runtime, based on which
 * physical side is running. That indirection is what keeps a dedicated server from ever
 * needing to load this class (or anything it references, like KeyBindings -> GuiHub ->
 * GuiScreen) at all - it never even attempts to resolve those class names server-side.
 */
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit() {
        KeyBindings.register();
        SubstitutionClientRegistration.registerRenderers();
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
    public void openChakraLearnGui(final ChakraMode mode) {
        Minecraft.getMinecraft().addScheduledTask(() -> Minecraft.getMinecraft()
                .displayGuiScreen(new GuiChakraModeLearn(mode)));
    }

    @Override
    public void handleChakraModeEffect(final ChakraModeEffectMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> ClientChakraModeOverlay.INSTANCE.update(message));
    }

    @Override
    public ItemNinjaArmor.ArmorData createRogueArmorData(ItemNinjaArmor.Type type,
                                                          String texture,
                                                          boolean hideHeadwear) {
        return new ClientRogueArmorData(type, texture, hideHeadwear);
    }
}
