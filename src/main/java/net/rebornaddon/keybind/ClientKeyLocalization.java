package net.rebornaddon.keybind;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.LanguageManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

@SideOnly(Side.CLIENT)
public final class ClientKeyLocalization implements IResourceManagerReloadListener {
    private static final ClientKeyLocalization INSTANCE = new ClientKeyLocalization();
    private static final Map<String, String> LABELS = labels();

    private ClientKeyLocalization() {
    }

    public static void install() {
        IResourceManager manager = Minecraft.getMinecraft().getResourceManager();
        if (manager instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager) manager).registerReloadListener(INSTANCE);
        }
        INSTANCE.applyLabels();
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        applyLabels();
    }

    private void applyLabels() {
        try {
            net.minecraft.client.resources.Locale locale = currentLocale();
            if (locale == null) {
                return;
            }
            for (Field field : net.minecraft.client.resources.Locale.class.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(locale);
                if (value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> translations = (Map<String, String>) value;
                    translations.putAll(LABELS);
                    return;
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static net.minecraft.client.resources.Locale currentLocale() throws ReflectiveOperationException {
        for (Field field : LanguageManager.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    || !net.minecraft.client.resources.Locale.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            return (net.minecraft.client.resources.Locale) field.get(null);
        }
        return null;
    }

    private static Map<String, String> labels() {
        Map<String, String> labels = new LinkedHashMap<String, String>();
        labels.put("key.categories.rebornaddon", "Reborn Addon");
        labels.put("key.rebornaddon.open_hub", "Open Reborn Hub");
        labels.put("key.rebornaddon.substitution", "Village Substitution");
        labels.put("key.categories.jutsuattribute", "Jutsu Attribute");
        labels.put("key.jutsuattribute.attribute_display", "Open Jutsu Attribute Display");
        return labels;
    }
}
