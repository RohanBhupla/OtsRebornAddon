package net.rebornaddon.village.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public final class OpenDiscordLinkPromptMessage implements IMessage {
    private boolean linked;

    public OpenDiscordLinkPromptMessage() {
    }

    public OpenDiscordLinkPromptMessage(boolean linked) {
        this.linked = linked;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        linked = buffer.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(linked);
    }

    public boolean linked() { return linked; }
}
