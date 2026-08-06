package net.rebornaddon.proxy;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.rebornaddon.data.RankedClientData;
import net.rebornaddon.keybind.KeyBindings;
import net.rebornaddon.keybind.ClientKeyLocalization;
import net.rebornaddon.music.client.RebornMusicController;
import net.rebornaddon.ranked.network.RankedSyncMessage;
import net.rebornaddon.substitution.client.SubstitutionClientRegistration;
import net.rebornaddon.substitution.SubstitutionEffectMessage;
import net.rebornaddon.substitution.client.ClientSubstitutionParticles;

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
    }

    @Override
    public void init() {
        MinecraftForge.EVENT_BUS.register(new KeyBindings());
        MinecraftForge.EVENT_BUS.register(RebornMusicController.INSTANCE);
        ClientKeyLocalization.install();
    }

    @Override
    public void handleRankedSync(RankedSyncMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> RankedClientData.update(message));
    }

    @Override
    public void handleSubstitutionEffect(final SubstitutionEffectMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> ClientSubstitutionParticles.INSTANCE.spawn(message));
    }
}
