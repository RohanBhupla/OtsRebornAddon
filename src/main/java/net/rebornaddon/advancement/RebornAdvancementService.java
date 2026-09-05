package net.rebornaddon.advancement;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.FrameType;
import net.minecraft.advancements.PlayerAdvancements;
import net.minecraft.advancements.critereon.ImpossibleTrigger;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.rebornaddon.compat.NarutoAddonAdvancementCompatibility;
import net.rebornaddon.compat.ShinobiAddonRestrictionHandler;
import net.narutomod.Chakra;
import net.narutomod.PlayerTracker;
import net.rebornaddon.jutsu.JutsuDefinition;
import net.rebornaddon.jutsu.JutsuRegistry;
import net.rebornaddon.jutsu.JutsuSettingsCache;
import net.rebornaddon.mode.ModeDefinition;
import net.rebornaddon.mode.ModeRegistry;
import net.rebornaddon.mode.ModeSettingsCache;
import net.rebornaddon.policy.ContentPolicyService;
import net.rebornaddon.policy.PolicyAction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the server-side advancement tree and the compatibility aliases used by
 * the installed NarutoMod procedures.  The roots are bundled JSON resources;
 * catalog-driven children are added after Forge has registered all mod items.
 */
public final class RebornAdvancementService {
    public static final RebornAdvancementService INSTANCE = new RebornAdvancementService();

    public static final ResourceLocation NINJA = id("ninja");
    public static final ResourceLocation KEKKEI_GENKAI = id("kekkei_genkai");
    public static final ResourceLocation CLAN = id("clan");
    public static final ResourceLocation NATURES = id("natures");
    public static final ResourceLocation MODES = id("modes");
    public static final ResourceLocation STORE = id("store");

    private static final Logger LOGGER = LogManager.getLogger("RebornAddon Advancements");
    private static final double INITIAL_NINJA_XP = 0.1D;
    private static final ResourceLocation LEGACY_NINJA =
            new ResourceLocation("narutomod", "ninjaachievement");
    private static final String[] NINJA_RECIPE_REWARDS = new String[] {
            "narutomod:ninja_boots_recipe",
            "narutomod:ninja_pants_recipe_konoha",
            "narutomod:ninja_vest_recipe_konoha",
            "narutomod:ninja_helmet_recipe_konoha",
            "narutomod:explosive_tag_recipe",
            "narutomod:kunai_explosive_recipe"
    };
    private static final List<String> NINJA_COMPATIBILITY_ALIASES =
            Collections.singletonList(LEGACY_NINJA.toString());
    private static final List<ResourceLocation> ROOT_IDS = Collections.unmodifiableList(Arrays.asList(
            NINJA, NATURES, KEKKEI_GENKAI, CLAN, MODES, STORE));
    private static final String[] LEGENDARY_MIST_SWORDS = new String[] {
            "narutomod:hiramekarei_sword",
            "narutomod:kabutowari",
            "narutomod:kiba_blades",
            "narutomod:nuibari_sword",
            "narutomod:samehada",
            "narutomod:shibuki_sword",
            "narutomod:zabuza_sword"
    };
    private static final String[] CURATED_KEKKEI_ITEMS = new String[] {
            "narutomod:sharinganhelmet",
            "narutomod:sharingan_1tomoehelmet",
            "narutomod:sharingan_2tomoehelmet",
            "narutomod:mangekyosharinganhelmet",
            "narutomod:mangekyosharinganobitohelmet",
            "narutomod:mangekyosharinganeternalhelmet",
            "narutomod:byakuganhelmet",
            "narutomod:rinneganhelmet",
            "narutomod:rinne_sharinganhelmet",
            "narutomod:shikotsumyaku",
            "narutomod:hyoton",
            "narutomod:futton",
            "narutomod:bakuton",
            "narutomod:ranton",
            "narutomod:yooton",
            "narutomod:shakuton",
            "narutomod:shoton",
            "narutomod:mokuton",
            "narutomod:jinton",
            "narutomod:jiton",
            "shinobiaddon:dynamic_sharingan",
            "shinobiaddon:dynamic_byakugan",
            "shinobiaddon:dynamic_ketsuryugan"
    };

    private final Map<String, Advancement> legacyAliases = new HashMap<String, Advancement>();
    private final Map<Item, List<Advancement>> itemUnlocks =
            new IdentityHashMap<Item, List<Advancement>>();
    private final Map<Item, List<JutsuUnlock>> jutsuUnlocks =
            new IdentityHashMap<Item, List<JutsuUnlock>>();
    private final Map<Advancement, ModeDefinition> modeUnlocks =
            new IdentityHashMap<Advancement, ModeDefinition>();
    private final Map<String, Advancement> jutsuNodes = new HashMap<String, Advancement>();
    private final Map<String, Advancement> kekkeiNodes = new LinkedHashMap<String, Advancement>();
    private final Map<String, Advancement> natureNodes = new LinkedHashMap<String, Advancement>();
    private final Map<String, Advancement> modeNodes = new LinkedHashMap<String, Advancement>();
    private final Map<UUID, Long> inventoryFingerprints = new HashMap<UUID, Long>();
    private final Set<UUID> ninjaBenefitsApplied = new HashSet<UUID>();
    private final List<Advancement> created = new ArrayList<Advancement>();
    private List<Advancement> visibleCatalog = Collections.emptyList();
    private MinecraftServer server;
    private boolean initialized;

    private RebornAdvancementService() {
    }

    public synchronized void initialize(MinecraftServer nextServer) {
        reset();
        if (nextServer == null) {
            return;
        }

        server = nextServer;
        Map<ResourceLocation, Advancement> roots = new LinkedHashMap<ResourceLocation, Advancement>();
        roots.put(NINJA, root(NINJA, "Ninja Advancement", "Your gateway to the shinobi systems.",
                "narutomod:kunai", "minecraft:textures/blocks/stone.png"));
        roots.put(NATURES, root(NATURES, "Natures", "Master the elemental paths and their jutsu.",
                "narutomod:katon", "minecraft:textures/blocks/grass_top.png"));
        roots.put(KEKKEI_GENKAI, root(KEKKEI_GENKAI, "Kekkei Genkai", "Discover the bloodlines allowed on this server.",
                "narutomod:sharinganhelmet", "minecraft:textures/blocks/diamond_block.png"));
        roots.put(CLAN, root(CLAN, "Clan", "Clan progression will be added here.",
                "minecraft:book", "minecraft:textures/blocks/bookshelf.png"));
        roots.put(MODES, root(MODES, "Modes", "Learn the modes enabled by the server.",
                "narutomod:senjutsu", "minecraft:textures/blocks/glowstone.png"));
        roots.put(STORE, root(STORE, "Store", "The seven legendary swords of the Mist.",
                "narutomod:hiramekarei_sword", "minecraft:textures/blocks/iron_block.png"));

        addNatureBranches(roots.get(NATURES));
        addKekkeiNodes(roots.get(KEKKEI_GENKAI));
        addModeNodes(roots.get(MODES));
        addJutsuNodes(roots.get(NATURES));
        addStoreNodes(roots.get(STORE));
        registerLegacyAliases(roots.get(NINJA));
        rebuildTreeLayout(roots.values());
        visibleCatalog = Collections.unmodifiableList(orderedAdvancements());

        initialized = true;
        LOGGER.info("Loaded {} RebornAddon advancement roots and {} catalog nodes.",
                ROOT_IDS.size(), created.size());
    }

    public synchronized void reset() {
        initialized = false;
        server = null;
        legacyAliases.clear();
        itemUnlocks.clear();
        jutsuUnlocks.clear();
        modeUnlocks.clear();
        jutsuNodes.clear();
        kekkeiNodes.clear();
        natureNodes.clear();
        modeNodes.clear();
        inventoryFingerprints.clear();
        ninjaBenefitsApplied.clear();
        created.clear();
        visibleCatalog = Collections.emptyList();
    }

    public synchronized Advancement legacyAdvancement(String name) {
        if (name == null || server == null) {
            return null;
        }
        String clean = name.trim().toLowerCase(Locale.ROOT);
        String full = clean.indexOf(':') >= 0 ? clean : "narutomod:" + clean;
        Advancement replacement = legacyAliases.get(full);
        if (replacement != null) {
            return replacement;
        }
        try {
            return server.getAdvancementManager().getAdvancement(new ResourceLocation(full));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static Advancement replacementFor(Advancement advancement) {
        return INSTANCE.replacement(advancement);
    }

    public static boolean isRebornAdvancement(Advancement advancement) {
        if (advancement == null || advancement.getId() == null) {
            return false;
        }
        return "rebornaddon".equals(advancement.getId().getResourceDomain());
    }

    /** Repairs both advancement representations and returns the authoritative Ninja state. */
    public synchronized boolean ensureNinja(EntityPlayerMP player) {
        if (player == null) {
            return false;
        }

        Advancement reborn = find(NINJA);
        Advancement legacy = server == null ? null
                : server.getAdvancementManager().getAdvancement(LEGACY_NINJA);
        boolean ninja = isComplete(player, reborn)
                || isComplete(player, legacy)
                || PlayerTracker.isNinja(player);
        if (!ninja) {
            return false;
        }

        if (reborn != null && !isComplete(player, reborn)) {
            grant(player, reborn, "ninjaachievement");
        }
        synchronizeNinjaProgress(player);
        applyNinjaBenefits(player);
        return true;
    }

    private synchronized Advancement replacement(Advancement advancement) {
        if (advancement == null || isRebornAdvancement(advancement)) {
            return advancement;
        }
        ResourceLocation id = advancement.getId();
        if (id == null) {
            return advancement;
        }
        if (LEGACY_NINJA.equals(id)) {
            Advancement ninja = find(NINJA);
            if (ninja != null) {
                return ninja;
            }
        }
        Advancement replacement = legacyAliases.get(id.toString().toLowerCase(Locale.ROOT));
        return replacement == null ? advancement : replacement;
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            migrateLegacyProgress(player);
            grantAutomaticRoots(player);
            syncInventory(player, true);
            synchronizeNinjaProgress(player);
            applyNinjaBenefits(player);
            syncModes(player);
            exposeAdvancements(player, true);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!initialized || event.phase != TickEvent.Phase.END
                || !(event.player instanceof EntityPlayerMP)
                || event.player.world.isRemote || event.player.ticksExisted % 20 != 0) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        migrateLegacyProgress(player);
        syncInventory(player, false);
        synchronizeNinjaProgress(player);
        applyNinjaBenefits(player);
        syncModes(player);
        exposeAdvancements(player, false);
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player != null) {
            inventoryFingerprints.remove(event.player.getUniqueID());
            ninjaBenefitsApplied.remove(event.player.getUniqueID());
        }
    }

    @SubscribeEvent
    public void onAdvancement(AdvancementEvent event) {
        if (!initialized || !(event.getEntityPlayer() instanceof EntityPlayerMP)
                || event.getAdvancement() == null || event.getAdvancement().getId() == null) {
            return;
        }
        ResourceLocation advancementId = event.getAdvancement().getId();
        if (!NINJA.equals(advancementId) && !LEGACY_NINJA.equals(advancementId)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();
        synchronizeNinjaProgress(player);
        applyNinjaBenefits(player);
    }

    @SubscribeEvent
    public void onItemPickup(EntityItemPickupEvent event) {
        markInventoryDirty(event.getEntityPlayer());
    }

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        markInventoryDirty(event.player);
    }

    @SubscribeEvent
    public void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        markInventoryDirty(event.player);
    }

    @SubscribeEvent
    public void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayer) {
            markInventoryDirty((EntityPlayer) event.getEntityLiving());
        }
    }

    /** Called after the exact configured jutsu stage successfully creates its effect. */
    public synchronized void onJutsuUsed(EntityPlayerMP player, JutsuDefinition definition) {
        Advancement advancement = definition == null ? null : jutsuNodes.get(definition.id());
        if (player != null && advancement != null && isAllowed(definition)
                && !player.isCreative() && !player.isSpectator()
                && !isComplete(player, advancement)) {
            grantWithAncestors(player, advancement);
            exposeAdvancements(player, false);
        }
    }

    private void syncInventory(EntityPlayerMP player, boolean force) {
        if (player == null) return;
        long fingerprint = inventoryFingerprint(player);
        Long previous = inventoryFingerprints.get(player.getUniqueID());
        if (!force && previous != null && previous.longValue() == fingerprint) return;
        inventoryFingerprints.put(player.getUniqueID(), Long.valueOf(fingerprint));

        Item kunai = item("narutomod:kunai");
        Item shuriken = item("narutomod:shuriken");
        InventoryState state = new InventoryState(kunai, shuriken);
        boolean mayUnlockFromItems = !player.isCreative() && !player.isSpectator();
        scanInventory(player, player.inventory.mainInventory, state, mayUnlockFromItems);
        scanInventory(player, player.inventory.armorInventory, state, mayUnlockFromItems);
        scanInventory(player, player.inventory.offHandInventory, state, mayUnlockFromItems);
        Advancement ninja = find(NINJA);
        if (state.hasKunai && state.hasShuriken && ninja != null && !isComplete(player, ninja)) {
            grant(player, ninja, "ninjaachievement");
        }
    }

    private void syncModes(EntityPlayerMP player) {
        for (Map.Entry<Advancement, ModeDefinition> entry : modeUnlocks.entrySet()) {
            ModeDefinition mode = entry.getValue();
            if (isAllowed(mode) && !isComplete(player, entry.getKey())
                    && ModeRegistry.INSTANCE.active(player, mode)) {
                grantWithAncestors(player, entry.getKey());
            }
        }
    }

    private Advancement root(ResourceLocation id, String title, String description,
                              String icon, String background) {
        Advancement existing = find(id);
        return existing != null ? existing : addAdvancement(null, id, title, description,
                icon, background, FrameType.GOAL, false, false);
    }

    private Advancement addAdvancement(Advancement parent, ResourceLocation id, String title,
                                       String description, String iconId, String background,
                                       FrameType frame, boolean showToast, boolean announce) {
        Advancement existing = find(id);
        if (existing != null) {
            return existing;
        }
        Item item = item(iconId);
        ItemStack icon = item == null ? new ItemStack(Items.BOOK) : new ItemStack(item);
        DisplayInfo display = new DisplayInfo(icon, new TextComponentString(title),
                new TextComponentString(description), new ResourceLocation(background), frame,
                showToast, announce, false);
        Map<String, Criterion> criteria = new LinkedHashMap<String, Criterion>();
        criteria.put(parent == null && NINJA.equals(id) ? "ninjaachievement" : "unlock",
                new Criterion(new ImpossibleTrigger.Instance()));
        String[][] requirements = new String[][] {{criteria.keySet().iterator().next()}};
        Advancement advancement = new Advancement(id, parent, display,
                AdvancementRewards.EMPTY,
                criteria, requirements);
        if (!register(advancement)) {
            return find(id);
        }
        created.add(advancement);
        return advancement;
    }

    private void addNatureBranches(Advancement parent) {
        if (parent == null) return;
        trackNature("fire", addAdvancement(parent, id("natures/fire"), "Fire Nature", "Katon techniques.",
                "narutomod:katon", "minecraft:textures/blocks/grass_top.png", FrameType.TASK, false, false));
        trackNature("water", addAdvancement(parent, id("natures/water"), "Water Nature", "Suiton techniques.",
                "narutomod:suiton", "minecraft:textures/blocks/grass_top.png", FrameType.TASK, false, false));
        trackNature("wind", addAdvancement(parent, id("natures/wind"), "Wind Nature", "Futon techniques.",
                "narutomod:futon", "minecraft:textures/blocks/grass_top.png", FrameType.TASK, false, false));
        trackNature("lightning", addAdvancement(parent, id("natures/lightning"), "Lightning Nature", "Raiton techniques.",
                "narutomod:raiton", "minecraft:textures/blocks/grass_top.png", FrameType.TASK, false, false));
        trackNature("earth", addAdvancement(parent, id("natures/earth"), "Earth Nature", "Doton techniques.",
                "narutomod:doton", "minecraft:textures/blocks/grass_top.png", FrameType.TASK, false, false));
    }

    private void addJutsuNodes(Advancement natureRoot) {
        if (natureRoot == null) return;
        Map<String, Advancement> branches = new LinkedHashMap<String, Advancement>();
        branches.put("fire", find(id("natures/fire")));
        branches.put("water", find(id("natures/water")));
        branches.put("wind", find(id("natures/wind")));
        branches.put("lightning", find(id("natures/lightning")));
        branches.put("earth", find(id("natures/earth")));
        for (JutsuDefinition definition : JutsuRegistry.INSTANCE.all()) {
            if (!isAllowed(definition)) continue;

            ModeDefinition mode = ModeRegistry.INSTANCE.forJutsu(definition);
            Advancement modeParent = mode == null ? null : modeNodes.get(mode.id());
            if (modeParent != null) {
                jutsuNodes.put(definition.id(), modeParent);
                trackJutsu(modeParent, definition);
                continue;
            }

            String kekkei = kekkeiForJutsu(definition.category(), definition.id(),
                    definition.displayName());
            Advancement kekkeiParent = kekkei.isEmpty() ? null : kekkeiNodes.get(kekkei);
            if (kekkeiParent != null) {
                Advancement advancement;
                if (sameName(definition.displayName(), kekkeiTitle(kekkei))) {
                    advancement = kekkeiParent;
                } else {
                    advancement = addAdvancement(kekkeiParent,
                            id("kekkei_genkai/" + kekkei + "/" + stable(definition.id())),
                            definition.displayName(), "Learn this allowed kekkei genkai technique.",
                            jutsuIcon(definition, ""),
                            "minecraft:textures/blocks/diamond_block.png",
                            FrameType.TASK, true, false);
                }
                if (advancement != null) {
                    jutsuNodes.put(definition.id(), advancement);
                    trackJutsu(advancement, definition);
                }
                continue;
            }

            String branch = natureBranch(definition.category(), "", "");
            Advancement branchParent = branches.get(branch);
            if (branchParent == null) continue;
            String path = "natures/" + branch + "/" + stable(definition.id());
            String icon = jutsuIcon(definition, branch);
            Advancement advancement = addAdvancement(branchParent, id(path), definition.displayName(),
                    "Learn this allowed jutsu.", icon, "minecraft:textures/blocks/grass_top.png",
                    FrameType.TASK, true, false);
            if (advancement != null) {
                jutsuNodes.put(definition.id(), advancement);
                trackJutsu(advancement, definition);
            }
        }
    }

    private void addKekkeiNodes(Advancement parent) {
        if (parent == null) return;
        Map<String, KekkeiGroup> groups = new LinkedHashMap<String, KekkeiGroup>();
        for (String itemId : CURATED_KEKKEI_ITEMS) {
            Item item = item(itemId);
            if (item != null) offerKekkei(groups, item);
        }
        List<Item> registered = new ArrayList<Item>(ForgeRegistries.ITEMS.getValuesCollection());
        Collections.sort(registered, new Comparator<Item>() {
            @Override
            public int compare(Item left, Item right) {
                String a = left.getRegistryName() == null ? "" : left.getRegistryName().toString();
                String b = right.getRegistryName() == null ? "" : right.getRegistryName().toString();
                return a.compareTo(b);
            }
        });
        for (Item item : registered) {
            ResourceLocation name = item.getRegistryName();
            if (name != null && isKekkeiPath(name.getResourcePath())) offerKekkei(groups, item);
        }
        for (KekkeiGroup group : groups.values()) {
            if (group.icon == null || group.icon.getRegistryName() == null) continue;
            Advancement advancement = addAdvancement(parent, id("kekkei_genkai/" + group.key),
                    group.title, "Awaken this allowed kekkei genkai.",
                    group.icon.getRegistryName().toString(),
                    "minecraft:textures/blocks/diamond_block.png", FrameType.CHALLENGE, true, false);
            kekkeiNodes.put(group.key, advancement);
            for (Item trigger : group.triggers) trackItem(advancement, trigger);
        }
    }

    private void addModeNodes(Advancement parent) {
        if (parent == null) return;
        for (ModeDefinition mode : ModeRegistry.INSTANCE.all()) {
            if (!isAllowed(mode)) continue;
            String icon = modeIcon(mode.id());
            Advancement advancement = addAdvancement(parent, id("modes/" + stable(mode.id())),
                    mode.displayName(), "Use this allowed mode.", icon,
                    "minecraft:textures/blocks/glowstone.png", FrameType.GOAL, true, false);
            trackMode(advancement, mode);
        }
    }

    private void addStoreNodes(Advancement parent) {
        if (parent == null) return;
        for (String itemId : LEGENDARY_MIST_SWORDS) {
            Item item = item(itemId);
            if (item == null || !allowed(item)) {
                LOGGER.warn("The configured Mist sword {} is not registered; its advancement was skipped.", itemId);
                continue;
            }
            Advancement advancement = addAdvancement(parent, id("store/" + stable(itemId)),
                    title(itemId), "Obtain one of the seven legendary swords of the Mist.", itemId,
                    "minecraft:textures/blocks/iron_block.png", FrameType.CHALLENGE, true, false);
            trackItem(advancement, item);
        }
    }

    private void registerLegacyAliases(Advancement ninja) {
        for (String legacyId : NINJA_COMPATIBILITY_ALIASES) {
            alias(legacyId, ninja);
        }
        alias("narutomod:sharinganopened", findKekkei("sharingan"));
        alias("narutomod:mangekyosharinganopened", findKekkei("sharingan"));
        alias("narutomod:eternalmangekyoachieved", findKekkei("sharingan"));
        alias("narutomod:byakuganopened", findKekkei("byakugan"));
        alias("narutomod:rinneganawakened", findKekkei("rinnegan"));
        alias("narutomod:rinnesharinganactivated", findKekkei("rinne_sharingan"));
        alias("narutomod:mini_rinne_sharingan_activated", findKekkei("rinne_sharingan"));
        alias("narutomod:tenseigan_achieved", findKekkei("tenseigan"));
        alias("narutomod:tensei_byakugan_activated", findKekkei("tenseigan"));
        alias("narutomod:shikotsumyaku_acquired", findKekkei("shikotsumyaku"));
        alias("narutomod:kekkei_tota_awakened", findKekkei("jinton"));
        alias("narutomod:hyoton_acquired", findKekkei("hyoton"));
        alias("narutomod:futton_acquired", findKekkei("futton"));
        alias("narutomod:bakuton_acquired", findKekkei("bakuton"));
        alias("narutomod:ranton_acquired", findKekkei("ranton"));
        alias("narutomod:yooton_acquired", findKekkei("yooton"));
        alias("narutomod:shakuton_acquired", findKekkei("shakuton"));
        alias("narutomod:openedgates", find(id("modes/narutomod_eight_gates")));
        alias("narutomod:deiton_acquired", findKekkei("deiton"));
        alias("narutomod:jindon_acquired", findKekkei("jindon"));
        alias("narutomod:jiton_acquired", findKekkei("jiton"));
        alias("narutomod:koton_acquired", findKekkei("koton"));
        alias("narutomod:mokuton_acquired", findKekkei("mokuton"));
    }

    static boolean isLegacyNinjaAlias(String advancementId) {
        return NINJA_COMPATIBILITY_ALIASES.contains(
                advancementId == null ? "" : advancementId.trim().toLowerCase(Locale.ROOT));
    }

    static List<String> ninjaRecipeRewardIds() {
        return Collections.unmodifiableList(Arrays.asList(NINJA_RECIPE_REWARDS));
    }

    private Advancement findKekkei(String token) {
        for (Map.Entry<String, Advancement> entry : kekkeiNodes.entrySet()) {
            if (entry.getKey().contains(stable(token))) return entry.getValue();
        }
        return null;
    }

    private void alias(String legacyId, Advancement replacement) {
        if (legacyId != null && replacement != null) {
            legacyAliases.put(legacyId.toLowerCase(Locale.ROOT), replacement);
        }
    }

    private void trackItem(Advancement advancement, String itemId) {
        Item item = item(itemId);
        trackItem(advancement, item);
    }

    private void trackItem(Advancement advancement, Item item) {
        if (advancement == null || item == null || item.getRegistryName() == null) return;
        List<Advancement> advancements = itemUnlocks.get(item);
        if (advancements == null) {
            advancements = new ArrayList<Advancement>();
            itemUnlocks.put(item, advancements);
        }
        if (!advancements.contains(advancement)) advancements.add(advancement);
    }

    private void trackJutsu(Advancement advancement, JutsuDefinition definition) {
        Item item = definition == null ? null : definition.item();
        if (advancement == null || item == null) return;
        List<JutsuUnlock> definitions = jutsuUnlocks.get(item);
        if (definitions == null) {
            definitions = new ArrayList<JutsuUnlock>();
            jutsuUnlocks.put(item, definitions);
        }
        definitions.add(new JutsuUnlock(advancement, definition));
    }

    private void trackMode(Advancement advancement, ModeDefinition mode) {
        if (advancement != null && mode != null) {
            modeUnlocks.put(advancement, mode);
            modeNodes.put(mode.id(), advancement);
        }
    }

    private void trackNature(String branch, Advancement advancement) {
        if (branch != null && advancement != null) natureNodes.put(branch, advancement);
    }

    private Advancement find(ResourceLocation id) {
        return server == null || id == null ? null : server.getAdvancementManager().getAdvancement(id);
    }

    private boolean register(Advancement advancement) {
        if (server == null || advancement == null) return false;
        try {
            Object list = null;
            for (Field field : AdvancementManagerFields.fields()) {
                if (!Modifier.isStatic(field.getModifiers())
                        || !"net.minecraft.advancements.AdvancementList".equals(field.getType().getName())) {
                    continue;
                }
                field.setAccessible(true);
                list = field.get(null);
                if (list != null) break;
            }
            if (list == null) {
                Method method = server.getAdvancementManager().getClass().getMethod("getAdvancementList");
                list = method.invoke(server.getAdvancementManager());
            }
            if (list == null) return false;
            Map map = null;
            for (Field field : list.getClass().getDeclaredFields()) {
                if (Map.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    map = (Map) field.get(list);
                    break;
                }
            }
            if (map == null) return false;
            map.put(advancement.getId(), advancement);
            Object roots = invoke(list, "getRoots", "func_192088_b");
            if (advancement.getParent() == null) {
                if (roots instanceof Set) ((Set) roots).add(advancement);
            } else {
                for (Field field : list.getClass().getDeclaredFields()) {
                    if (!Set.class.isAssignableFrom(field.getType())) continue;
                    field.setAccessible(true);
                    Object candidates = field.get(list);
                    if (candidates instanceof Set && candidates != roots) {
                        ((Set) candidates).add(advancement);
                    }
                }
            }
            return true;
        } catch (Throwable throwable) {
            LOGGER.error("Could not register advancement {}.", advancement.getId(), throwable);
            return false;
        }
    }

    private void rebuildTreeLayout(Iterable<Advancement> roots) {
        for (Advancement root : roots) {
            if (root == null) continue;
            try {
                Class<?> type = Class.forName("net.minecraft.advancements.AdvancementTreeNode");
                Method method;
                try {
                    method = type.getMethod("layout", Advancement.class);
                } catch (NoSuchMethodException ignored) {
                    method = type.getMethod("func_192323_a", Advancement.class);
                }
                method.invoke(null, root);
            } catch (Throwable ignored) {
                // The explicit JSON roots still render; only automatic layout is unavailable.
            }
        }
    }

    private static Object getField(Object target, String... names) {
        Class<?> type = target == null ? null : target.getClass();
        while (type != null) {
            for (String name : names) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static final class AdvancementManagerFields {
        private static Field[] fields() {
            return net.minecraft.advancements.AdvancementManager.class.getDeclaredFields();
        }
    }

    private static Object invoke(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Item item(String id) {
        if (id == null || id.trim().isEmpty()) return null;
        try {
            return ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean allowed(Item item) {
        try {
            if (item == null || item.getRegistryName() == null
                    || ShinobiAddonRestrictionHandler.shouldHideItem(item)) {
                return false;
            }
            String id = item.getRegistryName().toString();
            return !ContentPolicyService.INSTANCE.denies(PolicyAction.JEI, id)
                    && !ContentPolicyService.INSTANCE.denies(PolicyAction.CREATIVE, id)
                    && !ContentPolicyService.INSTANCE.denies(PolicyAction.USE, id);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isAllowed(JutsuDefinition definition) {
        if (definition == null || definition.restricted()
                || !JutsuSettingsCache.INSTANCE.enabled(definition)) {
            return false;
        }
        if (definition.item() != null && !allowed(definition.item())) return false;
        return !ContentPolicyService.INSTANCE.denies(PolicyAction.JEI, definition.id())
                && !ContentPolicyService.INSTANCE.denies(PolicyAction.USE, definition.id());
    }

    private static boolean isAllowed(ModeDefinition mode) {
        if (mode == null || ModeRegistry.GLOBAL_ID.equals(mode.id())
                || !ModeSettingsCache.INSTANCE.enabled(mode)) {
            return false;
        }
        return !ContentPolicyService.INSTANCE.denies(PolicyAction.JEI, mode.id())
                && !ContentPolicyService.INSTANCE.denies(PolicyAction.USE, mode.id());
    }

    private void scanInventory(EntityPlayerMP player, Iterable<ItemStack> stacks,
                               InventoryState state, boolean mayUnlock) {
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            Item held = stack.getItem();
            if (held == state.kunai) state.hasKunai = true;
            if (held == state.shuriken) state.hasShuriken = true;
            if (!mayUnlock) continue;
            grantItemUnlocks(player, held);
            grantJutsuUnlocks(player, held, stack);
            grantLearnerUnlocks(player, stack);
        }
    }

    private void grantItemUnlocks(EntityPlayerMP player, Item held) {
        List<Advancement> advancements = itemUnlocks.get(held);
        if (advancements == null) return;
        for (Advancement advancement : advancements) {
            if (!isComplete(player, advancement)) grantWithAncestors(player, advancement);
        }
    }

    private void grantJutsuUnlocks(EntityPlayerMP player, Item held, ItemStack stack) {
        List<JutsuUnlock> definitions = jutsuUnlocks.get(held);
        if (definitions == null) return;
        for (JutsuUnlock unlock : definitions) {
            if (isAllowed(unlock.definition) && !isComplete(player, unlock.advancement)
                    && JutsuRegistry.INSTANCE.matchesInventoryStack(unlock.definition, stack)) {
                grantWithAncestors(player, unlock.advancement);
                grantModeForJutsu(player, unlock.definition);
            }
        }
    }

    private void grantLearnerUnlocks(EntityPlayerMP player, ItemStack stack) {
        String descriptor = learnerDescriptor(stack);

        String kekkei = canonicalKekkei(descriptor);
        Advancement kekkeiAdvancement = kekkei.isEmpty() ? null : kekkeiNodes.get(kekkei);
        if (kekkeiAdvancement != null && !isComplete(player, kekkeiAdvancement)) {
            grantWithAncestors(player, kekkeiAdvancement);
        }

        String nature = learnerNature(descriptor);
        Advancement natureAdvancement = natureNodes.get(nature);
        if (natureAdvancement != null && !isComplete(player, natureAdvancement)) {
            grantWithAncestors(player, natureAdvancement);
        }

        ModeDefinition learnerMode = ModeRegistry.INSTANCE.learnerMode(player, stack);
        grantMode(player, learnerMode);
    }

    private void grantModeForJutsu(EntityPlayerMP player, JutsuDefinition definition) {
        grantMode(player, ModeRegistry.INSTANCE.forJutsu(player, definition));
    }

    private void grantMode(EntityPlayerMP player, ModeDefinition mode) {
        if (mode == null || !isAllowed(mode)) return;
        Advancement advancement = modeNodes.get(mode.id());
        if (advancement != null && !isComplete(player, advancement)) {
            grantWithAncestors(player, advancement);
        }
    }

    static String learnerDescriptor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        StringBuilder value = new StringBuilder();
        try {
            ResourceLocation id = stack.getItem().getRegistryName();
            if (id != null) value.append(id.toString()).append(' ');
            value.append(stack.getItem().getClass().getName()).append(' ')
                    .append(stack.getUnlocalizedName()).append(' ');
            if (stack.hasDisplayName()) value.append(stack.getDisplayName()).append(' ');
            if (stack.hasTagCompound()) value.append(stack.getTagCompound().toString());
        } catch (Throwable ignored) {
        }
        return value.toString().toLowerCase(Locale.ROOT);
    }

    private void markInventoryDirty(EntityPlayer player) {
        if (player != null) inventoryFingerprints.remove(player.getUniqueID());
    }

    private static long inventoryFingerprint(EntityPlayer player) {
        long hash = 0xcbf29ce484222325L;
        hash = fingerprint(hash, player.inventory.mainInventory);
        hash = fingerprint(hash, player.inventory.armorInventory);
        return fingerprint(hash, player.inventory.offHandInventory);
    }

    private static long fingerprint(long hash, Iterable<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            hash ^= stack == null || stack.isEmpty() ? 0L : System.identityHashCode(stack.getItem());
            hash *= 0x100000001b3L;
            if (stack != null && !stack.isEmpty()) {
                hash ^= stack.getCount();
                hash *= 0x100000001b3L;
                hash ^= stack.getMetadata();
                hash *= 0x100000001b3L;
                hash ^= stack.hasTagCompound() ? stack.getTagCompound().hashCode() : 0;
                hash *= 0x100000001b3L;
            }
        }
        return hash;
    }

    private void migrateLegacyProgress(EntityPlayerMP player) {
        if (player == null || server == null) return;
        for (Map.Entry<String, Advancement> entry : legacyAliases.entrySet()) {
            try {
                Advancement legacy = server.getAdvancementManager().getAdvancement(
                        new ResourceLocation(entry.getKey()));
                NarutoAddonAdvancementCompatibility.migrateCompletion(
                        player.getAdvancements(), legacy, entry.getValue());
                if (isComplete(player, entry.getValue())) {
                    grantAncestors(player, entry.getValue().getParent());
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void synchronizeNinjaProgress(EntityPlayerMP player) {
        if (player == null || server == null) return;
        Advancement reborn = find(NINJA);
        Advancement legacy = server.getAdvancementManager().getAdvancement(LEGACY_NINJA);
        if (reborn == null || legacy == null) return;
        if (isComplete(player, legacy) && !isComplete(player, reborn)) {
            NarutoAddonAdvancementCompatibility.migrateCompletion(
                    player.getAdvancements(), legacy, reborn);
        } else if (isComplete(player, reborn) && !isComplete(player, legacy)) {
            grant(player, legacy, "ninjaachievement");
        }
    }

    private void applyNinjaBenefits(EntityPlayerMP player) {
        Advancement ninja = find(NINJA);
        if (player == null || ninja == null || !isComplete(player, ninja)) {
            return;
        }
        if (!PlayerTracker.isNinja(player)) {
            PlayerTracker.addBattleXp(player, INITIAL_NINJA_XP);
        }
        Chakra.pathway(player);
        if (ninjaBenefitsApplied.add(player.getUniqueID())) {
            ResourceLocation[] recipes = new ResourceLocation[NINJA_RECIPE_REWARDS.length];
            for (int index = 0; index < NINJA_RECIPE_REWARDS.length; index++) {
                recipes[index] = new ResourceLocation(NINJA_RECIPE_REWARDS[index]);
            }
            NarutoAddonAdvancementCompatibility.safeUnlockRecipes(player, recipes);
        }
    }

    private void grantAutomaticRoots(EntityPlayerMP player) {
        Advancement clan = find(CLAN);
        if (clan != null && !isComplete(player, clan)) grant(player, clan, "unlock");
    }

    @SuppressWarnings("unchecked")
    private void exposeAdvancements(EntityPlayerMP player, boolean reorder) {
        if (player == null) return;
        PlayerAdvancements progress = player.getAdvancements();
        Object visibleValue = getField(progress, "visible", "field_192759_g", "g");
        Object changedValue = getField(progress, "visibilityChanged", "field_192760_h", "h");
        if (!(visibleValue instanceof Set) || !(changedValue instanceof Set)) return;
        Set<Advancement> visible = (Set<Advancement>) visibleValue;
        Set<Advancement> changed = (Set<Advancement>) changedValue;
        List<Advancement> ordered = visibleCatalog;
        if (reorder) {
            Set<Advancement> previouslyVisible = Collections.newSetFromMap(
                    new IdentityHashMap<Advancement, Boolean>());
            for (Advancement advancement : ordered) {
                if (visible.remove(advancement)) previouslyVisible.add(advancement);
                changed.remove(advancement);
            }
            for (Advancement advancement : ordered) {
                visible.add(advancement);
                if (!previouslyVisible.contains(advancement)) changed.add(advancement);
            }
            return;
        }
        for (Advancement advancement : ordered) {
            if (visible.add(advancement)) changed.add(advancement);
        }
    }

    private List<Advancement> orderedAdvancements() {
        List<Advancement> result = new ArrayList<Advancement>();
        for (ResourceLocation rootId : ROOT_IDS) {
            Advancement root = find(rootId);
            if (root == null) continue;
            result.add(root);
            List<Advancement> descendants = new ArrayList<Advancement>();
            for (Advancement advancement : created) {
                if (root.equals(rootOf(advancement))) descendants.add(advancement);
            }
            Collections.sort(descendants, new Comparator<Advancement>() {
                @Override
                public int compare(Advancement left, Advancement right) {
                    int depth = Integer.compare(depth(left), depth(right));
                    return depth != 0 ? depth : left.getId().toString()
                            .compareTo(right.getId().toString());
                }
            });
            result.addAll(descendants);
        }
        return result;
    }

    private static Advancement rootOf(Advancement advancement) {
        Advancement root = advancement;
        while (root != null && root.getParent() != null) root = root.getParent();
        return root;
    }

    private static int depth(Advancement advancement) {
        int depth = 0;
        for (Advancement current = advancement; current != null && current.getParent() != null;
             current = current.getParent()) depth++;
        return depth;
    }

    private static boolean isComplete(EntityPlayerMP player, Advancement advancement) {
        try {
            AdvancementProgress progress = player.getAdvancements().getProgress(advancement);
            return progress != null && progress.isDone();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void grant(EntityPlayerMP player, Advancement advancement, String preferredCriterion) {
        try {
            AdvancementProgress progress = player.getAdvancements().getProgress(advancement);
            if (progress == null || progress.isDone()) return;
            if (advancement.getCriteria().containsKey(preferredCriterion)) {
                player.getAdvancements().grantCriterion(advancement, preferredCriterion);
                return;
            }
            for (String criterion : advancement.getCriteria().keySet()) {
                player.getAdvancements().grantCriterion(advancement, criterion);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void grantWithAncestors(EntityPlayerMP player, Advancement advancement) {
        if (player == null || advancement == null) return;
        grantAncestors(player, advancement.getParent());
        grant(player, advancement, "unlock");
    }

    private static void grantAncestors(EntityPlayerMP player, Advancement advancement) {
        if (advancement == null) return;
        grantAncestors(player, advancement.getParent());
        grant(player, advancement, "unlock");
    }

    static String natureBranch(String category, String id, String name) {
        String value = normalizeRelease(category);
        if ("fire".equals(value) || "katon".equals(value)) return "fire";
        if ("water".equals(value) || "suiton".equals(value)) return "water";
        if ("wind".equals(value) || "futon".equals(value)) return "wind";
        if ("lightning".equals(value) || "raiton".equals(value)) return "lightning";
        if ("earth".equals(value) || "doton".equals(value)) return "earth";
        return "";
    }

    static String kekkeiForJutsu(String category, String id, String name) {
        String release = normalizeRelease(category);
        if ("ice".equals(release) || "hyoton".equals(release)) return "hyoton";
        if ("boil".equals(release) || "futton".equals(release)) return "futton";
        if ("explosion".equals(release) || "bakuton".equals(release)) return "bakuton";
        if ("storm".equals(release) || "ranton".equals(release)) return "ranton";
        if ("lava".equals(release) || "yoton".equals(release)
                || "yooton".equals(release)) return "yooton";
        if ("scorch".equals(release) || "shakuton".equals(release)) return "shakuton";
        if ("crystal".equals(release) || "shoton".equals(release)) return "shoton";
        if ("wood".equals(release) || "mokuton".equals(release)) return "mokuton";
        if ("dust".equals(release) || "jinton".equals(release)
                || "jindon".equals(release)) return "jinton";
        if ("magnet".equals(release) || "jiton".equals(release)) return "jiton";
        if ("steel".equals(release) || "koton".equals(release)) return "koton";
        if ("mud".equals(release) || "deiton".equals(release)) return "deiton";
        if ("dojutsu".equals(release)) {
            return canonicalKekkei((id == null ? "" : id) + " "
                    + (name == null ? "" : name));
        }
        return "";
    }

    private static String learnerNature(String descriptor) {
        String value = descriptor == null ? "" : descriptor.toLowerCase(Locale.ROOT);
        if (contains(value, "katon", "fire nature", "fire release")) return "fire";
        if (contains(value, "suiton", "water nature", "water release")) return "water";
        if (contains(value, "futon", "wind nature", "wind release")) return "wind";
        if (contains(value, "raiton", "lightning nature", "lightning release")) return "lightning";
        if (contains(value, "doton", "earth nature", "earth release")) return "earth";
        return "";
    }

    private static String normalizeRelease(String value) {
        String clean = value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
        clean = clean.replace("style", "").replace("release", "")
                .replace("nature", "").replaceAll("[^a-z0-9]", "");
        return clean;
    }

    private static boolean sameName(String left, String right) {
        return stable(left).replace("_", "").replace("-", "")
                .equals(stable(right).replace("_", "").replace("-", ""));
    }

    static List<ResourceLocation> rootIdsForDisplay() {
        return ROOT_IDS;
    }

    private static String jutsuIcon(JutsuDefinition definition, String branch) {
        Item icon = definition == null ? null : definition.item();
        if (icon != null && icon.getRegistryName() != null) return icon.getRegistryName().toString();
        return natureIcon(branch, definition);
    }

    private static String natureIcon(String branch, JutsuDefinition definition) {
        if ("fire".equals(branch)) return "narutomod:katon";
        if ("water".equals(branch)) return "narutomod:suiton";
        if ("wind".equals(branch)) return "narutomod:futon";
        if ("lightning".equals(branch)) return "narutomod:raiton";
        if ("earth".equals(branch)) return "narutomod:doton";
        String[] candidates = new String[] {
                "narutomod:" + natureToken(branch),
                definition == null ? "" : definition.id()
        };
        for (String candidate : candidates) {
            String itemId = candidate;
            int slash = itemId.indexOf('/');
            if (slash >= 0) itemId = itemId.substring(0, slash);
            if (item(itemId) != null) return itemId;
        }
        return "narutomod:jutsu";
    }

    private static String natureToken(String branch) {
        if ("ice".equals(branch)) return "hyoton";
        if ("boil".equals(branch)) return "futton";
        if ("lava".equals(branch)) return "yooton";
        if ("storm".equals(branch)) return "ranton";
        if ("scorch".equals(branch)) return "shakuton";
        if ("explosion".equals(branch)) return "bakuton";
        if ("magnet".equals(branch)) return "jiton";
        if ("wood".equals(branch)) return "mokuton";
        if ("dust".equals(branch)) return "jinton";
        if ("crystal".equals(branch)) return "shoton";
        if ("steel".equals(branch)) return "koton";
        if ("mud".equals(branch)) return "deiton";
        if ("yin".equals(branch)) return "inton";
        return "jutsu";
    }

    private static String natureTitle(String branch) {
        return "other".equals(branch) ? "Other Techniques" : title(branch) + " Nature";
    }

    private static String natureDescription(String branch) {
        return "other".equals(branch) ? "Allowed techniques outside the nature branches."
                : title(branch) + " release techniques.";
    }

    private static String modeIcon(String id) {
        String value = id == null ? "" : id.toLowerCase(Locale.ROOT);
        if (value.contains("sage")) return "narutomod:senjutsu";
        if (value.contains("karma")) return "narutomod:karma";
        if (value.contains("cursemark")) return "ahznbcursemarkaddon:cursemark";
        if (value.contains("byakugou")) return "ahznbcursemarkaddon:byakugou_jutsu";
        if (value.contains("eight_gates")) return "narutomod:eightgates";
        if (value.contains("six_path")) return "narutomod:six_path_senjutsu";
        if (value.contains("tenseigan")) return "narutomod:tenseigan_chakra_mode";
        if (value.contains("biju")) return "narutomod:biju_cloakbody";
        if (value.contains("wind")) return "narutomod:scroll_wind_chakra_flow";
        if (value.contains("lightning")) return "narutomod:scroll_lightning_chakra_mode";
        if (value.contains("lava")) return "narutomod:yooton";
        if (value.contains("water") || value.contains("rain")) return "narutomod:suiton";
        if (value.contains("earth")) return "narutomod:doton";
        return item(id) == null ? "minecraft:book" : id;
    }

    private static void offerKekkei(Map<String, KekkeiGroup> groups, Item item) {
        if (!allowed(item) || item.getRegistryName() == null) return;
        ResourceLocation name = item.getRegistryName();
        String key = canonicalKekkei(name.getResourcePath());
        if (key.isEmpty()) return;
        KekkeiGroup group = groups.get(key);
        if (group == null) {
            group = new KekkeiGroup(key, kekkeiTitle(key));
            groups.put(key, group);
        }
        group.triggers.add(item);
        int score = kekkeiIconScore(name, key);
        if (group.icon == null || score > group.iconScore) {
            group.icon = item;
            group.iconScore = score;
        }
    }

    static String canonicalKekkei(String path) {
        if (path == null) return "";
        String value = path.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (value.contains("rinnesharingan")) return "rinne_sharingan";
        if (value.contains("ketsuryugan")) return "ketsuryugan";
        if (value.contains("byakugan")) return "byakugan";
        if (value.contains("sharingan")) return "sharingan";
        if (value.contains("rinnegan")) return "rinnegan";
        if (value.contains("tenseigan")) return "tenseigan";
        if (value.contains("shikotsumyaku")) return "shikotsumyaku";
        if (value.contains("hyoton")) return "hyoton";
        if (value.contains("futton")) return "futton";
        if (value.contains("bakuton")) return "bakuton";
        if (value.contains("ranton")) return "ranton";
        if (value.contains("yooton") || value.contains("yoton")) return "yooton";
        if (value.contains("shakuton")) return "shakuton";
        if (value.contains("shoton")) return "shoton";
        if (value.contains("mokuton")) return "mokuton";
        if (value.contains("jinton") || value.contains("jindon")) return "jinton";
        if (value.contains("jiton")) return "jiton";
        if (value.contains("koton")) return "koton";
        if (value.contains("deiton")) return "deiton";
        if (value.endsWith("genkai")) return stable(path.substring(0, path.length() - "genkai".length()));
        return "";
    }

    private static String kekkeiTitle(String key) {
        if ("rinne_sharingan".equals(key)) return "Rinne Sharingan";
        if ("hyoton".equals(key)) return "Ice Release";
        if ("futton".equals(key)) return "Boil Release";
        if ("bakuton".equals(key)) return "Explosion Release";
        if ("ranton".equals(key)) return "Storm Release";
        if ("yooton".equals(key)) return "Lava Release";
        if ("shakuton".equals(key)) return "Scorch Release";
        if ("shoton".equals(key)) return "Crystal Release";
        if ("mokuton".equals(key)) return "Wood Release";
        if ("jinton".equals(key)) return "Dust Release";
        if ("jiton".equals(key)) return "Magnet Release";
        if ("koton".equals(key)) return "Steel Release";
        if ("deiton".equals(key)) return "Mud Release";
        if ("shikotsumyaku".equals(key)) return "Shikotsumyaku";
        return title(key);
    }

    private static int kekkeiIconScore(ResourceLocation name, String key) {
        String path = name.getResourcePath().toLowerCase(Locale.ROOT);
        String compact = path.replaceAll("[^a-z0-9]", "");
        int score = "narutomod".equals(name.getResourceDomain()) ? 100 : 0;
        String token = key.replace("_", "");
        if (compact.equals(token) || compact.equals(token + "helmet")) score += 60;
        if (contains(compact, "dynamic", "tomoe", "mangekyo", "eternal")) score -= 20;
        return score;
    }

    private static boolean isKekkeiPath(String path) {
        if (path == null) return false;
        String value = path.toLowerCase(Locale.ROOT);
        if (contains(value, "armor", "robe", "clothes", "jutsu", "scroll", "sword",
                "cloak", "mode", "susanoo")) return false;
        return value.endsWith("genkai") || value.contains("sharingan") || value.contains("byakugan")
                || value.contains("rinnegan") || value.contains("ketsuryugan")
                || value.contains("tenseigan")
                || value.contains("shikotsumyaku")
                || contains(value, "hyoton", "futton", "bakuton", "ranton", "yooton", "yoton",
                "shakuton", "shoton", "mokuton", "jinton", "jiton", "koton", "deiton", "jindon");
    }

    private static boolean contains(String value, String... tokens) {
        for (String token : tokens) if (value.contains(token)) return true;
        return false;
    }

    private static String title(String id) {
        String value = id == null ? "" : id;
        int colon = value.indexOf(':');
        if (colon >= 0) value = value.substring(colon + 1);
        value = value.replace('_', ' ').replace('-', ' ').trim();
        StringBuilder result = new StringBuilder();
        for (String word : value.split("\\s+")) {
            if (word.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.length() == 0 ? "Advancement" : result.toString();
    }

    private static String stable(String value) {
        String clean = value == null ? "" : value.toLowerCase(Locale.ROOT);
        clean = clean.replace(':', '_').replace('/', '_').replaceAll("[^a-z0-9._-]", "_");
        return clean.length() > 80 ? clean.substring(0, 80) : clean;
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("rebornaddon", path);
    }

    private static final class InventoryState {
        private final Item kunai;
        private final Item shuriken;
        private boolean hasKunai;
        private boolean hasShuriken;

        private InventoryState(Item kunai, Item shuriken) {
            this.kunai = kunai;
            this.shuriken = shuriken;
        }
    }

    private static final class JutsuUnlock {
        private final Advancement advancement;
        private final JutsuDefinition definition;

        private JutsuUnlock(Advancement advancement, JutsuDefinition definition) {
            this.advancement = advancement;
            this.definition = definition;
        }
    }

    private static final class KekkeiGroup {
        private final String key;
        private final String title;
        private final Set<Item> triggers = Collections.newSetFromMap(
                new IdentityHashMap<Item, Boolean>());
        private Item icon;
        private int iconScore = Integer.MIN_VALUE;

        private KekkeiGroup(String key, String title) {
            this.key = key;
            this.title = title;
        }
    }
}
