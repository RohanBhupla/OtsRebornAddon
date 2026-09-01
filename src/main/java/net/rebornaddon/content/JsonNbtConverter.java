package net.rebornaddon.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagString;

import java.util.Map;

public final class JsonNbtConverter {
    private JsonNbtConverter() {
    }

    public static NBTTagCompound compound(JsonObject object) {
        NBTTagCompound result = new NBTTagCompound();
        if (object == null) {
            return result;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            NBTBase value = value(entry.getValue());
            if (value != null) {
                result.setTag(entry.getKey(), value);
            }
        }
        return result;
    }

    private static NBTBase value(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return new NBTTagString("");
        }
        if (element.isJsonObject()) {
            return compound(element.getAsJsonObject());
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            NBTTagList list = new NBTTagList();
            for (JsonElement item : array) {
                NBTBase child = value(item);
                if (child != null && (list.tagCount() == 0 || list.getTagType() == child.getId())) {
                    list.appendTag(child);
                }
            }
            return list;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return new NBTTagByte((byte) (primitive.getAsBoolean() ? 1 : 0));
        }
        if (primitive.isNumber()) {
            double decimal = primitive.getAsDouble();
            long whole = primitive.getAsLong();
            return decimal == whole ? new NBTTagLong(whole) : new NBTTagDouble(decimal);
        }
        return new NBTTagString(primitive.getAsString());
    }
}
