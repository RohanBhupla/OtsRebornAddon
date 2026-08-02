package net.rebornaddon.credits;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.rebornaddon.compat.MinecraftAccess;

public final class CreatorCreditsHandler {
    public static final CreatorCreditsHandler INSTANCE = new CreatorCreditsHandler();

    private CreatorCreditsHandler() {
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP) || MinecraftAccess.isRemote(event.player)) {
            return;
        }

        MinecraftAccess.sendMessage(event.player, joinMessage());
    }

    public static TextComponentString joinMessage() {
        TextComponentString message = new TextComponentString(
                "Full credit for many of these mods goes to the following creators. ");
        Style messageStyle = MinecraftAccess.style(message);
        MinecraftAccess.setColor(messageStyle, TextFormatting.GRAY);

        TextComponentString link = new TextComponentString("Click here to see their info.");
        Style linkStyle = MinecraftAccess.style(link);
        MinecraftAccess.setColor(linkStyle, TextFormatting.GOLD);
        MinecraftAccess.setUnderlined(linkStyle, Boolean.TRUE);
        MinecraftAccess.setClickEvent(linkStyle, new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reborncredits"));
        MinecraftAccess.setHoverEvent(linkStyle, new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new TextComponentString("Open creator credits")));
        MinecraftAccess.appendSibling(message, link);
        return message;
    }
}
