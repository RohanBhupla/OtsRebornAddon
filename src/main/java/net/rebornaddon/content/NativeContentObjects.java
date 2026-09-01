package net.rebornaddon.content;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.rebornaddon.RebornAddonMod;
import net.rebornaddon.content.block.BlockRebornMailbox;
import net.rebornaddon.content.item.ItemNpcPathTool;

@Mod.EventBusSubscriber(modid = RebornAddonMod.MODID)
public final class NativeContentObjects {
    public static final BlockRebornMailbox MAILBOX = new BlockRebornMailbox();
    public static final ItemBlock MAILBOX_ITEM = createMailboxItem();
    public static final ItemNpcPathTool PATH_TOOL = new ItemNpcPathTool();

    private NativeContentObjects() {
    }

    private static ItemBlock createMailboxItem() {
        ItemBlock item = new ItemBlock(MAILBOX);
        item.setRegistryName(MAILBOX.getRegistryName());
        item.setCreativeTab(CreativeTabs.DECORATIONS);
        return item;
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(MAILBOX);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(MAILBOX_ITEM, PATH_TOOL);
    }
}
