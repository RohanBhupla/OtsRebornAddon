package net.rebornaddon.chakra;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.rebornaddon.RebornAddonMod;

public final class ItemChakraModeFocus extends Item {
    private final ChakraMode mode;

    ItemChakraModeFocus(ChakraMode mode) {
        this.mode = mode;
        setRegistryName(RebornAddonMod.MODID, mode.key() + "_chakra_mode");
        setUnlocalizedName(RebornAddonMod.MODID + "." + mode.key() + "_chakra_mode");
        setMaxStackSize(1);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote && player instanceof EntityPlayerMP) {
            ChakraModeHandler.INSTANCE.migrateLegacy((EntityPlayerMP) player, mode);
        }
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
    }

    public ChakraMode getMode() {
        return mode;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return mode.displayName();
    }
}
