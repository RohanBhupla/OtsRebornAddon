package net.rebornaddon.keybind;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.rebornaddon.gui.GuiHub;
import org.lwjgl.input.Keyboard;

/**
 * Only ever loaded via ClientProxy (see net.rebornaddon.proxy), which itself is only
 * ever resolved on the physical client. The @SideOnly here is a second layer of safety
 * on top of that isolation, not the only thing preventing a server-side crash.
 */
@SideOnly(Side.CLIENT)
public class KeyBindings {

    public static KeyBinding openHub;

    public static void register() {
        openHub = new KeyBinding("key.rebornaddon.open_hub", Keyboard.KEY_H, "key.categories.rebornaddon");
        ClientRegistry.registerKeyBinding(openHub);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) return; // don't steal the key while another screen is open
        if (openHub.isPressed()) {
            mc.displayGuiScreen(new GuiHub());
        }
    }
}
