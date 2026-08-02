package net.rebornaddon.compat;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
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
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Arrays;
import java.util.HashSet;
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
            "shinobiaddon:asura_path_armor_body",
            "shinobiaddon:konoha_pants_2",
            "shinobiaddon:konoha_pants_3",
            "shinobiaddon:konoha_shirt_2",
            "shinobiaddon:konoha_shirt_3",
            "shinobiaddon:tenseigan_chakra_mode_body",
            "shinobiaddon:tenseigan_chakra_mode_legs",
            "shinobiaddon:tenseigan_chakra_mode_boots"
    ));

    private ShinobiAddonRestrictionHandler() {
    }

    public static boolean shouldRestrict(ItemStack stack) {
        return isBlocked(stack);
    }

    public static boolean shouldRestrictName(ResourceLocation name) {
        return name != null && isShinobiName(name) && !ALLOWED_ITEMS.contains(name.toString());
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
        return shouldRestrictName(name);
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
}
