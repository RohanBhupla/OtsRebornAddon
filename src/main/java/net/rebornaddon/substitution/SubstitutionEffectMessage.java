package net.rebornaddon.substitution;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.RebornAddonMod;

public class SubstitutionEffectMessage implements IMessage {
    public static final int ACTIVATION = 0;
    public static final int ARRIVAL = 1;
    public static final int PULSE = 2;
    public static final int HIT = 3;
    public static final int FADE = 4;

    private int villageId;
    private int phase;
    private double x;
    private double y;
    private double z;

    public SubstitutionEffectMessage() {
    }

    public SubstitutionEffectMessage(int villageId, int phase, double x, double y, double z) {
        this.villageId = villageId;
        this.phase = phase;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        villageId = buf.readUnsignedByte();
        phase = buf.readUnsignedByte();
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(villageId);
        buf.writeByte(phase);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    public int getVillageId() {
        return villageId;
    }

    public int getPhase() {
        return phase;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public static class Handler implements IMessageHandler<SubstitutionEffectMessage, IMessage> {
        @Override
        public IMessage onMessage(final SubstitutionEffectMessage message, MessageContext context) {
            RebornAddonMod.proxy.handleSubstitutionEffect(message);
            return null;
        }
    }
}
