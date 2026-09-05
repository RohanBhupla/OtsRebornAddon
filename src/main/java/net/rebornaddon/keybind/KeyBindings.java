package net.rebornaddon.keybind;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.rebornaddon.gui.GuiHub;
import net.rebornaddon.village.network.RebornAddonNetwork;
import net.rebornaddon.compat.ChakraControlCompatibility;
import org.lwjgl.input.Keyboard;

/**
 * Only ever loaded via ClientProxy (see net.rebornaddon.proxy), which itself is only
 * ever resolved on the physical client. The @SideOnly here is a second layer of safety
 * on top of that isolation, not the only thing preventing a server-side crash.
 */
@SideOnly(Side.CLIENT)
public class KeyBindings {

    public static KeyBinding openHub;
    public static KeyBinding substitution;
    public static KeyBinding discordLink;
    public static KeyBinding chakraControl;
    public static KeyBinding zoom;

    public static void register() {
        openHub = new KeyBinding("key.rebornaddon.open_hub", Keyboard.KEY_H, "key.categories.rebornaddon");
        substitution = new KeyBinding("key.rebornaddon.substitution", Keyboard.KEY_X, "key.categories.rebornaddon");
        discordLink = new KeyBinding("key.rebornaddon.discord_link", Keyboard.KEY_NONE, "key.categories.rebornaddon");
        chakraControl = new KeyBinding("key.rebornaddon.chakra_control", Keyboard.KEY_Z,
                "key.categories.rebornaddon");
        zoom = new KeyBinding("key.rebornaddon.zoom", Keyboard.KEY_C, "key.categories.rebornaddon");
        ClientRegistry.registerKeyBinding(openHub);
        ClientRegistry.registerKeyBinding(substitution);
        ClientRegistry.registerKeyBinding(discordLink);
        ClientRegistry.registerKeyBinding(chakraControl);
        ClientRegistry.registerKeyBinding(zoom);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) return; // don't steal the key while another screen is open
        if (openHub.isPressed()) {
            mc.displayGuiScreen(new GuiHub());
        }
        if (substitution.isPressed()) {
            RebornAddonNetwork.sendSubstitutionUse();
        }
        if (discordLink.isPressed()) {
            RebornAddonNetwork.requestDiscordLinkPrompt();
        }
        if (chakraControl.isPressed() && mc.player != null) {
            boolean enabled = !ChakraControlCompatibility.isEnabled(mc.player);
            ChakraControlCompatibility.setEnabled(mc.player, enabled);
            RebornAddonNetwork.setChakraControl(enabled);
        }
    }
}
