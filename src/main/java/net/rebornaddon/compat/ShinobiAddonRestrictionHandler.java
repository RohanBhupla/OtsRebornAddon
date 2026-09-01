package net.rebornaddon.compat;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionType;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLInterModComms;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import net.rebornaddon.store.StoreCatalog;
import net.rebornaddon.policy.ContentPolicyService;
import net.rebornaddon.policy.PolicyAction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ShinobiAddonRestrictionHandler {

    public static final ShinobiAddonRestrictionHandler INSTANCE = new ShinobiAddonRestrictionHandler();

    private static final String MODID = "shinobiaddon";
    private static final String ENTITY_PACKAGE = "com.leolifeless.shinobiaddon.entity.";
    private static final Set<String> ALLOWED_COMMANDS = new HashSet<String>(Arrays.asList(
            "com.leolifeless.shinobiaddon.command.DojutsuCommand",
            "com.leolifeless.shinobiaddon.command.DojutsuOwnerCommand"
    ));
    private static final String[] ALLOWED_ENTITY_PACKAGES = new String[] {
            ENTITY_PACKAGE + "byakugan.",
            ENTITY_PACKAGE + "ketsuryugan.",
            ENTITY_PACKAGE + "sharingan."
    };
    private static final Set<String> ALLOWED_ITEMS = new HashSet<String>(Arrays.asList(
            "shinobiaddon:dynamic_sharingan",
            "shinobiaddon:dynamic_byakugan",
            "shinobiaddon:dynamic_ketsuryugan",
            "shinobiaddon:anbu_mask_menma_helmet",
            "shinobiaddon:anbu_mask_type_1",
            "shinobiaddon:anbu_mask_type_2",
            "shinobiaddon:anbu_mask_type_3",
            "shinobiaddon:anbu_mask_type_4",
            "shinobiaddon:anbu_mask_type_5",
            "shinobiaddon:anbu_mask_type_6",
            "shinobiaddon:anbu_mask_type_7",
            "shinobiaddon:anbu_mask_type_8",
            "shinobiaddon:anbu_robe_black_body",
            "shinobiaddon:anbu_robe_blue_body",
            "shinobiaddon:anbu_robe_green_body",
            "shinobiaddon:anbu_robe_light_blue_body",
            "shinobiaddon:anbu_robe_orange_body",
            "shinobiaddon:anbu_robe_pink_body",
            "shinobiaddon:anbu_robe_purple_body",
            "shinobiaddon:anbu_robe_red_body",
            "shinobiaddon:anbu_robe_white_body",
            "shinobiaddon:anbu_robe_yellow_body",
            "shinobiaddon:akatsuki_robe_blue",
            "shinobiaddon:akatsuki_robe_dark_green",
            "shinobiaddon:akatsuki_robe_gray",
            "shinobiaddon:akatsuki_robe_green",
            "shinobiaddon:akatsuki_robe_light_blue",
            "shinobiaddon:akatsuki_robe_orange",
            "shinobiaddon:akatsuki_robe_pink",
            "shinobiaddon:akatsuki_robe_purple",
            "shinobiaddon:akatsuki_robe_redblack",
            "shinobiaddon:akatsuki_robe_whitered",
            "shinobiaddon:akatsuki_robe_yellow",
            "shinobiaddon:konoha_pants_2",
            "shinobiaddon:konoha_pants_3",
            "shinobiaddon:konoha_shirt_2",
            "shinobiaddon:konoha_shirt_3"
    ));
    private static final Set<String> BLOCKED_ITEMS = new HashSet<String>(Arrays.asList(
            "minecraft:spawn_egg",
            "narutomod:hanaton",
            "narutomod:girar_cla_genkai",
            "narutomod:jashin",
            "narutomod:otsutsuki_fruit",
            "narutomod:deiton",
            "narutomod:mini_six_path_senjutsu",
            "narutomod:flower_shuriken",
            "narutomod:aburame_genkai",
            "narutomod:akimichi_genkai",
            "narutomod:nara_genkai",
            "narutomod:uzumaki_genkai",
            "narutomod:namikaze_genkai",
            "narutomod:koton",
            "narutomod:meiton",
            "narutomod:jindon",
            "narutomod:chakrafruit",
            "narutomod:karma",
            "narutomod:tenseigan_chakra_mode",
            "narutomod:amaterasublock",
            "narutomod:clothes_tsukikagebody",
            "narutomod:clothes_tsukikagehelmet",
            "narutomod:senbon_arm",
            "narutomod:pre_rinneganhelmet",
            "narutomod:totsuka_sword",
            "narutomod:whitezetsuflesh",
            "narutomod:ashbones",
            "narutomod:kekkei_mora",
            "narutomod:sage_staff",
            "narutomod:six_path_senjutsu",
            "narutomod:kamuiblock",
            "narutomod:portalblock",
            "narutomod:mud",
            "narutomod:water_still",
            "narutomod:light_source",
            "narutomod:zzz",
            "narutomod:rinneganbody",
            "narutomod:rinneganlegs",
            "narutomod:cosmic_akatsuki_robebody",
            "ahznbcursemarkaddon:baburu",
            "ahznbcursemarkaddon:byakugou_jutsu",
            "ahznbcursemarkaddon:inku",
            "ahznbcursemarkaddon:inuzuka",
            "ahznbcursemarkaddon:konchu",
            "ahznbcursemarkaddon:koraru",
            "ahznbcursemarkaddon:kouton",
            "ahznbcursemarkaddon:kumo_nenkin_bow",
            "ahznbcursemarkaddon:momoshiki_core",
            "ahznbcursemarkaddon:black_receiver",
            "ahznbcursemarkaddon:steel",
            "ahznbcursemarkaddon:steel_claw",
            "ahznbcursemarkaddon:jiton",
            "kgtweaks:jiton",
            "kgtweaks:magnet_release",
            "kgtweaks:uzumaki_genkai",
            "kgtweaks:nara_genkai",
            "kgtweaks:akimichi_genkai",
            "kgtweaks:aburame_genkai",
            "kabutoaddon:dna",
            "kabutoaddon:edo_tensei_scroll",
            "kabutoaddon:karma_seal",
            "kabutoaddon:kg_remover",
            "kabutoaddon:kg_reroll",
            "kabutoaddon:sumirehime",
            "kabutoaddon:rinne_sharingan_helmet",
            "narutomodaddon:item_chakra_charge",
            "narutomodaddon:chakra_charge",
            "betterquesting:extra_life",
            "betterquesting:guide",
            "betterquesting:guide_book",
            "betterquesting:heart_full",
            "betterquesting:heart_half",
            "betterquesting:heart_quarter",
            "betterquesting:placeholder",
            "ftbquests:custom_icon",
            "customnpcs:npcborder"
    ));
    private static final Set<String> HIDDEN_ONLY_ITEMS = new HashSet<String>(Arrays.asList(
            "rebornaddon:water_chakra_mode",
            "rebornaddon:rain_chakra_mode",
            "rebornaddon:earth_chakra_mode"
    ));
    private static final String[] HIDDEN_ONLY_PREFIXES = new String[] {
            "narutomod:biju_cloak",
            "narutomod:biju_cloak_2",
            "narutomod:normal_biju_cloak",
            "kabutoaddon:biju_cloak",
            "ahznbcursemarkaddon:susanoo_armor"
    };
    private static final String[] BLOCKED_PREFIXES = new String[] {
            "narutomod:ninja_armor_moon",
            "narutomod:ninja_armor_moon_r",
            "narutomod:ketsuryugan_",
            "narutomod:prismal_dojutsu",
            "narutomod:nebula_dojutsu",
            "narutomod:senrigan_dojutsu",
            "narutomod:rinnesharingan",
            "narutomod:byakurinnesharingan",
            "ahznbcursemarkaddon:blue_rinnegan",
            "ahznbcursemarkaddon:byakugou",
            "ahznbcursemarkaddon:byakusharingan",
            "ahznbcursemarkaddon:coral_armor",
            "ahznbcursemarkaddon:dudu_rinnegan",
            "ahznbcursemarkaddon:e_mangekyo_sharingan_inferno",
            "ahznbcursemarkaddon:e_mangekyo_sharingan_magicians",
            "ahznbcursemarkaddon:e_mangekyo_sharingan_preta",
            "ahznbcursemarkaddon:eye",
            "ahznbcursemarkaddon:fake_kokugan",
            "ahznbcursemarkaddon:glass_eye",
            "ahznbcursemarkaddon:ishiki_core_",
            "ahznbcursemarkaddon:jougan",
            "ahznbcursemarkaddon:ketsuryugan",
            "ahznbcursemarkaddon:kingan",
            "ahznbcursemarkaddon:madara_rinnegan",
            "ahznbcursemarkaddon:mangekyo_sharingan_inferno",
            "ahznbcursemarkaddon:mangekyo_sharingan_madara",
            "ahznbcursemarkaddon:mangekyo_sharingan_magicians",
            "ahznbcursemarkaddon:mangekyo_sharingan_preta",
            "ahznbcursemarkaddon:mike_jougan",
            "ahznbcursemarkaddon:ratja_eye",
            "ahznbcursemarkaddon:red_rinnegan",
            "ahznbcursemarkaddon:yashi_eye"
    };
    private static final Set<ResourceLocation> JEI_HIDE_SENT = new HashSet<ResourceLocation>();

    private ShinobiAddonRestrictionHandler() {
    }

    public static boolean shouldRestrict(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return shouldRestrictItem(stack.getItem()) || isCustomPotionStack(stack) || isBlockedStackVariant(stack);
    }

    public static boolean shouldRestrictName(ResourceLocation name) {
        if (name == null) {
            return false;
        }

        String id = name.toString();
        if (isShinobiName(name)) {
            return !ALLOWED_ITEMS.contains(id);
        }
        if (BLOCKED_ITEMS.contains(id) || "rerollthingy".equals(name.getResourceDomain())) {
            return true;
        }
        if ("itemfilters".equals(name.getResourceDomain())) {
            return true;
        }
        if ("waystones".equals(name.getResourceDomain())) {
            return !"waystone".equals(name.getResourcePath());
        }
        if (!"minecraft".equals(name.getResourceDomain())
                && name.getResourcePath().toLowerCase().contains("potion")) {
            return true;
        }
        if (isSpawnEggName(name)) {
            return true;
        }
        for (String prefix : BLOCKED_PREFIXES) {
            if (id.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static boolean shouldRestrictItem(Item item) {
        if (item == null) {
            return false;
        }
        return shouldRestrictName(item.getRegistryName()) || isSpawnEgg(item) || isVariedCommoditiesEquipment(item);
    }

    public static boolean shouldHide(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return shouldRestrict(stack) || isHiddenOnly(stack.getItem())
                || ContentPolicyService.clientDenies(PolicyAction.CREATIVE, stack)
                || ContentPolicyService.clientDenies(PolicyAction.JEI, stack);
    }

    public static boolean shouldHideItem(Item item) {
        if (item == null) return false;
        ResourceLocation name = item.getRegistryName();
        String id = name == null ? "" : name.toString();
        return shouldRestrictItem(item) || isHiddenOnly(item)
                || ContentPolicyService.clientDenies(PolicyAction.CREATIVE, id)
                || ContentPolicyService.clientDenies(PolicyAction.JEI, id);
    }

    public static boolean shouldBlockRecipeOutput(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation name = stack.getItem().getRegistryName();
        return shouldRestrict(stack)
                || ContentPolicyService.INSTANCE.denies(PolicyAction.CRAFT, stack)
                || StoreCatalog.isRestrictedCraftingOutput(stack)
                || name != null && "armourers_workshop".equals(name.getResourceDomain());
    }

    public static void applyVisibilityRules() {
        for (Item item : ForgeRegistries.ITEMS.getValuesCollection()) {
            ResourceLocation name = item.getRegistryName();
            if (!shouldHideItem(item)) {
                continue;
            }

            item.setCreativeTab(null);
            if (name != null && JEI_HIDE_SENT.add(name)) {
                sendJeiHide(item);
            }
        }
    }

    public static List<ItemStack> visibilityStacksFor(Item item) {
        NonNullList<ItemStack> found = NonNullList.create();
        List<ItemStack> stacks = new ArrayList<ItemStack>();
        Set<String> seen = new HashSet<String>();

        addSubItems(item, CreativeTabs.SEARCH, found);
        for (CreativeTabs tab : CreativeTabs.CREATIVE_TAB_ARRAY) {
            if (tab != null) {
                addSubItems(item, tab, found);
            }
        }

        for (ItemStack stack : found) {
            addStack(stacks, seen, stack);
        }

        addStack(stacks, seen, new ItemStack(item));
        addStack(stacks, seen, new ItemStack(item, 1, OreDictionary.WILDCARD_VALUE));
        for (int meta = 0; meta <= 15; meta++) {
            addStack(stacks, seen, new ItemStack(item, 1, meta));
        }

        return stacks;
    }

    private static void addSubItems(Item item, CreativeTabs tab, NonNullList<ItemStack> stacks) {
        try {
            item.getSubItems(tab, stacks);
        } catch (RuntimeException ignored) {
        }
    }

    private static void addStack(List<ItemStack> stacks, Set<String> seen, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        ResourceLocation name = stack.getItem().getRegistryName();
        if (name == null) {
            return;
        }

        String key = name.toString() + ":" + stack.getMetadata();
        if (seen.add(key)) {
            stacks.add(stack);
        }
    }

    private static void sendJeiHide(Item item) {
        if (!Loader.isModLoaded("jei") || item == null) {
            return;
        }

        for (ItemStack stack : visibilityStacksFor(item)) {
            FMLInterModComms.sendMessage("jei", "hide", stack);
            FMLInterModComms.sendMessage("jei", "itemBlacklist", stack);
            FMLInterModComms.sendMessage("jei", "hide_item", stack);
        }

        ResourceLocation name = item.getRegistryName();
        if (name != null) {
            String id = name.toString();
            FMLInterModComms.sendMessage("jei", "hide", id);
            FMLInterModComms.sendMessage("jei", "itemBlacklist", id);
            FMLInterModComms.sendMessage("jei", "hide_item", id);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        World world = MinecraftAccess.world(event.player);
        if (event.phase != TickEvent.Phase.END || world == null || world.isRemote
                || world.getTotalWorldTime() % 20L != 0L) {
            return;
        }

        cleanPlayer(event.player);
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        cleanPlayer(event.player);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        cleanPlayer(event.player);
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        cleanPlayer(event.player);
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isBlocked(event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isBlocked(event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (isBlocked(event.getItemStack()) || isBlockedEntity(event.getTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        Block block = event.getPlacedBlock().getBlock();

        if (isFromShinobiAddon(block) || isBlocked(event.getItemInHand())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (isBlocked(event.getEntityPlayer().getHeldItemMainhand())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onItemPickup(EntityItemPickupEvent event) {
        if (removeEntityItem(event.getItem())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onItemToss(ItemTossEvent event) {
        removeEntityItem(event.getEntityItem());
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinWorldEvent event) {
        Entity entity = event.getEntity();

        if (entity instanceof EntityItem) {
            if (removeEntityItem((EntityItem) entity)) {
                event.setCanceled(true);
            }
            return;
        }

        if (isBlockedEntity(entity)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        cleanPlayer(event.player);
    }

    @SubscribeEvent
    public void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        cleanPlayer(event.player);
    }

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        String name = event.getCommand().getClass().getName();

        if (name.startsWith("com.leolifeless.shinobiaddon.command.") && !ALLOWED_COMMANDS.contains(name)) {
            event.setCanceled(true);
        }
    }

    private static void cleanPlayer(EntityPlayer player) {
        clean(player.inventory.mainInventory);
        clean(player.inventory.armorInventory);
        clean(player.inventory.offHandInventory);
    }

    private static void clean(NonNullList<ItemStack> items) {
        for (int i = 0; i < items.size(); i++) {
            if (isBlocked(items.get(i))) {
                items.set(i, ItemStack.EMPTY);
            }
        }
    }

    private static boolean removeEntityItem(EntityItem entityItem) {
        if (!isBlocked(entityItem.getItem())) {
            return false;
        }

        entityItem.setDead();
        return true;
    }

    private static boolean isBlocked(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        ResourceLocation name = stack.getItem().getRegistryName();
        return shouldRestrict(stack);
    }

    private static boolean isFromShinobiAddon(Block block) {
        if (block == null) {
            return false;
        }

        ResourceLocation name = block.getRegistryName();
        return name != null && isShinobiName(name);
    }

    private static boolean isBlockedEntity(Entity entity) {
        if (entity == null) {
            return false;
        }

        String name = entity.getClass().getName();
        if (!name.startsWith(ENTITY_PACKAGE)) {
            return false;
        }

        for (String allowedPackage : ALLOWED_ENTITY_PACKAGES) {
            if (name.startsWith(allowedPackage)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isShinobiName(ResourceLocation name) {
        return name.toString().startsWith(MODID + ":");
    }

    private static boolean isSpawnEgg(Item item) {
        if (item instanceof ItemMonsterPlacer) {
            return true;
        }
        ResourceLocation name = item.getRegistryName();
        if (isSpawnEggName(name)) {
            return true;
        }
        String className = item.getClass().getName().toLowerCase();
        return className.contains("spawnegg") || className.contains("spawn_egg");
    }

    private static boolean isSpawnEggName(ResourceLocation name) {
        if (name == null) {
            return false;
        }
        String path = name.getResourcePath().toLowerCase();
        return path.equals("spawn_egg") || path.contains("spawn_egg") || path.contains("spawnegg");
    }

    private static boolean isCustomPotionStack(ItemStack stack) {
        Item item = stack.getItem();
        if (item != Items.POTIONITEM && item != Items.SPLASH_POTION
                && item != Items.LINGERING_POTION && item != Items.TIPPED_ARROW) {
            return false;
        }

        PotionType type = PotionUtils.getPotionFromItem(stack);
        ResourceLocation name = type == null ? null : type.getRegistryName();
        return name != null && !"minecraft".equals(name.getResourceDomain());
    }

    private static boolean isBlockedStackVariant(ItemStack stack) {
        if (!"patchouli:guide_book".equals(registryName(stack.getItem()))) {
            return false;
        }

        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && "ahznbstweaks:guide".equals(tag.getString("patchouli:book"));
    }

    private static boolean isVariedCommoditiesEquipment(Item item) {
        ResourceLocation name = item.getRegistryName();
        if (name == null || !"variedcommodities".equals(name.getResourceDomain())) {
            return false;
        }

        for (Class<?> type = item.getClass(); type != null; type = type.getSuperclass()) {
            String className = type.getSimpleName().toLowerCase();
            if (className.contains("weapon") || className.contains("armor")
                    || className.contains("shield") || className.contains("staff")
                    || className.contains("wand") || className.contains("gun")
                    || className.contains("musket") || className.contains("crossbow")
                    || className.contains("bow") || className.contains("slingshot")
                    || className.contains("kunai") || className.contains("grenade")) {
                return true;
            }
        }
        return false;
    }

    private static String registryName(Item item) {
        ResourceLocation name = item == null ? null : item.getRegistryName();
        return name == null ? "" : name.toString();
    }

    private static boolean isHiddenOnly(Item item) {
        String id = registryName(item);
        if (HIDDEN_ONLY_ITEMS.contains(id)) {
            return true;
        }
        for (String prefix : HIDDEN_ONLY_PREFIXES) {
            if (id.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
