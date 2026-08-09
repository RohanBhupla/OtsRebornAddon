package net.rebornaddon.quest;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class QuestTabStore extends WorldSavedData {
    private static final String DATA_NAME = "rebornaddon_quest_tabs";
    public static final int MAX_TABS = 48;

    private final Map<String, QuestTabDefinition> tabs = new LinkedHashMap<String, QuestTabDefinition>();

    public QuestTabStore() {
        super(DATA_NAME);
    }

    public QuestTabStore(String name) {
        super(name);
    }

    public static QuestTabStore get(MinecraftServer server) {
        WorldServer world = server == null ? null : server.getWorld(0);
        if (world == null) {
            return null;
        }

        MapStorage storage = world.getMapStorage();
        QuestTabStore data = (QuestTabStore) storage.getOrLoadData(QuestTabStore.class, DATA_NAME);
        if (data == null) {
            data = new QuestTabStore();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public List<QuestTabDefinition> all() {
        return Collections.unmodifiableList(new ArrayList<QuestTabDefinition>(tabs.values()));
    }

    public boolean put(QuestTabDefinition definition) {
        String key = normalizeId(definition.getId());
        if (key.isEmpty() || tabs.containsKey(key) || tabs.size() >= MAX_TABS) {
            return false;
        }
        tabs.put(key, definition);
        markDirty();
        return true;
    }

    public boolean remove(String id) {
        if (tabs.remove(normalizeId(id)) == null) {
            return false;
        }
        markDirty();
        return true;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        tabs.clear();
        NBTTagList list = nbt.getTagList("Tabs", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            QuestTabDefinition definition = QuestTabDefinition.read(list.getCompoundTagAt(i));
            String key = normalizeId(definition.getId());
            if (!key.isEmpty() && tabs.size() < MAX_TABS) {
                tabs.put(key, definition);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList list = new NBTTagList();
        for (QuestTabDefinition definition : tabs.values()) {
            list.appendTag(definition.write());
        }
        compound.setTag("Tabs", list);
        return compound;
    }

    public static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
