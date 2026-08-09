package net.rebornaddon.keybind;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SideOnly(Side.CLIENT)
public final class BlockedKeyBindingHandler {
    public static final BlockedKeyBindingHandler INSTANCE = new BlockedKeyBindingHandler();

    private static final String SHINOBI_PREFIX = "key.shinobiaddon.";
    private static final String SPRING_PREFIX = "key.mcreator.special_jutsu_";
    private static final String SPRING_CATEGORY = "key.categories.springaddon";
    private static final String SHINOBI_CATEGORY = "key.shinobiaddon.category";
    private static final String TAIJUTSU_CATEGORY = "key.shinobiaddon.taijutsu";

    private int nextCheck;

    private BlockedKeyBindingHandler() {
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (nextCheck-- <= 0) {
            suppress();
            nextCheck = 100;
        }
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.getGui() instanceof GuiControls) {
            suppress();
        }
    }

    public void suppress() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.gameSettings == null || minecraft.gameSettings.keyBindings == null) {
            return;
        }

        List<KeyBinding> kept = new ArrayList<KeyBinding>();
        boolean changed = false;
        for (KeyBinding binding : minecraft.gameSettings.keyBindings) {
            if (isBlocked(binding)) {
                disable(binding);
                changed = true;
            } else {
                kept.add(binding);
            }
        }
        if (changed) {
            minecraft.gameSettings.keyBindings = kept.toArray(new KeyBinding[kept.size()]);
        }

        changed |= removeStaticBindings();
        removeCategories();
        if (changed) {
            KeyBinding.resetKeyBindingArrayAndHash();
        }
    }

    private static boolean removeStaticBindings() {
        try {
            Field field = ReflectionHelper.findField(KeyBinding.class,
                    "KEYBIND_ARRAY", "field_74516_a");
            @SuppressWarnings("unchecked")
            Map<String, KeyBinding> bindings = (Map<String, KeyBinding>) field.get(null);
            boolean changed = false;
            Iterator<Map.Entry<String, KeyBinding>> iterator = bindings.entrySet().iterator();
            while (iterator.hasNext()) {
                KeyBinding binding = iterator.next().getValue();
                if (isBlocked(binding)) {
                    disable(binding);
                    iterator.remove();
                    changed = true;
                }
            }
            return changed;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static void removeCategories() {
        try {
            Field setField = ReflectionHelper.findField(KeyBinding.class,
                    "KEYBIND_SET", "field_151473_c");
            @SuppressWarnings("unchecked")
            Set<String> categories = (Set<String>) setField.get(null);
            categories.remove(SHINOBI_CATEGORY);
            categories.remove(TAIJUTSU_CATEGORY);
            categories.remove(SPRING_CATEGORY);

            Field orderField = ReflectionHelper.findField(KeyBinding.class,
                    "CATEGORY_ORDER", "field_193627_d");
            @SuppressWarnings("unchecked")
            Map<String, Integer> order = (Map<String, Integer>) orderField.get(null);
            order.remove(SHINOBI_CATEGORY);
            order.remove(TAIJUTSU_CATEGORY);
            order.remove(SPRING_CATEGORY);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void disable(KeyBinding binding) {
        int oldCode = binding.getKeyCode();
        KeyBinding.setKeyBindState(oldCode, false);
        ReflectionHelper.setPrivateValue(KeyBinding.class, binding, false,
                "pressed", "field_74513_e");
        ReflectionHelper.setPrivateValue(KeyBinding.class, binding, 0,
                "pressTime", "field_151474_i");
        binding.setKeyModifierAndCode(KeyModifier.NONE, Keyboard.KEY_NONE);
    }

    private static boolean isBlocked(KeyBinding binding) {
        if (binding == null) {
            return false;
        }
        String description = binding.getKeyDescription();
        String category = binding.getKeyCategory();
        return description.startsWith(SHINOBI_PREFIX)
                || description.startsWith(SPRING_PREFIX)
                || SPRING_CATEGORY.equals(category)
                || SHINOBI_CATEGORY.equals(category)
                || TAIJUTSU_CATEGORY.equals(category);
    }
}
