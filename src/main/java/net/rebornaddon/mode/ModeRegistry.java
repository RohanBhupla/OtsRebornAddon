package net.rebornaddon.mode;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.rebornaddon.chakra.ChakraMode;
import net.rebornaddon.chakra.ChakraModeHandler;
import net.rebornaddon.jutsu.JutsuDefinition;
import net.rebornaddon.jutsu.JutsuRegistry;
import net.narutomod.item.ItemSenjutsu;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ModeRegistry {
    public static final ModeRegistry INSTANCE = new ModeRegistry();
    public static final String GLOBAL_ID = "server:global";

    private final Map<String, ModeDefinition> modes = new LinkedHashMap<String, ModeDefinition>();
    private final Map<String, Method> methods = new HashMap<String, Method>();

    private ModeRegistry() {
    }

    public synchronized void discover() {
        modes.clear();
        add(GLOBAL_ID, "Global Player Balance", "server", "global", "global",
                "Native: normal player attributes. These values are additional server adjustments.", 1.0D);
        String sage = "Native NarutoMod: +60 attack, 2.5x movement, +80 health, +2 reach, 3x attack speed. The installed Toad, Snake, and Slug variants use the same movement modifier.";
        add("narutomod:toad_sage_mode", "Toad Sage Mode", "narutomod", "sage_toad", "sage", sage, 2.5D);
        add("narutomod:snake_sage_mode", "Snake Sage Mode", "narutomod", "sage_snake", "sage", sage, 2.5D);
        add("narutomod:slug_sage_mode", "Slug Sage Mode", "narutomod", "sage_slug", "sage", sage, 2.5D);
        add("narutomod:karma_mode", "Karma Mode", "narutomod", "karma", "karma",
                "Native: +30 attack, 2.5x movement, +60 health, +2 reach, 3x attack speed.", 2.5D);
        add("ahznbcursemarkaddon:cursemark_mode", "Curse Mark", "ahznbcursemarkaddon", "cursemark", "cursemark",
                "Native: +45 attack, 2.5x movement, +60 health, +2.2 reach, 3.5x attack speed.", 2.5D);
        add("ahznbcursemarkaddon:byakugou_mode", "Strength of a Hundred Seal (Byakugou)", "ahznbcursemarkaddon", "byakugou", "byakugou",
                "Native: +40 attack, 2x movement, +100 health, +2 reach, 2.5x attack speed.", 2.0D);
        add("narutomod:eight_gates", "Eight Gates", "narutomod", "gates", "eightgates",
                "Native: dynamic attributes based on the opened gate and battle XP.", 1.0D);
        add("narutomod:six_path_senjutsu", "Six Paths Senjutsu", "narutomod", "sixpaths", "sixpath",
                "Native: NarutoMod Six Paths attributes; values below apply afterward.", 1.0D);
        add("narutomod:tenseigan_chakra_mode", "Tenseigan Chakra Mode", "narutomod", "tenseigan", "tenseigan",
                "Native: NarutoMod Tenseigan attributes; values below apply afterward.", 1.0D);
        add("narutomod:biju_cloak", "Tailed Beast Cloak", "narutomod", "biju", "bijucloak",
                "Native: dynamic attributes based on beast, cloak stage, and cloak XP.", 1.0D);
        add("narutomod:wind_chakra_mode", "Wind Chakra Mode", "narutomod", "generic", "windchakramode",
                "Native: Speed VII (2.4x), plus NarutoMod chakra-mode damage and defense.", 2.4D);
        add("narutomod:lightning_chakra_mode", "Lightning Chakra Mode", "narutomod", "generic", "lightningchakramode",
                "Native: Speed XXXIII (7.6x), plus NarutoMod chakra-mode damage and defense.", 7.6D);
        add("narutomod:lava_chakra_mode", "Lava Chakra Mode", "narutomod", "generic", "lavachakramode",
                "Native: NarutoMod lava-mode damage and defense; movement remains 1x.", 1.0D);
        add("rebornaddon:water_chakra_mode", "Water Chakra Mode", "rebornaddon", "water", "waterchakra",
                "Native: Speed IV (1.8x), Strength III, and Resistance II.", 1.8D);
        add("rebornaddon:rain_chakra_mode", "Rain Chakra Mode", "rebornaddon", "rain", "rainchakra",
                "Native: Speed VI (2.2x), Strength IV, Haste IV, and Resistance II.", 2.2D);
        add("rebornaddon:earth_chakra_mode", "Earth Chakra Mode", "rebornaddon", "earth", "earthchakra",
                "Native: 1x movement, Strength VI, Resistance IV, and Fire Resistance.", 1.0D);
    }

    public synchronized void clear() {
        modes.clear();
        methods.clear();
    }

    public ModeDefinition get(String id) { return modes.get(id); }

    public List<ModeDefinition> all() {
        List<ModeDefinition> result = new ArrayList<ModeDefinition>(modes.values());
        Collections.sort(result);
        ModeDefinition global = modes.get(GLOBAL_ID);
        if (global != null) {
            result.remove(global);
            result.add(0, global);
        }
        return Collections.unmodifiableList(result);
    }

    public ModeDefinition forJutsu(EntityPlayer player, JutsuDefinition jutsu) {
        if (jutsu == null) return null;
        ModeDefinition direct = modeForAdvancement(jutsu.id(), jutsu.displayName());
        if (direct != null && !direct.detector().startsWith("sage_")) return direct;
        String displayCandidate = canonical(jutsu.displayName());
        String idCandidate = canonicalPath(jutsu.id());
        if ("sage".equals(displayCandidate) || "sage".equals(idCandidate)) {
            return sageMode(player);
        }
        return direct;
    }

    ModeDefinition modeForAdvancement(String jutsuId, String displayName) {
        String displayCandidate = canonical(displayName);
        String idCandidate = canonicalPath(jutsuId);
        ModeDefinition best = null;
        for (ModeDefinition mode : modes.values()) {
            if (GLOBAL_ID.equals(mode.id())) continue;
            String modeName = canonical(mode.displayName());
            String modeId = canonicalPath(mode.id());
            String token = canonical(mode.token());
            if (matchesModeCandidate(displayCandidate, modeName, modeId, token)
                    || matchesModeCandidate(idCandidate, modeName, modeId, token)) {
                if (best == null || "narutomod".equals(mode.source())) best = mode;
            }
        }
        return best;
    }

    private static boolean matchesModeCandidate(String candidate, String name, String id,
                                                String token) {
        return !candidate.isEmpty() && (candidate.equals(name) || candidate.equals(id)
                || !token.isEmpty() && candidate.equals(token));
    }

    private static String canonicalPath(String value) {
        String path = value == null ? "" : value;
        int colon = path.indexOf(':');
        if (colon >= 0 && colon + 1 < path.length()) path = path.substring(colon + 1);
        return canonical(path);
    }

    public ModeDefinition forJutsu(JutsuDefinition jutsu) {
        return forJutsu(null, jutsu);
    }

    /** Resolves actual mode-learning items without treating ordinary named jutsu as modes. */
    public ModeDefinition learnerMode(EntityPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.getItem() instanceof ItemSenjutsu.RangedItem) {
            ItemSenjutsu.Type type = ((ItemSenjutsu.RangedItem) stack.getItem()).getSageType(stack);
            if (type == ItemSenjutsu.Type.SNAKE) return modes.get("narutomod:snake_sage_mode");
            if (type == ItemSenjutsu.Type.SLUG) return modes.get("narutomod:slug_sage_mode");
            if (type == ItemSenjutsu.Type.TOAD) return modes.get("narutomod:toad_sage_mode");
        }
        ResourceLocation registryName = stack.getItem().getRegistryName();
        String path = registryName == null ? "" : canonical(registryName.getResourcePath());
        if (path.isEmpty()) return null;
        for (ModeDefinition mode : modes.values()) {
            if (GLOBAL_ID.equals(mode.id()) || mode.detector().startsWith("sage_")) continue;
            String token = canonical(mode.token());
            if (!token.isEmpty() && (path.equals(token) || path.equals(token + "jutsu")
                    || path.equals(token + "learner") || path.contains(token + "mode")
                    || path.contains("scroll" + token))) {
                return mode;
            }
        }
        return null;
    }

    public boolean active(EntityPlayer player, ModeDefinition mode) {
        if (player == null || mode == null) return false;
        if (GLOBAL_ID.equals(mode.id())) return true;
        if (mode.detector().startsWith("sage_")) {
            ModeDefinition activeSage = sageMode(player);
            return activeSage != null && activeSage.id().equals(mode.id())
                    && ItemSenjutsu.isSageModeActivated(player);
        }
        if ("karma".equals(mode.detector())) return invokeBoolean(
                "net.narutomod.item.ItemKarma", "isKarmaModeActivated", player);
        if ("cursemark".equals(mode.detector())) return invokeBoolean(
                "net.mcreator.ahznbcursemarkaddon.item.ItemCursemarkMode", "isCursemarkActivated", player);
        if ("byakugou".equals(mode.detector())) return invokeBoolean(
                "net.mcreator.ahznbcursemarkaddon.item.ItemByakugouJutsu", "isByakugouModeActivated", player);
        if ("gates".equals(mode.detector())) return invokeInt(
                "net.narutomod.item.ItemEightGates", "getGatesOpened", player) > 0;
        if ("sixpaths".equals(mode.detector())) return player.getEntityData().hasKey("6pSenjutsuItem");
        if ("tenseigan".equals(mode.detector())) return equippedContains(player, "tenseigan");
        if ("biju".equals(mode.detector())) return equippedContains(player, "biju_cloak")
                || equippedContains(player, "bijucloak");
        if ("water".equals(mode.detector())) return ChakraModeHandler.INSTANCE.isActive(player, ChakraMode.WATER);
        if ("rain".equals(mode.detector())) return ChakraModeHandler.INSTANCE.isActive(player, ChakraMode.RAIN);
        if ("earth".equals(mode.detector())) return ChakraModeHandler.INSTANCE.isActive(player, ChakraMode.EARTH);
        return hasActiveFlag(player, mode.token()) || equippedContains(player, mode.token());
    }

    public boolean deactivate(EntityPlayer player, ModeDefinition mode) {
        if (player == null || mode == null) return false;
        if (mode.detector().startsWith("sage_")) return invokeVoid(
                "net.narutomod.item.ItemSenjutsu", "deactivateSageMode", player);
        if ("karma".equals(mode.detector())) return invokeVoid(
                "net.narutomod.item.ItemKarma", "deactivateKarmaMode", player);
        if ("cursemark".equals(mode.detector())) return invokeVoid(
                "net.mcreator.ahznbcursemarkaddon.item.ItemCursemarkMode", "deactivateCursemarkMode", player);
        if ("byakugou".equals(mode.detector())) return invokeVoid(
                "net.mcreator.ahznbcursemarkaddon.item.ItemByakugouJutsu", "deactivateByakugouMode", player);
        if ("water".equals(mode.detector())) return deactivateChakra(player, ChakraMode.WATER);
        if ("rain".equals(mode.detector())) return deactivateChakra(player, ChakraMode.RAIN);
        if ("earth".equals(mode.detector())) return deactivateChakra(player, ChakraMode.EARTH);
        return false;
    }

    private boolean deactivateChakra(EntityPlayer player, ChakraMode mode) {
        if (!(player instanceof net.minecraft.entity.player.EntityPlayerMP)) return false;
        ChakraModeHandler.INSTANCE.deactivateFromLearner(
                (net.minecraft.entity.player.EntityPlayerMP) player, mode);
        return true;
    }

    private void add(String id, String name, String source, String detector, String token,
                     String nativeSummary, double nativeMovementMultiplier) {
        modes.put(id, new ModeDefinition(id, name, source, detector, token, nativeSummary,
                nativeMovementMultiplier));
    }

    private ModeDefinition sageMode(EntityPlayer player) {
        if (player == null) return modes.get("narutomod:toad_sage_mode");
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack.isEmpty() || !(stack.getItem() instanceof ItemSenjutsu.RangedItem)) continue;
            ItemSenjutsu.Type type = ((ItemSenjutsu.RangedItem) stack.getItem()).getSageType(stack);
            if (type == ItemSenjutsu.Type.SNAKE) return modes.get("narutomod:snake_sage_mode");
            if (type == ItemSenjutsu.Type.SLUG) return modes.get("narutomod:slug_sage_mode");
            if (type == ItemSenjutsu.Type.TOAD) return modes.get("narutomod:toad_sage_mode");
        }
        return null;
    }

    private static String canonical(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "")
                .replace("chakra", "").replace("mode", "").replace("jutsu", "");
    }

    private static boolean equipped(EntityPlayer player, String id) {
        for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
            ResourceLocation name = player.getItemStackFromSlot(slot).getItem().getRegistryName();
            if (name != null && id.equals(name.toString())) return true;
        }
        return false;
    }

    private static boolean equippedContains(EntityPlayer player, String token) {
        String wanted = canonical(token);
        if (wanted.isEmpty()) return false;
        for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
            ItemStack stack = player.getItemStackFromSlot(slot);
            ResourceLocation name = stack.isEmpty() ? null : stack.getItem().getRegistryName();
            if (name != null && canonical(name.toString()).contains(wanted)) return true;
        }
        return false;
    }

    private static boolean hasActiveFlag(EntityPlayer player, String token) {
        String wanted = canonical(token);
        if (wanted.isEmpty()) return false;
        if (activeFlag(player.getEntityData(), wanted, 0)) return true;
        if (player.getEntityData().hasKey(EntityPlayer.PERSISTED_NBT_TAG, 10)
                && activeFlag(player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG), wanted, 0)) {
            return true;
        }
        for (ItemStack stack : player.inventory.mainInventory) {
            if (!stack.isEmpty() && stack.hasTagCompound()
                    && activeFlag(stack.getTagCompound(), wanted, 0)) return true;
        }
        return false;
    }

    private static boolean activeFlag(NBTTagCompound data, String token, int depth) {
        if (data == null || depth > 2) return false;
        for (String key : data.getKeySet()) {
            String normalized = canonical(key);
            NBTBase value = data.getTag(key);
            if (normalized.contains(token) && (normalized.contains("active")
                    || normalized.contains("activated") || normalized.contains("stage"))) {
                if (value != null && value.getId() >= 1 && value.getId() <= 6
                        && data.getLong(key) > 0L) return true;
            }
            if (value instanceof NBTTagCompound
                    && activeFlag((NBTTagCompound) value, token, depth + 1)) return true;
        }
        return false;
    }

    private boolean invokeBoolean(String className, String name, EntityPlayer player) {
        try {
            Object result = method(className, name).invoke(null, player);
            return result instanceof Boolean && ((Boolean) result).booleanValue();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int invokeInt(String className, String name, EntityPlayer player) {
        try {
            Object result = method(className, name).invoke(null, player);
            return result instanceof Number ? ((Number) result).intValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private boolean invokeVoid(String className, String name, EntityPlayer player) {
        try {
            method(className, name).invoke(null, player);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Method method(String className, String name) throws Exception {
        String key = className + '#' + name;
        Method method = methods.get(key);
        if (method == null) {
            Class<?> type = Class.forName(className);
            for (Method candidate : type.getMethods()) {
                if (candidate.getName().equals(name) && candidate.getParameterTypes().length == 1) {
                    method = candidate;
                    break;
                }
            }
            if (method == null) throw new NoSuchMethodException(key);
            methods.put(key, method);
        }
        return method;
    }
}
