package net.rebornaddon.gameplay.codex;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.rebornaddon.compat.NarutoLearnerDropProtectionHandler;
import net.rebornaddon.compat.ShinobiAddonRestrictionHandler;
import net.rebornaddon.policy.ContentPolicyService;
import net.rebornaddon.policy.PolicyAction;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class ShinobiCodexService {
    public static final ShinobiCodexService INSTANCE = new ShinobiCodexService();
    private static final String CODEX_KEY = "RebornShinobiCodex";
    private static final int MAX_ENTRIES = 512;

    private ShinobiCodexService() {
    }

    public void scanOwnedDiscoveries(EntityPlayerMP player) {
        if (player == null) return;
        for (ItemStack stack : player.inventory.mainInventory) discoverItem(player, stack);
        for (ItemStack stack : player.inventory.armorInventory) discoverItem(player, stack);
        for (ItemStack stack : player.inventory.offHandInventory) discoverItem(player, stack);
    }

    public void discoverTechnique(EntityPlayerMP player, String id, String name, String category) {
        discover(player, "jutsu:" + clean(id, 120), clean(name, 120),
                clean(category, 80), "Technique successfully used", "technique");
    }

    public void discoverBoss(EntityPlayerMP player, String id, String name) {
        discover(player, "boss:" + clean(id, 120), clean(name, 120), "Encounter",
                "Boss encountered in the world", "boss");
    }

    public String snapshot(EntityPlayerMP player) {
        scanOwnedDiscoveries(player);
        JsonObject root = new JsonObject();
        root.addProperty("available", true);
        root.addProperty("title", "Shinobi Codex");
        root.addProperty("message", "Only techniques, equipment, and encounters discovered by this character are shown.");
        JsonArray entries = new JsonArray();
        NBTTagList stored = codex(player);
        for (int i = stored.tagCount() - 1; i >= 0 && entries.size() < MAX_ENTRIES; i--) {
            NBTTagCompound tag = stored.getCompoundTagAt(i);
            JsonObject row = new JsonObject();
            row.addProperty("id", tag.getString("Id"));
            row.addProperty("title", tag.getString("Title"));
            row.addProperty("subtitle", tag.getString("Subtitle"));
            row.addProperty("detail", tag.getString("Detail"));
            row.addProperty("category", tag.getString("Category"));
            row.addProperty("discoveredAt", tag.getLong("DiscoveredAt"));
            entries.add(row);
        }
        if (entries.size() == 0) {
            JsonObject empty = new JsonObject();
            empty.addProperty("id", "codex-empty");
            empty.addProperty("title", "No discoveries recorded yet");
            empty.addProperty("subtitle", "Explore, train, and use techniques to fill the Codex.");
            empty.addProperty("detail", "Learners, scrolls, village equipment, used jutsu, and encountered bosses are recorded.");
            entries.add(empty);
        }
        JsonArray metrics = new JsonArray();
        metric(metrics, "Discoveries", Integer.toString(stored.tagCount()));
        metric(metrics, "Capacity", MAX_ENTRIES + " max");
        root.add("metrics", metrics);
        root.add("entries", entries);
        return root.toString();
    }

    private void discoverItem(EntityPlayerMP player, ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem().getRegistryName() == null
                || NarutoLearnerDropProtectionHandler.isProtectedLearner(stack)
                || ShinobiAddonRestrictionHandler.shouldHide(stack)
                || ShinobiAddonRestrictionHandler.shouldHideItem(stack.getItem())
                || ContentPolicyService.INSTANCE.denies(PolicyAction.CREATIVE, stack)) return;
        ResourceLocation key = stack.getItem().getRegistryName();
        String lower = key.toString().toLowerCase(Locale.ROOT);
        String category;
        if (lower.contains("learner") || lower.contains("scroll")) category = "Progression";
        else if (lower.contains("headband") || lower.contains("forehead") || lower.contains("robe")
                || lower.contains("armor") || stack.getItem().isValidArmor(stack,
                net.minecraft.inventory.EntityEquipmentSlot.CHEST, player)) category = "Equipment";
        else return;
        discover(player, "item:" + key + ':' + stack.getMetadata(), stack.getDisplayName(),
                category, "Equipment or progression item discovered", "item");
    }

    private void discover(EntityPlayerMP player, String id, String title, String subtitle,
                          String detail, String category) {
        if (player == null || id == null || id.isEmpty() || title == null || title.isEmpty()) return;
        NBTTagList list = codex(player);
        for (int i = 0; i < list.tagCount(); i++) {
            if (id.equals(list.getCompoundTagAt(i).getString("Id"))) return;
        }
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("Id", clean(id, 160));
        entry.setString("Title", clean(title, 160));
        entry.setString("Subtitle", clean(subtitle, 120));
        entry.setString("Detail", clean(detail, 240));
        entry.setString("Category", clean(category, 40));
        entry.setLong("DiscoveredAt", System.currentTimeMillis());
        list.appendTag(entry);
        while (list.tagCount() > MAX_ENTRIES) list.removeTag(0);
        persisted(player).setTag(CODEX_KEY, list);
    }

    private static NBTTagList codex(EntityPlayerMP player) {
        NBTTagCompound root = persisted(player);
        if (!root.hasKey(CODEX_KEY, 9)) root.setTag(CODEX_KEY, new NBTTagList());
        return root.getTagList(CODEX_KEY, 10);
    }

    private static NBTTagCompound persisted(EntityPlayerMP player) {
        NBTTagCompound root = player.getEntityData();
        if (!root.hasKey(EntityPlayerMP.PERSISTED_NBT_TAG, 10)) {
            root.setTag(EntityPlayerMP.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return root.getCompoundTag(EntityPlayerMP.PERSISTED_NBT_TAG);
    }

    private static void metric(JsonArray metrics, String label, String value) {
        JsonObject metric = new JsonObject();
        metric.addProperty("label", label);
        metric.addProperty("value", value);
        metrics.add(metric);
    }

    private static String clean(String value, int maximum) {
        String clean = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
        return clean.length() <= maximum ? clean : clean.substring(0, maximum);
    }
}
