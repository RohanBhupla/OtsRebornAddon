package net.rebornaddon.store;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.init.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemDoor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.rebornaddon.compat.ShinobiAddonRestrictionHandler;
import net.rebornaddon.village.Village;
import net.rebornaddon.village.VillageRoles;
import net.rebornaddon.policy.ContentPolicyService;
import net.rebornaddon.policy.PolicyAction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class StoreCatalog {
    public static final String ARMOR = "armor";
    public static final String WEAPONS = "weapons";
    public static final String FOOD = "food";
    public static final String BUILDING = "building";
    public static final String TOOLS = "tools";
    public static final String MUSIC = "music";
    public static final String SCROLLS = "scrolls";

    public static final String ARMOR_VILLAGE = "village";
    public static final String ARMOR_ANBU = "anbu";
    public static final String ARMOR_KAGE = "kage";
    public static final String QUOTA_ANBU_MASK = "anbu_mask";
    public static final String QUOTA_ANBU_CLOAK = "anbu_cloak";
    public static final String QUOTA_KAGE_HAT = "kage_hat";
    public static final String QUOTA_KAGE_ROBE = "kage_robe";
    public static final String QUOTA_AKATSUKI_HEADBAND = "akatsuki_headband";
    public static final String QUOTA_AKATSUKI_HAT = "akatsuki_hat";
    public static final String QUOTA_AKATSUKI_ROBE = "akatsuki_robe";

    public static final String BUILDING_BLOCKS = "blocks";
    public static final String BUILDING_DECORATION = "decoration";
    public static final String BUILDING_DOORS = "doors";
    public static final String BUILDING_NATURE = "nature";
    public static final String BUILDING_LIGHTING = "lighting";
    public static final String BUILDING_FURNITURE = "furniture";
    public static final String BUILDING_HARDWARE = "hardware";

    public static final String SCROLL_ALL = "all";
    public static final String SCROLL_FIRE = "fire";
    public static final String SCROLL_WATER = "water";
    public static final String SCROLL_WIND = "wind";
    public static final String SCROLL_LIGHTNING = "lightning";
    public static final String SCROLL_EARTH = "earth";
    public static final String SCROLL_KEKKEI = "kekkei";
    public static final String SCROLL_MEDICAL = "medical";
    public static final String SCROLL_SEALING = "sealing";
    public static final String SCROLL_DOJUTSU = "dojutsu";
    public static final String SCROLL_TAIJUTSU = "taijutsu";
    public static final String SCROLL_SUMMONING = "summoning";
    public static final String SCROLL_OTHER = "other";

    public static final List<String> CATEGORIES = Collections.unmodifiableList(Arrays.asList(
            ARMOR, WEAPONS, FOOD, BUILDING, TOOLS, MUSIC, SCROLLS));
    public static final List<String> BUILDING_CATEGORIES = Collections.unmodifiableList(Arrays.asList(
            BUILDING_BLOCKS, BUILDING_DECORATION, BUILDING_DOORS,
            BUILDING_NATURE, BUILDING_LIGHTING, BUILDING_FURNITURE, BUILDING_HARDWARE));
    public static final List<String> ARMOR_CATEGORIES = Collections.unmodifiableList(Arrays.asList(
            ARMOR_VILLAGE, ARMOR_ANBU, ARMOR_KAGE));
    public static final List<String> SCROLL_CATEGORIES = Collections.unmodifiableList(Arrays.asList(
            SCROLL_ALL, SCROLL_FIRE, SCROLL_WATER, SCROLL_WIND, SCROLL_LIGHTNING,
            SCROLL_EARTH, SCROLL_MEDICAL, SCROLL_SEALING,
            SCROLL_DOJUTSU, SCROLL_TAIJUTSU, SCROLL_SUMMONING, SCROLL_OTHER));

    private static final Set<String> EXCLUDED_STORE_DOMAINS = new HashSet<String>(Arrays.asList(
            "customnpcs", "armourers_workshop", "armourers", "betterquesting",
            "ftbquests", "itemfilters", "npcintegration"));
    private static final Set<String> EXCLUDED_WEAPONS = new HashSet<String>(Arrays.asList(
            "narutomod:asuracanon", "narutomod:asura_canon", "narutomod:asura_cannon",
            "narutomod:clematis_dance_vine", "narutomod:clemantis_dance_vine",
            "narutomod:bone_sword", "variedcommodities:chicken_sword"));
    private static final String[] EXCLUDED_WEAPON_WORDS = new String[] {
            "gunbai", "crossbow_bolt", "crescentstaff", "crescent_staff",
            "zabuza_sword", "kubikiribocho", "samehada", "kiba_blade", "kiba_sword",
            "hiramekarei", "kabutowari", "nuibari", "shibuki_sword",
            "kagutsuchi_sword", "kagutsuchisword", "susanoo_kagutsuchi",
            "ice_senbon", "kamui_shuriken", "kamuishuriken", "kusanagi",
            "kunai_hiraishin", "hiraishin_kunai", "scythe_hidan", "scythe_3blade",
            "scythe_3_blade", "scythe_madara", "madara_scythe", "violin_bow", "whip",
            "ishiken", "yagura_staff", "yagurastaff", "trident"
    };
    private static final String[] STORAGE_BLOCK_MATERIALS = new String[] {
            "coal", "iron", "gold", "lapis", "redstone", "diamond", "emerald", "quartz",
            "copper", "tin", "silver", "lead", "nickel", "aluminum", "aluminium", "bronze",
            "steel", "uranium", "platinum", "mithril", "ruby", "sapphire", "amethyst"
    };

    private static final Set<String> REDSTONE_ITEMS = new HashSet<String>(Arrays.asList(
            "minecraft:redstone", "minecraft:redstone_torch", "minecraft:unlit_redstone_torch",
            "minecraft:repeater", "minecraft:unpowered_repeater", "minecraft:powered_repeater",
            "minecraft:comparator", "minecraft:unpowered_comparator", "minecraft:powered_comparator",
            "minecraft:lever", "minecraft:tripwire_hook", "minecraft:daylight_detector",
            "minecraft:daylight_detector_inverted", "minecraft:observer", "minecraft:piston",
            "minecraft:sticky_piston", "minecraft:hopper", "minecraft:dispenser", "minecraft:dropper",
            "minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block",
            "minecraft:structure_block"));
    private static final String[] REDSTONE_WORDS = new String[] {
            "redstone", "repeater", "comparator", "pressure_plate", "tripwire", "daylight_detector",
            "observer", "piston", "hopper", "dispenser", "dropper", "command_block", "structure_block"
    };
    private static final String[] ANBU_WORDS = new String[] {"anbu", "root_mask", "root_armor"};
    private static final String[] KAGE_WORDS = new String[] {
            "hokage", "mizukage", "kazekage", "raikage", "tsuchikage", "amekage", "tsukikage",
            "kage_robe", "kagerobe", "kage_hat", "kagehat"
    };
    private static final String[] ARMOR_WORDS = new String[] {
            "armor", "armour", "robe", "headband", "forehead", "protector", "helmet", "chestplate",
            "leggings", "boots", "pants", "shirt", "clothes", "cloak", "mask"
    };
    private static final String[] WEAPON_WORDS = new String[] {
            "sword", "katana", "kunai", "shuriken", "senbon", "dagger", "spear", "glaive",
            "halberd", "scythe", "trident", "warhammer", "battleaxe", "mace", "staff", "wand",
            "gun", "rifle", "pistol", "cannon", "canon", "bow", "crossbow", "claw", "weapon",
            "shakujo", "totsuka", "blade"
    };
    private static final String[] TOOL_WORDS = new String[] {
            "pickaxe", "shovel", "spade", "hoe", "hatchet", "hammer", "wrench", "drill",
            "fishing_rod", "shears", "flint_and_steel"
    };

    private static volatile List<Entry> cachedEntries;
    private static volatile Map<String, Entry> cachedByKey;
    private static volatile List<Scope> cachedScopes;
    private static volatile Map<String, Scope> cachedScopesByKey;

    private StoreCatalog() {
    }

    public static List<Entry> entries() {
        List<Entry> cached = cachedEntries;
        if (cached != null) {
            return cached;
        }
        synchronized (StoreCatalog.class) {
            if (cachedEntries == null) {
                build();
            }
            return cachedEntries;
        }
    }

    public static Entry find(String key) {
        entries();
        return key == null ? null : cachedByKey.get(key.toLowerCase(Locale.ROOT));
    }

    public static List<String> keys() {
        List<String> keys = new ArrayList<String>();
        for (Entry entry : entries()) {
            keys.add(entry.key());
        }
        return keys;
    }

    public static void clear() {
        cachedEntries = null;
        cachedByKey = null;
        cachedScopes = null;
        cachedScopesByKey = null;
    }

    public static List<Scope> scopes() {
        entries();
        List<Scope> cached = cachedScopes;
        if (cached != null) return cached;
        synchronized (StoreCatalog.class) {
            if (cachedScopes == null) buildScopes();
            return cachedScopes;
        }
    }

    public static Scope findScope(String key) {
        scopes();
        return key == null ? null : cachedScopesByKey.get(key.toLowerCase(Locale.ROOT));
    }

    public static String categoryScope(String category) {
        return "category:" + cleanScopePart(category);
    }

    public static String subcategoryScope(String category, String subcategory) {
        return "subcategory:" + cleanScopePart(category) + ":" + cleanScopePart(subcategory);
    }

    public static String tierScope(int tier) {
        return "tier:armor:" + Math.max(0, Math.min(4, tier));
    }

    public static boolean routeEnabled(Entry entry) {
        if (entry == null || ContentPolicyService.INSTANCE.denies(PolicyAction.STORE, entry.stack())
                || !StoreSettingsCache.INSTANCE.scopeEnabled(categoryScope(entry.category()))) {
            return false;
        }
        if (!entry.subcategory().isEmpty()
                && !StoreSettingsCache.INSTANCE.scopeEnabled(
                subcategoryScope(entry.category(), entry.subcategory()))) {
            return false;
        }
        return !ARMOR.equals(entry.category())
                || StoreSettingsCache.INSTANCE.scopeEnabled(tierScope(entry.tier()));
    }

    private static void buildScopes() {
        Map<String, Scope> result = new LinkedHashMap<String, Scope>();
        for (Entry entry : cachedEntries) {
            addScope(result, new Scope(categoryScope(entry.category()), "category",
                    entry.category(), "", -1));
            if (!entry.subcategory().isEmpty()) {
                addScope(result, new Scope(subcategoryScope(entry.category(), entry.subcategory()),
                        "subcategory", entry.category(), entry.subcategory(), -1));
            }
            if (ARMOR.equals(entry.category())) {
                addScope(result, new Scope(tierScope(entry.tier()), "tier",
                        ARMOR, "", entry.tier()));
            }
        }
        cachedScopes = Collections.unmodifiableList(new ArrayList<Scope>(result.values()));
        cachedScopesByKey = Collections.unmodifiableMap(result);
    }

    private static void addScope(Map<String, Scope> scopes, Scope scope) {
        if (!scopes.containsKey(scope.key())) scopes.put(scope.key(), scope);
    }

    private static String cleanScopePart(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "");
    }

    public static boolean isRestrictedCraftingOutput(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() instanceof ItemBlock) {
            return false;
        }
        Item item = stack.getItem();
        if (item instanceof ItemArmor || item instanceof ItemSword || item instanceof ItemBow
                || item instanceof ItemTool || item instanceof ItemHoe
                || item == Items.SHIELD || item == Items.SHEARS
                || item == Items.FISHING_ROD || item == Items.FLINT_AND_STEEL) {
            return true;
        }
        String descriptor = descriptor(stack);
        return containsAny(descriptor, ARMOR_WORDS)
                || containsAny(descriptor, WEAPON_WORDS)
                || containsAny(descriptor, TOOL_WORDS);
    }

    public static boolean allowedFor(Entry entry, Village village, Set<String> groups) {
        if (entry == null) {
            return false;
        }
        if (!ARMOR.equals(entry.category())) {
            return true;
        }
        if (ARMOR_ANBU.equals(entry.subcategory())) {
            return village != null && VillageRoles.isKage(groups, village);
        }
        if (ARMOR_KAGE.equals(entry.subcategory())) {
            return village != null && village == entry.village()
                    && VillageRoles.isKage(groups, village);
        }
        if (entry.akatsuki()) {
            return containsGroup(groups, "akatsuki");
        }
        return village != null && village == entry.village();
    }

    private static void build() {
        List<Entry> result = new ArrayList<Entry>();
        Map<String, Entry> byKey = new LinkedHashMap<String, Entry>();
        for (Item item : ForgeRegistries.ITEMS.getValuesCollection()) {
            for (ItemStack stack : variants(item)) {
                try {
                    Entry classified = classify(stack);
                    for (Entry entry : expandTiers(classified)) {
                        if (!byKey.containsKey(entry.key())) {
                            byKey.put(entry.key(), entry);
                            result.add(entry);
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        addExplicitDoor(result, byKey, Items.OAK_DOOR);
        addExplicitDoor(result, byKey, Items.SPRUCE_DOOR);
        addExplicitDoor(result, byKey, Items.BIRCH_DOOR);
        addExplicitDoor(result, byKey, Items.JUNGLE_DOOR);
        addExplicitDoor(result, byKey, Items.ACACIA_DOOR);
        addExplicitDoor(result, byKey, Items.DARK_OAK_DOOR);
        Collections.sort(result, new Comparator<Entry>() {
            @Override
            public int compare(Entry left, Entry right) {
                int category = Integer.compare(CATEGORIES.indexOf(left.category()), CATEGORIES.indexOf(right.category()));
                if (category != 0) {
                    return category;
                }
                int name = left.displayName().compareToIgnoreCase(right.displayName());
                return name != 0 ? name : left.key().compareTo(right.key());
            }
        });
        cachedEntries = Collections.unmodifiableList(result);
        cachedByKey = Collections.unmodifiableMap(byKey);
    }

    private static Entry classify(ItemStack stack) {
        Item item = stack.getItem();
        ResourceLocation name = item.getRegistryName();
        if (name == null || ShinobiAddonRestrictionHandler.shouldHide(stack)
                || ShinobiAddonRestrictionHandler.shouldHideItem(item)) {
            return null;
        }
        String descriptor = descriptor(stack);
        String domain = name.getResourceDomain();
        String path = name.getResourcePath().toLowerCase(Locale.ROOT);
        String registryName = name.toString().toLowerCase(Locale.ROOT);

        if (EXCLUDED_STORE_DOMAINS.contains(domain)) {
            return null;
        }
        if ("chinjufumod".equals(domain)) {
            if (isChinjufuFoodPath(path) || isFood(stack, descriptor)) {
                return entry(stack, FOOD, "", 100, null, false, "");
            }
            if (!allowsChinjufuStoreItem(path, false,
                    item instanceof ItemBlock || item instanceof ItemDoor)) {
                return null;
            }
        }
        if (descriptor.contains("netherite")) {
            return null;
        }
        if ("rebornaddon".equals(domain) && path.startsWith("record_")) {
            return entry(stack, MUSIC, "", 2500, null, false, "");
        }
        if (isJutsuScroll(domain, path, descriptor)) {
            String category = scrollCategory(descriptor);
            return isExcludedJutsuScroll(descriptor, category) ? null
                    : entry(stack, SCROLLS, category, 25000, null, false, "");
        }

        if (isArmor(stack, descriptor)) {
            boolean anbu = containsAny(descriptor, ANBU_WORDS);
            Village kageVillage = kageVillage(descriptor);
            boolean kage = kageVillage != null || containsAny(descriptor, KAGE_WORDS)
                    || hasToken(descriptor, "kage");
            if (anbu) {
                String quota = headPiece(descriptor) ? QUOTA_ANBU_MASK
                        : bodyPiece(descriptor) ? QUOTA_ANBU_CLOAK : "";
                return quota.isEmpty() ? null : entry(stack, ARMOR, ARMOR_ANBU,
                        defaultArmorPrice(descriptor), null, false, quota);
            }
            if (kage) {
                String quota = headPiece(descriptor) ? QUOTA_KAGE_HAT
                        : bodyPiece(descriptor) ? QUOTA_KAGE_ROBE : "";
                return kageVillage == null || quota.isEmpty() ? null
                        : entry(stack, ARMOR, ARMOR_KAGE, defaultArmorPrice(descriptor),
                        kageVillage, false, quota);
            }
            boolean akatsuki = descriptor.contains("akatsuki") || isRogueProtector(descriptor);
            Village village = akatsuki ? null : village(descriptor);
            if (!akatsuki && village == null) {
                return null;
            }
            String quota = akatsukiQuota(descriptor);
            return entry(stack, ARMOR, ARMOR_VILLAGE, defaultArmorPrice(descriptor),
                    village, akatsuki, quota);
        }
        if (isWeaponRack(path, descriptor)) {
            return entry(stack, BUILDING, BUILDING_FURNITURE, 25, null, false, "");
        }
        if (EXCLUDED_WEAPONS.contains(registryName)
                || containsAny(descriptor, EXCLUDED_WEAPON_WORDS)
                || containsAny(descriptor, new String[] {"asura_canon", "asura_cannon",
                "clematis_dance_vine", "clemantis_dance_vine", "chicken_sword", "bone_sword"})
                || containsAny(descriptor, new String[] {"clematis", "clementis", "clemantis"})
                && descriptor.contains("vine")) {
            return null;
        }
        if (isWeapon(stack, descriptor)) {
            return "minecraft".equals(domain) || "futuremc".equals(domain) ? null
                    : entry(stack, WEAPONS, "", 12500, null, false, "");
        }
        if (isExplosiveTag(path, descriptor)) {
            return entry(stack, WEAPONS, "", 12500, null, false, "");
        }
        if (isFood(stack, descriptor)) {
            return entry(stack, FOOD, "", descriptor.contains("chakra") && descriptor.contains("pill")
                    ? 2500 : 100, null, false, "");
        }
        if (isDiamondTool(item)) {
            return entry(stack, TOOLS, "", 5000, null, false, "");
        }
        if (isBuildingSupply(stack, descriptor) && !isRedstone(stack, descriptor)
                && !isExcludedBuildingSupply(domain, path, descriptor)) {
            return entry(stack, BUILDING, buildingCategory(stack, descriptor), 25, null, false, "");
        }
        return null;
    }

    private static boolean isArmor(ItemStack stack, String descriptor) {
        return stack.getItem() instanceof ItemArmor || containsAny(descriptor, ARMOR_WORDS);
    }

    private static boolean isWeapon(ItemStack stack, String descriptor) {
        Item item = stack.getItem();
        return item instanceof ItemSword || item instanceof ItemBow
                || containsAny(descriptor, WEAPON_WORDS);
    }

    private static boolean isFood(ItemStack stack, String descriptor) {
        return stack.getItem() instanceof ItemFood
                || descriptor.contains("chakra_pill") || descriptor.contains("chakrapill")
                || descriptor.contains("chakra pill");
    }

    static boolean isChinjufuFoodPath(String path) {
        if (path == null) return false;
        String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.startsWith("item_food_") || normalized.startsWith("block_food_")
                || normalized.startsWith("item_bentou") || normalized.startsWith("block_boxh_");
    }

    static boolean allowsChinjufuStoreItem(String path, boolean food, boolean placeable) {
        return food || isChinjufuFoodPath(path) || placeable;
    }

    private static boolean isDiamondTool(Item item) {
        return item == Items.DIAMOND_PICKAXE || item == Items.DIAMOND_AXE
                || item == Items.DIAMOND_SHOVEL || item == Items.DIAMOND_HOE;
    }

    private static boolean isBuildingSupply(ItemStack stack, String descriptor) {
        Item item = stack.getItem();
        if (item instanceof ItemBlock || item instanceof ItemDoor) {
            return true;
        }
        CreativeTabs tab = item.getCreativeTab();
        if (tab == CreativeTabs.BUILDING_BLOCKS || tab == CreativeTabs.DECORATIONS) {
            return true;
        }
        return containsAny(descriptor, new String[] {
                "door", "trapdoor", "sign", "bed", "item_frame", "painting", "flower_pot"
        });
    }

    private static boolean isRedstone(ItemStack stack, String descriptor) {
        ResourceLocation name = stack.getItem().getRegistryName();
        if (name != null && isDoorPath(name.getResourcePath().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (name != null && REDSTONE_ITEMS.contains(name.toString())) {
            return true;
        }
        if (stack.getItem().getCreativeTab() == CreativeTabs.REDSTONE) {
            return true;
        }
        return containsAny(descriptor, REDSTONE_WORDS)
                || descriptor.contains("lever") || descriptor.contains("button");
    }

    private static boolean isWeaponRack(String path, String descriptor) {
        return path.contains("weapon_rack") || path.contains("weaponrack")
                || descriptor.contains("weapon_rack") || descriptor.contains("weaponrack");
    }

    private static boolean isExplosiveTag(String path, String descriptor) {
        return path.contains("explosive_tag") || path.contains("explosivetag")
                || descriptor.contains("explosive_tag") || descriptor.contains("explosivetag");
    }

    private static boolean isExcludedBuildingSupply(String domain, String path, String descriptor) {
        if (path.contains("barrier") || path.contains("beacon") || path.contains("end_crystal")
                || path.contains("endcrystal") || path.contains("portal")
                || path.contains("wither_rose") || path.contains("witherrose")
                || "six_doors".equals(path) || path.contains("sixdoors")) {
            return true;
        }
        if (hasPathToken(path, "ore") || hasPathToken(path, "rail")
                || path.endsWith("_rail") || path.startsWith("rail_")) {
            return true;
        }
        if (path.equals("skull") || hasPathToken(path, "skull")
                || hasPathToken(path, "head") || path.endsWith("_head")) {
            return true;
        }
        if ("variedcommodities".equals(domain)
                && ("lamp".equals(path) || "lamp_unlit".equals(path)
                || "candle".equals(path) || "candle_unlit".equals(path))) {
            return true;
        }
        if (descriptor.contains("title_npclamp") || descriptor.contains("title_npccandle")) {
            return true;
        }
        for (String material : STORAGE_BLOCK_MATERIALS) {
            if (path.equals(material + "_block") || path.equals("block_" + material)
                    || path.equals(material + "block")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDoorPath(String path) {
        return path.endsWith("_door") || path.endsWith("_trapdoor")
                || path.endsWith("_fence_gate") || path.equals("wooden_door")
                || path.equals("iron_door");
    }

    private static boolean hasPathToken(String path, String token) {
        return ("_" + path + "_").contains("_" + token + "_");
    }

    private static String buildingCategory(ItemStack stack, String descriptor) {
        ResourceLocation name = stack.getItem().getRegistryName();
        String domain = name == null ? "" : name.getResourceDomain();
        String path = name == null ? "" : name.getResourcePath().toLowerCase(Locale.ROOT);
        if (containsAny(path, new String[] {"chain", "rope", "hook", "bracket", "post"})) {
            return BUILDING_HARDWARE;
        }
        if (containsAny(path, new String[] {"door", "trapdoor", "fence_gate", "window"})) {
            return BUILDING_DOORS;
        }
        if (containsAny(path, new String[] {"torch", "lantern", "lamp", "light", "candle"})) {
            return BUILDING_LIGHTING;
        }
        if ("cfm".equals(domain) || "mcwfurnitures".equals(domain) || "variedcommodities".equals(domain)
                && containsAny(descriptor, new String[] {"chair", "table", "bench", "shelf", "cabinet", "sofa", "stool"})) {
            return BUILDING_FURNITURE;
        }
        if (containsAny(descriptor, new String[] {"sapling", "flower", "leaves", "log", "grass", "dirt",
                "mushroom", "vine", "cactus", "coral", "bush"})) {
            return BUILDING_NATURE;
        }
        if (stack.getItem().getCreativeTab() == CreativeTabs.DECORATIONS
                || containsAny(descriptor, new String[] {"banner", "carpet", "painting", "pot", "decor", "statue"})) {
            return BUILDING_DECORATION;
        }
        return BUILDING_BLOCKS;
    }

    private static boolean isRogueProtector(String descriptor) {
        return descriptor.contains("rogue_") && containsAny(descriptor,
                new String[] {"headband", "forehead", "protector"});
    }

    private static String akatsukiQuota(String descriptor) {
        if (isRogueProtector(descriptor)) {
            return QUOTA_AKATSUKI_HEADBAND;
        }
        if (headPiece(descriptor)) {
            return QUOTA_AKATSUKI_HAT;
        }
        return bodyPiece(descriptor) ? QUOTA_AKATSUKI_ROBE : "";
    }

    private static void addExplicitDoor(List<Entry> entries, Map<String, Entry> byKey, Item item) {
        if (item == null) return;
        Entry entry = classify(new ItemStack(item));
        if (entry != null && !byKey.containsKey(entry.key())) {
            byKey.put(entry.key(), entry);
            entries.add(entry);
        }
    }

    private static boolean isJutsuScroll(String domain, String path, String descriptor) {
        if (!path.contains("scroll") && !path.contains("jutsu_exp")
                && !path.contains("jutsu_xp") && !descriptor.contains("scroll")) {
            return false;
        }
        if ("minecraft".equals(domain) || "waystones".equals(domain)
                || "variedcommodities".equals(domain) || "patchouli".equals(domain)
                || EXCLUDED_STORE_DOMAINS.contains(domain)) {
            return false;
        }
        if ("earthscroll".equals(path) || "heavenscroll".equals(path)
                || "team_scroll".equals(path) || "ancient_scroll".equals(path)) {
            return false;
        }
        return "rebornaddon".equals(domain) || "narutomod".equals(domain)
                || domain.contains("naruto") || domain.contains("shinobi")
                || domain.contains("jutsu") || domain.contains("kabuto")
                || domain.contains("sharingan") || domain.contains("rinnegan")
                || domain.contains("seals") || domain.contains("ahznb")
                || descriptor.contains("jutsu") || descriptor.contains("chakra_mode")
                || descriptor.contains("medical_scroll");
    }

    private static boolean isExcludedJutsuScroll(String descriptor, String category) {
        if (SCROLL_KEKKEI.equals(category)) {
            return true;
        }
        return containsAny(descriptor, new String[] {
                "scroll_puppet", "puppet_scroll", "scroll_karasu", "scroll_sanshouo",
                "scroll_hiruko", "scroll_3rd_kazekage", "body_replacement", "kawarimi",
                "scroll_hiraishin", "flying_thunder", "flying_raijin", "medical_scroll",
                "scroll_medical", "medical_training", "mind_transfer", "mind_body_transfer",
                "shintenshin", "scroll_transformation", "transformation_scroll", "henge_scroll",
                "scroll_kage_bunshin", "shadow_clone", "kage_bunshin", "shadow_imitation",
                "scroll_shadow_imitation", "kagemane", "scroll_multi_size", "multi_size",
                "yang_release", "yang_style", "kekkei_genkai"
        });
    }

    private static String scrollCategory(String descriptor) {
        if (containsAny(descriptor, new String[] {"lava", "yoton", "ice", "hyoton", "boil", "futton",
                "storm", "ranton", "scorch", "shakuton", "explosion", "bakuton", "wood", "mokuton",
                "magnet", "jiton", "dust", "jinton", "steel", "koton", "crystal", "shoton"})) {
            return SCROLL_KEKKEI;
        }
        if (containsAny(descriptor, new String[] {"medical", "healing", "iryo", "cellular", "byakugou"})) {
            return SCROLL_MEDICAL;
        }
        if (containsAny(descriptor, new String[] {"sealing", "fuin", "seal_", "sealing_chains"})) {
            return SCROLL_SEALING;
        }
        if (containsAny(descriptor, new String[] {"dojutsu", "sharingan", "rinnegan", "byakugan",
                "mangekyo", "izanagi", "izanami", "genjutsu"})) {
            return SCROLL_DOJUTSU;
        }
        if (containsAny(descriptor, new String[] {"taijutsu", "fist", "eight_gate", "enhanced_strength"})) {
            return SCROLL_TAIJUTSU;
        }
        if (containsAny(descriptor, new String[] {"summon", "puppet", "karasu", "sanshouo", "hiruko"})) {
            return SCROLL_SUMMONING;
        }
        if (containsAny(descriptor, new String[] {"fire", "katon", "flame", "blaze", "enton", "phoenix"})) {
            return SCROLL_FIRE;
        }
        if (containsAny(descriptor, new String[] {"water", "suiton", "rain", "mist"})) {
            return SCROLL_WATER;
        }
        if (containsAny(descriptor, new String[] {"wind", "futon", "vacuum"})) {
            return SCROLL_WIND;
        }
        if (containsAny(descriptor, new String[] {"lightning", "raiton", "chidori", "kirin", "electric", "elec"})) {
            return SCROLL_LIGHTNING;
        }
        if (containsAny(descriptor, new String[] {"earth", "doton", "rock", "sand", "mud", "stone",
                "golem", "swamp"})) {
            return SCROLL_EARTH;
        }
        return SCROLL_OTHER;
    }

    private static int defaultArmorPrice(String descriptor) {
        if (descriptor.contains("headband") || descriptor.contains("forehead")
                || descriptor.contains("protector") || descriptor.contains("mask")) {
            return 5000;
        }
        return descriptor.contains("robe") || descriptor.contains("cloak") ? 15000 : 10000;
    }

    private static Entry entry(ItemStack stack, String category, String subcategory, int price,
                               Village village, boolean akatsuki, String quotaKind) {
        ResourceLocation name = stack.getItem().getRegistryName();
        if (name == null) {
            return null;
        }
        int metadata = Math.max(0, stack.getMetadata());
        String key = name.toString().toLowerCase(Locale.ROOT) + "@" + metadata;
        return new Entry(key, name, metadata, safeDisplayName(stack), category,
                subcategory, price, village, akatsuki, quotaKind, 0);
    }

    private static List<Entry> expandTiers(Entry entry) {
        if (entry == null) return Collections.emptyList();
        if (!ARMOR.equals(entry.category())) return Collections.singletonList(entry);
        List<Entry> tiers = new ArrayList<Entry>(5);
        for (int tier = 0; tier <= 4; tier++) {
            String suffix = tier == 0 ? "base" : "tier" + tier;
            tiers.add(new Entry(entry.key() + "#" + suffix, entry.registryName(), entry.metadata(),
                    entry.displayName(), entry.category(), entry.subcategory(),
                    multiplyPrice(entry.defaultPrice(), tier + 1), entry.village(), entry.akatsuki(),
                    entry.quotaKind(), tier));
        }
        return tiers;
    }

    private static int multiplyPrice(int value, int multiplier) {
        long result = (long) value * multiplier;
        return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }

    private static List<ItemStack> variants(Item item) {
        NonNullList<ItemStack> found = NonNullList.create();
        try {
            item.getSubItems(CreativeTabs.SEARCH, found);
        } catch (Throwable ignored) {
        }
        if (found.isEmpty()) {
            found.add(new ItemStack(item));
        }
        List<ItemStack> result = new ArrayList<ItemStack>();
        Set<String> seen = new HashSet<String>();
        for (ItemStack stack : found) {
            ResourceLocation name = stack.isEmpty() ? null : stack.getItem().getRegistryName();
            if (name != null && seen.add(name.toString() + "@" + stack.getMetadata())) {
                result.add(stack.copy());
            }
        }
        return result;
    }

    private static Village village(String descriptor) {
        if (hasVillageSignature(descriptor, "leaf", "konoha", "konohagakure")) return Village.LEAF;
        if (hasVillageSignature(descriptor, "stone", "iwa", "iwagakure")) return Village.STONE;
        if (hasVillageSignature(descriptor, "cloud", "kumo", "kumogakure")) return Village.CLOUD;
        if (hasVillageSignature(descriptor, "sand", "suna", "sunagakure")) return Village.SAND;
        if (hasVillageSignature(descriptor, "mist", "kiri", "kirigakure")) return Village.MIST;
        if (hasVillageSignature(descriptor, "rain", "ame", "amegakure")) return Village.RAIN;
        return null;
    }

    private static Village kageVillage(String descriptor) {
        if (descriptor.contains("hokage")) return Village.LEAF;
        if (descriptor.contains("tsuchikage")) return Village.STONE;
        if (descriptor.contains("raikage")) return Village.CLOUD;
        if (descriptor.contains("kazekage")) return Village.SAND;
        if (descriptor.contains("mizukage")) return Village.MIST;
        if (descriptor.contains("amekage")) return Village.RAIN;
        return null;
    }

    private static boolean headPiece(String descriptor) {
        return containsAny(descriptor, new String[] {
                "helmet", "headband", "forehead", "protector", "mask", "_hat", "hat_", "head"
        });
    }

    private static boolean bodyPiece(String descriptor) {
        return containsAny(descriptor, new String[] {
                "body", "chest", "robe", "cloak", "shirt", "coat", "jacket"
        });
    }

    private static boolean hasVillageSignature(String descriptor, String english,
                                               String shinobi, String fullName) {
        if (hasToken(descriptor, shinobi) || descriptor.contains(fullName)) {
            return true;
        }
        return descriptor.contains("rogue_" + english)
                || descriptor.contains(english + "_headband")
                || descriptor.contains(english + "headband")
                || descriptor.contains(english + "_forehead")
                || descriptor.contains(english + "forehead")
                || descriptor.contains(english + "_protector")
                || descriptor.contains(english + "_ninja")
                || descriptor.contains(english + "_shinobi")
                || descriptor.contains(english + "_village")
                || descriptor.contains("ninja_armor_" + english)
                || descriptor.contains("ninja_armour_" + english);
    }

    private static boolean hasToken(String value, String token) {
        return ("_" + value + "_").contains("_" + token + "_");
    }

    private static String descriptor(ItemStack stack) {
        ResourceLocation name = stack.getItem().getRegistryName();
        String registry = name == null ? "" : name.toString();
        String translation;
        try {
            translation = stack.getUnlocalizedName();
        } catch (Throwable ignored) {
            translation = "";
        }
        String display;
        try {
            display = stack.getDisplayName();
        } catch (Throwable ignored) {
            display = "";
        }
        return (registry + " " + translation + " " + display).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_");
    }

    private static String safeDisplayName(ItemStack stack) {
        try {
            return stack.getDisplayName();
        } catch (Throwable ignored) {
            ResourceLocation name = stack.getItem().getRegistryName();
            return name == null ? "Item" : name.getResourcePath();
        }
    }

    private static boolean containsAny(String value, String[] needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsGroup(Set<String> groups, String group) {
        if (groups == null) {
            return false;
        }
        for (String candidate : groups) {
            if (group.equals(candidate == null ? "" : candidate.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public static final class Entry {
        private final String key;
        private final ResourceLocation registryName;
        private final int metadata;
        private final String displayName;
        private final String category;
        private final String subcategory;
        private final int defaultPrice;
        private final Village village;
        private final boolean akatsuki;
        private final String quotaKind;
        private final int tier;

        private Entry(String key, ResourceLocation registryName, int metadata, String displayName,
                      String category, String subcategory, int defaultPrice,
                      Village village, boolean akatsuki, String quotaKind, int tier) {
            this.key = key;
            this.registryName = registryName;
            this.metadata = metadata;
            this.displayName = displayName;
            this.category = category;
            this.subcategory = subcategory;
            this.defaultPrice = defaultPrice;
            this.village = village;
            this.akatsuki = akatsuki;
            this.quotaKind = quotaKind;
            this.tier = tier;
        }

        public String key() { return key; }
        public ResourceLocation registryName() { return registryName; }
        public int metadata() { return metadata; }
        public String displayName() { return displayName; }
        public String category() { return category; }
        public String subcategory() { return subcategory; }
        public int defaultPrice() { return defaultPrice; }
        public Village village() { return village; }
        public boolean akatsuki() { return akatsuki; }
        public String quotaKind() { return quotaKind; }
        public int tier() { return tier; }

        public ItemStack stack() {
            Item item = ForgeRegistries.ITEMS.getValue(registryName);
            if (item == null) return ItemStack.EMPTY;
            ItemStack stack = new ItemStack(item, 1, metadata);
            if (tier > 0) {
                stack.addEnchantment(Enchantments.PROTECTION, tier);
                stack.addEnchantment(Enchantments.UNBREAKING, tier);
            }
            NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
            tag.setString("RebornStoreOffer", key);
            tag.setString("RebornStoreQuota", quotaKind);
            tag.setInteger("RebornStoreTier", tier);
            stack.setTagCompound(tag);
            return stack;
        }

        public boolean matches(ItemStack stack) {
            if (stack == null || stack.isEmpty() || stack.getItem().getRegistryName() == null
                    || !registryName.equals(stack.getItem().getRegistryName())
                    || metadata != stack.getMetadata() || !stack.hasTagCompound()) {
                return false;
            }
            NBTTagCompound tag = stack.getTagCompound();
            return key.equals(tag.getString("RebornStoreOffer"))
                    && quotaKind.equals(tag.getString("RebornStoreQuota"))
                    && tier == tag.getInteger("RebornStoreTier");
        }

        public String descriptor() {
            return key + "\t" + clean(displayName) + "\t" + category + "\t"
                    + subcategory + "\t" + defaultPrice + "\t" + tier;
        }

        private static String clean(String value) {
            return value == null ? "" : value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
        }
    }

    public static final class Scope {
        private final String key;
        private final String type;
        private final String category;
        private final String subcategory;
        private final int tier;

        private Scope(String key, String type, String category, String subcategory, int tier) {
            this.key = key;
            this.type = type;
            this.category = category;
            this.subcategory = subcategory;
            this.tier = tier;
        }

        public String key() { return key; }
        public String type() { return type; }
        public String category() { return category; }
        public String subcategory() { return subcategory; }
        public int tier() { return tier; }
    }
}
