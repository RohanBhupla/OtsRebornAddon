package net.rebornaddon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.LanguageManager;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SideOnly(Side.CLIENT)
public final class ClientLocalization implements IResourceManagerReloadListener {
    private static final Logger LOGGER = LogManager.getLogger("RebornAddon Localization");
    private static final ClientLocalization INSTANCE = new ClientLocalization();
    private static final ResourceLocation ENGLISH =
            new ResourceLocation("rebornaddon", "lang/en_us.lang");

    private ClientLocalization() {
    }

    public static void install() {
        IResourceManager manager = Minecraft.getMinecraft().getResourceManager();
        if (manager instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager) manager).registerReloadListener(INSTANCE);
        }
        INSTANCE.applyTranslations(manager);
    }

    public static String format(String key, String fallback, Object... arguments) {
        String translated = I18n.format(key, arguments);
        if (!key.equals(translated)) {
            return translated;
        }
        try {
            return String.format(java.util.Locale.ROOT, fallback, arguments);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        applyTranslations(resourceManager);
    }

    private void applyTranslations(IResourceManager resourceManager) {
        try {
            Map<String, String> active = activeTranslations();
            if (active == null) {
                return;
            }
            Map<String, String> loaded = loadTranslations(resourceManager);
            active.putAll(loaded);
            addItemFallbacks(active);
        } catch (ReflectiveOperationException | IOException exception) {
            LOGGER.warn("Unable to install RebornAddon translations", exception);
        }
    }

    private static Map<String, String> loadTranslations(IResourceManager resourceManager) throws IOException {
        Map<String, String> translations = new LinkedHashMap<String, String>();
        if (resourceManager != null) {
            try {
                List<IResource> resources = resourceManager.getAllResources(ENGLISH);
                for (IResource resource : resources) {
                    try (InputStream input = resource.getInputStream()) {
                        readLanguageFile(input, translations);
                    }
                }
            } catch (IOException exception) {
                LOGGER.debug("Falling back to the packaged RebornAddon language file", exception);
            }
        }
        if (!translations.isEmpty()) {
            return translations;
        }

        try (InputStream input = ClientLocalization.class
                .getResourceAsStream("/assets/rebornaddon/lang/en_us.lang")) {
            if (input != null) {
                readLanguageFile(input, translations);
            }
        }
        return translations;
    }

    static void readLanguageFile(InputStream input, Map<String, String> translations) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            translations.put(line.substring(0, separator).trim(), line.substring(separator + 1));
        }
    }

    private static Map<String, String> activeTranslations() throws ReflectiveOperationException {
        net.minecraft.client.resources.Locale locale = currentLocale();
        if (locale == null) {
            return null;
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
                return translations;
            }
        }
        return null;
    }

    private static net.minecraft.client.resources.Locale currentLocale()
            throws ReflectiveOperationException {
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

    private static void addItemFallbacks(Map<String, String> translations) {
        for (Item item : ForgeRegistries.ITEMS.getValuesCollection()) {
            ResourceLocation name = item.getRegistryName();
            if (name == null || !"rebornaddon".equals(name.getResourceDomain())) {
                continue;
            }
            String key = item.getUnlocalizedName() + ".name";
            if (!translations.containsKey(key)) {
                translations.put(key, readableName(name.getResourcePath()));
            }
        }
    }

    private static String readableName(String path) {
        StringBuilder result = new StringBuilder(path.length());
        for (String word : path.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            result.append(word.substring(1));
        }
        return result.toString();
    }
}
