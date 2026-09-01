package net.rebornaddon.jutsu;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.narutomod.item.ItemJutsu;
import net.rebornaddon.compat.ShinobiAddonRestrictionHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class JutsuRegistry {
    public static final JutsuRegistry INSTANCE = new JutsuRegistry();

    private static final Logger LOGGER = LogManager.getLogger("RebornAddon Jutsu Registry");
    private static final String EXPLOSIVE_CLAY_CALLBACK =
            "net.narutomod.item.ItemBakuton$ExplosiveClay$Jutsu";

    private final Map<String, JutsuDefinition> byId = new LinkedHashMap<String, JutsuDefinition>();
    private final Map<String, JutsuDefinition> byCanonicalName =
            new LinkedHashMap<String, JutsuDefinition>();
    private final IdentityHashMap<ItemJutsu.JutsuEnum, List<JutsuDefinition>> byJutsu =
            new IdentityHashMap<ItemJutsu.JutsuEnum, List<JutsuDefinition>>();
    private final IdentityHashMap<JutsuDefinition, ExternalActivation> externalActivations =
            new IdentityHashMap<JutsuDefinition, ExternalActivation>();

    private Field jutsuListField;
    private Field defaultCooldownMapField;
    private Method currentJutsuMethod;

    private JutsuRegistry() {
    }

    public synchronized void discover() {
        byId.clear();
        byCanonicalName.clear();
        byJutsu.clear();
        externalActivations.clear();
        ExternalJutsuSettings.clear();
        resolveAccessors();
        if (jutsuListField == null || currentJutsuMethod == null) {
            LOGGER.error("NarutoMod jutsu accessors were not found; jutsu configuration is unavailable.");
            return;
        }

        for (Item item : ForgeRegistries.ITEMS.getValuesCollection()) {
            if (!(item instanceof ItemJutsu.Base)) {
                continue;
            }
            registerItem((ItemJutsu.Base) item);
        }
        registerExternalManager("sharinganaddon", "net.decentstudio.narutoaddon.config.JutsuConfig",
                "net.decentstudio.narutoaddon.manager.JutsuManager", true);
        registerExternalManager("jutsuaddon", "net.decentstudio.jutsuaddon.config.JutsuConfig",
                "net.decentstudio.jutsuaddon.manager.JutsuManager", false);
        LOGGER.info("Discovered {} configurable jutsu entries.", byId.size());
    }

    public synchronized void clear() {
        byId.clear();
        byCanonicalName.clear();
        byJutsu.clear();
        externalActivations.clear();
        ExternalJutsuSettings.clear();
    }

    public synchronized List<JutsuDefinition> all() {
        List<JutsuDefinition> values = new ArrayList<JutsuDefinition>(byId.values());
        Collections.sort(values);
        return Collections.unmodifiableList(values);
    }

    public synchronized JutsuDefinition get(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    public synchronized List<String> ids() {
        List<String> values = new ArrayList<String>(byId.keySet());
        Collections.sort(values);
        return values;
    }

    public synchronized List<String> namespaces() {
        List<String> values = new ArrayList<String>();
        for (String id : byId.keySet()) {
            int separator = id.indexOf(':');
            String namespace = separator < 0 ? id : id.substring(0, separator);
            if (!values.contains(namespace)) {
                values.add(namespace);
            }
        }
        Collections.sort(values);
        return values;
    }

    public boolean activateExternal(JutsuDefinition definition, EntityLivingBase target, int durationTicks) {
        ExternalActivation activation;
        synchronized (this) {
            activation = externalActivations.get(definition);
        }
        return activation != null && activation.activate(target, durationTicks);
    }

    public JutsuDefinition resolve(ItemJutsu.Base item, ItemStack stack, float power) {
        ItemJutsu.JutsuEnum current = currentJutsu(item, stack);
        if (current == null) {
            return null;
        }
        synchronized (this) {
            List<JutsuDefinition> definitions = byJutsu.get(current);
            if (definitions == null || definitions.isEmpty()) {
                return null;
            }
            if (definitions.size() == 1) {
                return definitions.get(0);
            }
            JutsuDefinition first = definitions.get(0);
            for (JutsuDefinition definition : definitions) {
                if (power >= definition.minimumPower() && power < definition.maximumPower()) {
                    return definition;
                }
            }
            return power < first.minimumPower() ? first : definitions.get(definitions.size() - 1);
        }
    }

    public List<JutsuDefinition> stages(ItemJutsu.JutsuEnum jutsu) {
        synchronized (this) {
            List<JutsuDefinition> values = byJutsu.get(jutsu);
            return values == null ? Collections.<JutsuDefinition>emptyList()
                    : Collections.unmodifiableList(new ArrayList<JutsuDefinition>(values));
        }
    }

    public ItemJutsu.JutsuEnum currentJutsu(ItemJutsu.Base item, ItemStack stack) {
        if (item == null || stack == null || currentJutsuMethod == null) {
            return null;
        }
        try {
            return (ItemJutsu.JutsuEnum) currentJutsuMethod.invoke(item, stack);
        } catch (Throwable throwable) {
            return null;
        }
    }

    /** Returns whether an inventory stack represents this learned technique. */
    public boolean matchesInventoryStack(JutsuDefinition definition, ItemStack stack) {
        if (definition == null || stack == null || stack.isEmpty()
                || definition.item() == null || stack.getItem() != definition.item()) {
            return false;
        }
        if (!(definition.item() instanceof ItemJutsu.Base) || definition.jutsu() == null) {
            return true;
        }
        ItemJutsu.JutsuEnum selected = currentJutsu((ItemJutsu.Base) definition.item(), stack);
        if (selected != definition.jutsu()) {
            return false;
        }
        // Charge stages share one selected jutsu and are awarded when that exact stage is used.
        return stages(selected).size() <= 1;
    }

    private void registerItem(ItemJutsu.Base item) {
        ResourceLocation registryName = item.getRegistryName();
        if (registryName == null) {
            return;
        }

        try {
            @SuppressWarnings("unchecked")
            List<ItemJutsu.JutsuEnum> jutsus = (List<ItemJutsu.JutsuEnum>) jutsuListField.get(item);
            if (jutsus == null) {
                return;
            }
            for (ItemJutsu.JutsuEnum jutsu : jutsus) {
                if (jutsu == null || jutsu.jutsu == null) {
                    continue;
                }
                if (EXPLOSIVE_CLAY_CALLBACK.equals(jutsu.jutsu.getClass().getName())) {
                    registerExplosiveClay(item, jutsu);
                } else {
                    registerStandard(item, registryName, jutsus.size(), jutsu);
                }
            }
        } catch (Throwable throwable) {
            LOGGER.warn("Could not inspect jutsu item {}.", registryName, throwable);
        }
    }

    private void registerStandard(ItemJutsu.Base item, ResourceLocation registryName, int itemJutsuCount,
                                  ItemJutsu.JutsuEnum jutsu) {
        String key = stableKey(jutsu.unlocalizedName);
        String path = registryName.getResourcePath();
        String id = registryName.getResourceDomain() + ":" + path;
        if (itemJutsuCount > 1 || (!key.isEmpty() && !path.contains(key))) {
            id += "/" + (key.isEmpty() ? "jutsu-" + jutsu.index : key);
        }

        float delay = safeCallbackFloat(jutsu, "getPowerupDelay", 0.0F);
        float base = safeCallbackFloat(jutsu, "getBasePower", jutsu.basePower);
        float maximum = safeCallbackFloat(jutsu, "getMaxPower", base);
        int chargeTicks = delay > 0.0F && maximum > base
                ? Math.max(1, Math.round((maximum - base) * delay)) : -1;
        String display = displayName(jutsu, key);
        Set<String> properties = JutsuDefinition.properties(chargeTicks >= 0, false);
        Map<String, String> defaults = JutsuDefinition.defaults(jutsu, chargeTicks,
                defaultCooldown(item, jutsu));
        add(new JutsuDefinition(uniqueId(id), display, registryName.getResourceDomain(), "",
                category(jutsu, id, display), restrictedItem(item), item, jutsu,
                Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, chargeTicks, properties, defaults));
    }

    private void registerExplosiveClay(Item item, ItemJutsu.JutsuEnum jutsu) {
        String parent = "narutomod:explosive_clay";
        addClayStage(parent + "/c1", "Explosive Clay: C1", item, jutsu, 1.0F, 2.0F, 15, 20L);
        addClayStage(parent + "/c2", "Explosive Clay: C2", item, jutsu, 2.0F, 3.0F, 165, 0L);
        addClayStage(parent + "/c3", "Explosive Clay: C3", item, jutsu, 3.0F, 4.0F, 315, 1200L);
        addClayStage(parent + "/c4", "Explosive Clay: C4", item, jutsu, 4.0F,
                Float.POSITIVE_INFINITY, 465, 1800L);
    }

    private void addClayStage(String id, String name, Item item, ItemJutsu.JutsuEnum jutsu,
                              float minimumPower, float maximumPower, int chargeTicks,
                              long cooldownTicks) {
        add(new JutsuDefinition(id, name, "narutomod", "narutomod:explosive_clay",
                "Explosion Release", restrictedItem(item), item, jutsu,
                minimumPower, maximumPower, chargeTicks,
                JutsuDefinition.properties(true, true),
                JutsuDefinition.defaults(jutsu, chargeTicks, Long.valueOf(cooldownTicks))));
    }

    private synchronized boolean add(JutsuDefinition definition) {
        if (definition.jutsu() != null) {
            List<JutsuDefinition> sameJutsu = byJutsu.get(definition.jutsu());
            if (sameJutsu != null) for (JutsuDefinition existing : new ArrayList<JutsuDefinition>(sameJutsu)) {
                if (existing.minimumPower() != definition.minimumPower()
                        || existing.maximumPower() != definition.maximumPower()) continue;
                boolean preferNew = !"narutomod".equals(existing.source())
                        && "narutomod".equals(definition.source());
                if (!preferNew) return false;
                remove(existing);
            }
        }
        String canonical = canonicalName(definition.displayName());
        JutsuDefinition existing = byCanonicalName.get(canonical);
        if (existing != null) {
            boolean preferNew = !"narutomod".equals(existing.source())
                    && "narutomod".equals(definition.source());
            if (!preferNew) return false;
            remove(existing);
        }
        byId.put(definition.id(), definition);
        byCanonicalName.put(canonical, definition);
        if (definition.jutsu() == null) {
            return true;
        }
        List<JutsuDefinition> stages = byJutsu.get(definition.jutsu());
        if (stages == null) {
            stages = new ArrayList<JutsuDefinition>();
            byJutsu.put(definition.jutsu(), stages);
        }
        stages.add(definition);
        return true;
    }

    private void remove(JutsuDefinition definition) {
        byId.remove(definition.id());
        byCanonicalName.remove(canonicalName(definition.displayName()));
        externalActivations.remove(definition);
        if (definition.jutsu() == null) return;
        List<JutsuDefinition> stages = byJutsu.get(definition.jutsu());
        if (stages == null) return;
        stages.remove(definition);
        if (stages.isEmpty()) byJutsu.remove(definition.jutsu());
    }

    private void registerExternalManager(String source, String configClassName,
                                         String managerClassName, boolean staticList) {
        try {
            Class<?> configClass = Class.forName(configClassName);
            Method activationMethod = Class.forName(managerClassName).getMethod(
                    "activateJutsu", EntityLivingBase.class, String.class, Integer.TYPE);
            Object config = configClass.getMethod("getInstance").invoke(null);
            if (config == null) {
                return;
            }
            List<String> ids = new ArrayList<String>();
            if (staticList) {
                Object value = configClass.getField("JUTSU_LIST").get(null);
                if (value instanceof String[]) {
                    Collections.addAll(ids, (String[]) value);
                }
            } else {
                Object value = Class.forName(managerClassName).getMethod("getJutsuIdList").invoke(null);
                if (value instanceof Iterable) {
                    for (Object id : (Iterable<?>) value) {
                        if (id != null) ids.add(id.toString());
                    }
                }
            }

            Method settingsMethod = configClass.getMethod("getJutsuSettings", String.class);
            for (String externalId : ids) {
                Object settings = settingsMethod.invoke(config, externalId);
                if (settings == null) {
                    continue;
                }
                Map<String, String> defaults = new LinkedHashMap<String, String>();
                defaults.put("enabled", "true");
                defaults.put("cooldown", Integer.toString(invokeInt(settings, "getCooldown", 0)));
                defaults.put("damage", Integer.toString(invokeInt(settings, "getDamage", 0)));
                defaults.put("max-duration", Integer.toString(invokeInt(settings, "getDuration", 0)));
                defaults.put("negative-effects-enabled", "true");
                defaults.put("effect-duration-multiplier", "1");
                defaults.put("effect-amplifier-adjustment", "0");
                defaults.put("negative-effect-rules", "");
                defaults.put("additional-negative-effects", "");
                Set<String> properties = new java.util.LinkedHashSet<String>();
                Collections.addAll(properties, "enabled", "chakra", "cooldown", "damage", "max-duration",
                        "negative-effects-enabled", "effect-duration-multiplier",
                        "effect-amplifier-adjustment", "negative-effect-rules",
                        "additional-negative-effects");
                String id = source + ":" + stableKey(externalId);
                String display = cleanDisplayName(title(externalId), stableKey(externalId));
                JutsuDefinition definition = new JutsuDefinition(uniqueId(id), display, source, "",
                        category(null, id, display), false, null, null,
                        Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, -1,
                        properties, defaults);
                if (add(definition)) {
                    externalActivations.put(definition, new ExternalActivation(activationMethod, externalId));
                    ExternalJutsuSettings.register(settings, definition);
                }
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable throwable) {
            LOGGER.warn("Could not inspect {} manager jutsus.", source, throwable);
        }
    }

    private static int invokeInt(Object target, String method, int fallback) {
        try {
            Object value = target.getClass().getMethod(method).invoke(target);
            return value instanceof Number ? ((Number) value).intValue() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String title(String value) {
        String key = stableKey(value).replace('-', ' ');
        StringBuilder result = new StringBuilder();
        for (String word : key.split(" ")) {
            if (word.length() == 0) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.length() == 0 ? value : result.toString();
    }

    private String uniqueId(String requested) {
        String id = requested;
        int suffix = 2;
        while (byId.containsKey(id)) {
            id = requested + "-" + suffix++;
        }
        return id;
    }

    private void resolveAccessors() {
        try {
            jutsuListField = ItemJutsu.Base.class.getDeclaredField("jutsuList");
            jutsuListField.setAccessible(true);
        } catch (Throwable throwable) {
            jutsuListField = null;
        }
        try {
            currentJutsuMethod = ItemJutsu.Base.class.getDeclaredMethod("getCurrentJutsu", ItemStack.class);
            currentJutsuMethod.setAccessible(true);
        } catch (Throwable throwable) {
            currentJutsuMethod = null;
        }
        try {
            defaultCooldownMapField = ItemJutsu.Base.class.getDeclaredField("defaultCooldownMap");
            defaultCooldownMapField.setAccessible(true);
        } catch (Throwable throwable) {
            defaultCooldownMapField = null;
        }
    }

    private Long defaultCooldown(ItemJutsu.Base item, ItemJutsu.JutsuEnum jutsu) {
        if (defaultCooldownMapField == null || jutsu.index < 0) {
            return null;
        }
        try {
            long[] values = (long[]) defaultCooldownMapField.get(item);
            if (values != null && jutsu.index < values.length && values[jutsu.index] >= 0L) {
                return Long.valueOf(values[jutsu.index]);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static float safeCallbackFloat(ItemJutsu.JutsuEnum jutsu, String method, float fallback) {
        try {
            Object value = jutsu.jutsu.getClass().getMethod(method).invoke(jutsu.jutsu);
            return value instanceof Number ? ((Number) value).floatValue() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String stableKey(String value) {
        String key = value == null ? "" : value.toLowerCase(Locale.ROOT);
        int separator = Math.max(key.lastIndexOf('.'), key.lastIndexOf(':'));
        if (separator >= 0 && separator + 1 < key.length()) {
            key = key.substring(separator + 1);
        }
        key = key.replaceAll("[^a-z0-9]+", "-");
        return trimDashes(key);
    }

    private static String displayName(ItemJutsu.JutsuEnum jutsu, String key) {
        try {
            String value = jutsu.getName();
            if (value != null && value.trim().length() > 0 && !value.equals(jutsu.unlocalizedName)) {
                return cleanDisplayName(value, key);
            }
        } catch (Throwable ignored) {
        }
        String[] words = key.replace('-', ' ').split(" ");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (word.length() == 0) {
                continue;
            }
            if (name.length() > 0) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return cleanDisplayName(name.length() == 0 ? "Jutsu " + (jutsu.index + 1) : name.toString(), key);
    }

    private static String cleanDisplayName(String value, String fallbackKey) {
        String clean = value == null ? "" : value.trim();
        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.startsWith("entity.") || lower.startsWith("item.")
                || lower.startsWith("tile.") || lower.endsWith(".name")
                || lower.indexOf(':') >= 0 && clean.indexOf(' ') < 0) {
            String key = stableKey(clean);
            clean = title(key.isEmpty() ? fallbackKey : key);
        }
        clean = clean.replaceAll("(?i)^(entity|item|tile)\\s+", "").trim();
        return clean.isEmpty() ? title(fallbackKey) : clean;
    }

    private static String canonicalName(String value) {
        String key = stableKey(value).replace("-jutsu", "").replace("-technique", "");
        if (key.startsWith("ninja-art-")) key = key.substring("ninja-art-".length());
        return key.isEmpty() ? "unnamed" : key;
    }

    private static String category(ItemJutsu.JutsuEnum jutsu, String id, String display) {
        if (jutsu != null) {
            try {
                String type = String.valueOf(jutsu.getType());
                String mapped = typeCategory(type);
                if (!mapped.isEmpty()) return mapped;
            } catch (Throwable ignored) {
            }
        }
        String key = (id + " " + display).toLowerCase(Locale.ROOT);
        if (contains(key, "katon", "fire release", "fire style")) return "Fire Release";
        if (contains(key, "suiton", "water release", "water style")) return "Water Release";
        if (contains(key, "futon", "wind release", "wind style")) return "Wind Release";
        if (contains(key, "raiton", "lightning release", "lightning style")) return "Lightning Release";
        if (contains(key, "doton", "earth release", "earth style")) return "Earth Release";
        if (contains(key, "sharingan", "byakugan", "rinnegan", "tenseigan", "dojutsu")) return "Dojutsu";
        if (contains(key, "medical", "iryo", "healing")) return "Medical";
        if (contains(key, "sage", "senjutsu")) return "Senjutsu";
        if (contains(key, "taijutsu", "eight gates")) return "Taijutsu";
        if (contains(key, "genjutsu", "illusion")) return "Genjutsu";
        if (contains(key, "summon", "kuchiyose")) return "Summoning";
        return "Other";
    }

    private static String typeCategory(String type) {
        if ("KATON".equals(type)) return "Fire Release";
        if ("SUITON".equals(type)) return "Water Release";
        if ("FUTON".equals(type)) return "Wind Release";
        if ("RAITON".equals(type)) return "Lightning Release";
        if ("DOTON".equals(type)) return "Earth Release";
        if ("YOTON".equals(type) || "YOOTON".equals(type)) return "Lava Release";
        if ("FUTTON".equals(type)) return "Boil Release";
        if ("HYOTON".equals(type)) return "Ice Release";
        if ("RANTON".equals(type)) return "Storm Release";
        if ("SHAKUTON".equals(type)) return "Scorch Release";
        if ("BAKUTON".equals(type)) return "Explosion Release";
        if ("JITON".equals(type)) return "Magnet Release";
        if ("MOKUTON".equals(type)) return "Wood Release";
        if ("JINTON".equals(type)) return "Dust Release";
        if ("INTON".equals(type)) return "Yin Release";
        if ("IRYO".equals(type)) return "Medical";
        if ("KUCHIYOSE".equals(type)) return "Summoning";
        if ("SENJUTSU".equals(type) || "SIXPATHSENJUTSU".equals(type)) return "Senjutsu";
        if ("SHARINGAN".equals(type) || "BYAKUGAN".equals(type)
                || "RINNEGAN".equals(type) || "TENSEIGAN".equals(type)) return "Dojutsu";
        if ("NINJUTSU".equals(type)) return "Ninjutsu";
        if ("OTHER".equals(type)) return "Other";
        return type == null || type.isEmpty() ? "" : title(type);
    }

    private static boolean contains(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private static boolean restrictedItem(Item item) {
        if (item == null) return false;
        if (ShinobiAddonRestrictionHandler.shouldHideItem(item)) return true;
        for (ItemStack stack : ShinobiAddonRestrictionHandler.visibilityStacksFor(item)) {
            if (ShinobiAddonRestrictionHandler.shouldHide(stack)) return true;
        }
        return false;
    }

    private static String trimDashes(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '-') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '-') {
            end--;
        }
        return value.substring(start, end);
    }

    private static final class ExternalActivation {
        private final Method method;
        private final String id;

        private ExternalActivation(Method method, String id) {
            this.method = method;
            this.id = id;
        }

        private boolean activate(EntityLivingBase target, int durationTicks) {
            if (target == null || target.world.isRemote) {
                return false;
            }
            try {
                method.invoke(null, target, id, Integer.valueOf(Math.max(1, durationTicks)));
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }
}
