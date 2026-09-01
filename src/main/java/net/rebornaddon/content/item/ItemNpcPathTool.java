package net.rebornaddon.content.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.rebornaddon.content.NativeContentService;

public final class ItemNpcPathTool extends Item {
    public ItemNpcPathTool() {
        setRegistryName("rebornaddon", "npc_path_tool");
        setUnlocalizedName("rebornaddon.npc_path_tool");
        setMaxStackSize(1);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos,
                                      EnumHand hand, EnumFacing facing,
                                      float hitX, float hitY, float hitZ) {
        if (world.isRemote) return EnumActionResult.SUCCESS;
        if (!(player instanceof EntityPlayerMP) || !player.canUseCommand(2, "reborncontent")) {
            return EnumActionResult.FAIL;
        }
        ItemStack stack = player.getHeldItem(hand);
        String pathId = stack.hasTagCompound() ? stack.getTagCompound().getString("PathId") : "";
        if (pathId.isEmpty()) return EnumActionResult.FAIL;
        NativeContentService.INSTANCE.updatePath((EntityPlayerMP) player, pathId,
                pos.offset(facing), player.isSneaking());
        return EnumActionResult.SUCCESS;
    }
}
