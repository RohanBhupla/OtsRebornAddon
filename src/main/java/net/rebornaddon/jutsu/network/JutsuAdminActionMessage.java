package net.rebornaddon.jutsu.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.jutsu.JutsuConfigurationService;

public final class JutsuAdminActionMessage implements IMessage {
    private int action;
    private String id = "";
    private String property = "";
    private String value = "";

    public JutsuAdminActionMessage() {
    }

    public JutsuAdminActionMessage(int action, String id, String property, String value) {
        this.action = action;
        this.id = clean(id, 200);
        this.property = clean(property, 40);
        this.value = clean(value, 8192);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        action = buffer.readUnsignedByte();
        id = clean(ByteBufUtils.readUTF8String(buffer), 200);
        property = clean(ByteBufUtils.readUTF8String(buffer), 40);
        value = clean(ByteBufUtils.readUTF8String(buffer), 8192);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeByte(action);
        ByteBufUtils.writeUTF8String(buffer, id);
        ByteBufUtils.writeUTF8String(buffer, property);
        ByteBufUtils.writeUTF8String(buffer, value);
    }

    private static String clean(String input, int maximumLength) {
        String value = input == null ? "" : input.replace('\r', ' ').replace('\n', ' ').trim();
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }

    public static final class Handler implements IMessageHandler<JutsuAdminActionMessage, IMessage> {
        @Override
        public IMessage onMessage(final JutsuAdminActionMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    JutsuConfigurationService.INSTANCE.handleGuiAction(player, message.action,
                            message.id, message.property, message.value);
                }
            });
            return null;
        }
    }
}
