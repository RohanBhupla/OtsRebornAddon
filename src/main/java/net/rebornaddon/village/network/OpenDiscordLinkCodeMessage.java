package net.rebornaddon.village.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public final class OpenDiscordLinkCodeMessage implements IMessage {
    private String code = "";
    private String command = "/link";
    private String instruction = "";

    public OpenDiscordLinkCodeMessage() {
    }

    public OpenDiscordLinkCodeMessage(String code, String command, String instruction) {
        this.code = clean(code, 16);
        this.command = clean(command, 40);
        this.instruction = clean(instruction, 240);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        code = clean(ByteBufUtils.readUTF8String(buffer), 16);
        command = clean(ByteBufUtils.readUTF8String(buffer), 40);
        instruction = clean(ByteBufUtils.readUTF8String(buffer), 240);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeUTF8String(buffer, code);
        ByteBufUtils.writeUTF8String(buffer, command);
        ByteBufUtils.writeUTF8String(buffer, instruction);
    }

    public String getCode() {
        return code;
    }

    public String getCommand() {
        return command.isEmpty() ? "/link" : command;
    }

    public String getInstruction() {
        return instruction;
    }

    private static String clean(String value, int maximumLength) {
        String clean = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
        return clean.length() <= maximumLength ? clean : clean.substring(0, maximumLength).trim();
    }
}
