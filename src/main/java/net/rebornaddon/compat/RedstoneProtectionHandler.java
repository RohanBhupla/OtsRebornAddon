package net.rebornaddon.compat;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RedstoneProtectionHandler {
    public static final RedstoneProtectionHandler INSTANCE = new RedstoneProtectionHandler();

    private static final Set<String> RESTRICTED = new HashSet<String>(Arrays.asList(
            "redstone", "redstone_block", "repeater", "unpowered_repeater", "powered_repeater",
            "comparator", "unpowered_comparator", "powered_comparator", "redstone_torch",
            "unlit_redstone_torch", "redstone_lamp", "lit_redstone_lamp", "daylight_detector",
            "daylight_detector_inverted", "lever", "stone_button", "wooden_button",
            "stone_pressure_plate", "wooden_pressure_plate", "light_weighted_pressure_plate",
            "heavy_weighted_pressure_plate", "tripwire", "tripwire_hook", "piston",
            "sticky_piston", "piston_head", "piston_extension", "observer", "dispenser",
            "dropper", "hopper", "trapped_chest", "detector_rail", "golden_rail",
            "activator_rail", "tnt", "command_block", "chain_command_block",
            "repeating_command_block", "command_block_minecart", "structure_block"
    ));

    private final ConcurrentHashMap<UUID, Long> nextNotice = new ConcurrentHashMap<UUID, Long>();

    private RedstoneProtectionHandler() {
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!isOperator(event.getEntityPlayer())
                && (isRestricted(event.getItemStack()) || isRestricted(event.getWorld().getBlockState(event.getPos())))) {
            event.setCanceled(true);
            notifyPlayer(event.getEntityPlayer());
        }
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!isOperator(event.getEntityPlayer()) && isRestricted(event.getWorld().getBlockState(event.getPos()))) {
            event.setCanceled(true);
            notifyPlayer(event.getEntityPlayer());
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!isOperator(event.getEntityPlayer()) && isRestricted(event.getItemStack())) {
            event.setCanceled(true);
            notifyPlayer(event.getEntityPlayer());
        }
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.PlaceEvent event) {
        EntityPlayer player = event.getPlayer();
        if (player != null && !isOperator(player)
                && (isRestricted(event.getItemInHand()) || isRestricted(event.getPlacedBlock()))) {
            event.setCanceled(true);
            notifyPlayer(player);
        }
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() != null && !isOperator(event.getPlayer()) && isRestricted(event.getState())) {
            event.setCanceled(true);
            notifyPlayer(event.getPlayer());
        }
    }

    @SubscribeEvent
    public void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (isOperator(event.player) || !isRestricted(event.crafting)) {
            return;
        }

        event.crafting.setCount(0);
        event.player.inventory.markDirty();
        if (event.player instanceof EntityPlayerMP) {
            ((EntityPlayerMP) event.player).sendContainerToPlayer(event.player.openContainer);
        }
        notifyPlayer(event.player);
    }

    private static boolean isOperator(EntityPlayer player) {
        return player != null && player.canUseCommand(2, "rebornaddon.redstone");
    }

    private static boolean isRestricted(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation name = stack.getItem().getRegistryName();
        if (isRestrictedName(name)) {
            return true;
        }
        return stack.getItem() instanceof ItemBlock
                && isRestricted(((ItemBlock) stack.getItem()).getBlock().getDefaultState());
    }

    private static boolean isRestricted(IBlockState state) {
        if (state == null) {
            return false;
        }
        Block block = state.getBlock();
        return isRestrictedName(block.getRegistryName()) || block.canProvidePower(state);
    }

    private static boolean isRestrictedName(ResourceLocation name) {
        return name != null && "minecraft".equals(name.getResourceDomain())
                && RESTRICTED.contains(name.getResourcePath());
    }

    private void notifyPlayer(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long allowed = nextNotice.get(player.getUniqueID());
        if (allowed != null && allowed.longValue() > now) {
            return;
        }
        nextNotice.put(player.getUniqueID(), Long.valueOf(now + 1500L));
        player.sendStatusMessage(new TextComponentString(TextFormatting.RED
                + "Redstone and command-block systems are restricted to operators."), true);
    }
}
