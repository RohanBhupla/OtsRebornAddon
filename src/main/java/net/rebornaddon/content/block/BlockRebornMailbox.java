package net.rebornaddon.content.block;

import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.rebornaddon.content.NativeContentPluginBridge;
import net.rebornaddon.quest.NativeQuestService;
import net.rebornaddon.village.network.RebornAddonNetwork;

public final class BlockRebornMailbox extends Block {
    public BlockRebornMailbox() {
        super(Material.WOOD);
        setRegistryName("rebornaddon", "mailbox");
        setUnlocalizedName("rebornaddon.mailbox");
        setHardness(2.0F);
        setResistance(6.0F);
        setSoundType(SoundType.WOOD);
        setCreativeTab(CreativeTabs.DECORATIONS);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        if (!world.isRemote && player instanceof EntityPlayerMP) {
            EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
            if (!NativeContentPluginBridge.isAvailable()) {
                serverPlayer.sendStatusMessage(new net.minecraft.util.text.TextComponentString(
                        net.minecraft.util.text.TextFormatting.RED + "The server mail service is unavailable."), true);
                return true;
            }
            NativeQuestService.send(serverPlayer);
            JsonObject route = new JsonObject();
            route.addProperty("route", "mail");
            RebornAddonNetwork.sendNativeContentSync(serverPlayer, "interaction", route.toString());
        }
        return true;
    }
}
