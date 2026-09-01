package net.rebornaddon.diagnostic.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.diagnostic.CompatibilityDoctor;

public final class CompatibilityReportMessage implements IMessage {
    private NBTTagCompound report = new NBTTagCompound();

    public CompatibilityReportMessage() {
    }

    public CompatibilityReportMessage(NBTTagCompound report) {
        this.report = report == null ? new NBTTagCompound() : report;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        report = ByteBufUtils.readTag(buffer);
        if (report == null) report = new NBTTagCompound();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeTag(buffer, report);
    }

    public static final class Handler implements IMessageHandler<CompatibilityReportMessage, IMessage> {
        @Override
        public IMessage onMessage(final CompatibilityReportMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    CompatibilityDoctor.INSTANCE.acceptReport(player, message.report);
                }
            });
            return null;
        }
    }
}
