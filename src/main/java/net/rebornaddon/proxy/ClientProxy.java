package net.rebornaddon.proxy;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.rebornaddon.data.RankedClientData;
import net.rebornaddon.keybind.KeyBindings;
import net.rebornaddon.ranked.network.RankedSyncMessage;

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
    }

    @Override
    public void init() {
        MinecraftForge.EVENT_BUS.register(new KeyBindings());
    }

    @Override
    public void handleRankedSync(RankedSyncMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> RankedClientData.update(message));
    }
}
