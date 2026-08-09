package net.rebornaddon.quest;

import net.minecraft.nbt.NBTTagCompound;

public final class QuestTabDefinition {
    private final String id;
    private final String title;
    private final String categoryFilter;
    private final String village;

    public QuestTabDefinition(String id, String title, String categoryFilter, String village) {
        this.id = id;
        this.title = title;
        this.categoryFilter = categoryFilter;
        this.village = village == null ? "" : village;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getCategoryFilter() {
        return categoryFilter;
    }

    public String getVillage() {
        return village;
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Id", id);
        tag.setString("Title", title);
        tag.setString("Filter", categoryFilter);
        tag.setString("Village", village);
        return tag;
    }

    public static QuestTabDefinition read(NBTTagCompound tag) {
        return new QuestTabDefinition(tag.getString("Id"), tag.getString("Title"),
                tag.getString("Filter"), tag.getString("Village"));
    }
}
