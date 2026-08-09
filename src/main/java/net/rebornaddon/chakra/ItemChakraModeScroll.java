package net.rebornaddon.chakra;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.rebornaddon.RebornAddonMod;

import java.util.List;

public final class ItemChakraModeScroll extends Item {
    private final ChakraMode mode;

    ItemChakraModeScroll(ChakraMode mode) {
        this.mode = mode;
        setRegistryName(RebornAddonMod.MODID, "scroll_" + mode.key() + "_chakra_mode");
        setUnlocalizedName(RebornAddonMod.MODID + ".scroll_" + mode.key() + "_chakra_mode");
        setCreativeTab(CreativeTabs.COMBAT);
        setMaxStackSize(1);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            RebornAddonMod.proxy.openChakraLearnGui(mode);
        }
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add("B-rank chakra mode scroll");
    }

    public ChakraMode getMode() {
        return mode;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return mode.displayName() + " Scroll";
    }
}
