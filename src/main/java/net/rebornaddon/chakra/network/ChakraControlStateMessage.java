package net.rebornaddon.chakra.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.rebornaddon.RebornAddonMod;
import net.rebornaddon.chakra.ChakraControlConfigurationService;

public final class ChakraControlStateMessage implements IMessage {
    private boolean enabled;
    private boolean systemEnabled;
    private boolean waterWalkingEnabled;
    private boolean wallClimbingEnabled;
    private double wallClimbSpeedMultiplier = 1.0D;

    public ChakraControlStateMessage() {
    }

    public ChakraControlStateMessage(boolean enabled) {
        this.enabled = enabled;
        ChakraControlConfigurationService config = ChakraControlConfigurationService.INSTANCE;
        systemEnabled = config.enabled();
        waterWalkingEnabled = config.waterWalkingEnabled();
        wallClimbingEnabled = config.wallClimbingEnabled();
        wallClimbSpeedMultiplier = config.wallClimbSpeedMultiplier();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        enabled = buffer.readBoolean();
        systemEnabled = buffer.readBoolean();
        waterWalkingEnabled = buffer.readBoolean();
        wallClimbingEnabled = buffer.readBoolean();
        wallClimbSpeedMultiplier = buffer.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(enabled);
        buffer.writeBoolean(systemEnabled);
        buffer.writeBoolean(waterWalkingEnabled);
        buffer.writeBoolean(wallClimbingEnabled);
        buffer.writeDouble(wallClimbSpeedMultiplier);
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean systemEnabled() {
        return systemEnabled;
    }

    public boolean waterWalkingEnabled() {
        return waterWalkingEnabled;
    }

    public boolean wallClimbingEnabled() {
        return wallClimbingEnabled;
    }

    public double wallClimbSpeedMultiplier() {
        return wallClimbSpeedMultiplier;
    }

    public static final class Handler implements IMessageHandler<ChakraControlStateMessage, IMessage> {
        @Override
        public IMessage onMessage(ChakraControlStateMessage message, MessageContext context) {
            RebornAddonMod.proxy.handleChakraControlState(message);
            return null;
        }
    }
}
