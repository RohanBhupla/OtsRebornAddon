package net.rebornaddon.store.client;

import net.minecraft.item.Item;
import net.minecraft.init.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.store.network.StorePurchaseResultMessage;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public final class ClientStoreData {
    private static final long MESSAGE_DURATION_MILLIS = 5000L;
    private static final long BALANCE_SYNC_MILLIS = 750L;

    private static String message = "";
    private static boolean success;
    private static long balance;
    private static long expiresAt;
    private static long balanceExpiresAt;
    private static List<Entry> entries = Collections.emptyList();
    private static int revision;
    private static boolean loaded;
    private static boolean buildingLocked = true;
    private static boolean serviceAvailable;
    private static boolean preview;
    private static String village = "";
    private static boolean kage;
    private static int anbuMasks;
    private static int anbuCloaks;
    private static int kageHat;
    private static int kageRobe;
    private static int akatsukiHeadband;
    private static int akatsukiHat;
    private static int akatsukiRobe;
    private static Map<String, Boolean> scopes = Collections.emptyMap();

    private ClientStoreData() {
    }

    public static void update(StorePurchaseResultMessage result) {
        success = result.success();
        balance = result.balance();
        expiresAt = System.currentTimeMillis() + MESSAGE_DURATION_MILLIS;
        balanceExpiresAt = System.currentTimeMillis() + BALANCE_SYNC_MILLIS;
        String itemName = itemName(result.itemRegistryName());
        String price = formatRyo(result.price());
        if ("message.rebornaddon.store.purchased".equals(result.messageKey())) {
            message = ClientLocalization.format("message.rebornaddon.store.purchased",
                    "Purchased %s for %s ryo.", itemName, price);
        } else if ("message.rebornaddon.store.not_enough".equals(result.messageKey())) {
            long missing = Math.max(0L, (long) result.price() - result.balance());
            message = ClientLocalization.format("message.rebornaddon.store.not_enough",
                    "You need %s more ryo.", formatRyo(missing));
        } else if ("message.rebornaddon.store.wait".equals(result.messageKey())) {
            message = ClientLocalization.format("message.rebornaddon.store.wait",
                    "Please wait before purchasing again.");
        } else if ("message.rebornaddon.store.unavailable".equals(result.messageKey())) {
            message = ClientLocalization.format("message.rebornaddon.store.unavailable",
                    "That item is no longer available.");
        } else if ("message.rebornaddon.store.coming_soon".equals(result.messageKey())) {
            message = ClientLocalization.format("message.rebornaddon.store.coming_soon",
                    "Building supplies are coming soon.");
        } else if ("message.rebornaddon.store.service_unavailable".equals(result.messageKey())) {
            message = ClientLocalization.format("message.rebornaddon.store.service_unavailable",
                    "The server store is not configured yet.");
        } else if ("message.rebornaddon.store.limit".equals(result.messageKey())) {
            message = ClientLocalization.format("message.rebornaddon.store.limit",
                    "The purchase limit for that equipment has been reached.");
        } else {
            message = ClientLocalization.format("message.rebornaddon.store.error",
                    "The purchase could not be completed.");
        }
    }

    public static void updateCatalog(NBTTagCompound data) {
        List<Entry> updated = new ArrayList<Entry>();
        NBTTagList records = data.getTagList("Entries", 10);
        for (int i = 0; i < records.tagCount(); i++) {
            NBTTagCompound record = records.getCompoundTagAt(i);
            try {
                ResourceLocation name = new ResourceLocation(record.getString("Id"));
                Item item = ForgeRegistries.ITEMS.getValue(name);
                if (item == null) continue;
                ItemStack stack = new ItemStack(item, 1, Math.max(0, record.getInteger("Meta")));
                if (stack.isEmpty()) continue;
                int tier = Math.max(0, Math.min(4, record.getInteger("Tier")));
                if (tier > 0) {
                    stack.addEnchantment(Enchantments.PROTECTION, tier);
                    stack.addEnchantment(Enchantments.UNBREAKING, tier);
                }
                updated.add(new Entry(record.getString("Key"), record.getString("Category"),
                        record.getString("Subcategory"), stack, stack.getDisplayName(),
                        Math.max(0, record.getInteger("Price")), record.getBoolean("Purchasable"),
                        tier, record.getString("Quota")));
            } catch (RuntimeException ignored) {
            }
        }
        entries = Collections.unmodifiableList(updated);
        Map<String, Boolean> updatedScopes = new HashMap<String, Boolean>();
        NBTTagList scopeTags = data.getTagList("Scopes", 10);
        for (int i = 0; i < scopeTags.tagCount(); i++) {
            NBTTagCompound tag = scopeTags.getCompoundTagAt(i);
            if (!tag.getString("Key").isEmpty()) {
                updatedScopes.put(tag.getString("Key"), Boolean.valueOf(tag.getBoolean("Enabled")));
            }
        }
        scopes = Collections.unmodifiableMap(updatedScopes);
        revision++;
        loaded = true;
        buildingLocked = data.getBoolean("BuildingLocked");
        serviceAvailable = data.getBoolean("ServiceAvailable");
        preview = data.getBoolean("Preview");
        village = data.getString("Village");
        kage = data.getBoolean("Kage");
        anbuMasks = Math.max(0, data.getInteger("AnbuMasks"));
        anbuCloaks = Math.max(0, data.getInteger("AnbuCloaks"));
        kageHat = Math.max(0, data.getInteger("KageHat"));
        kageRobe = Math.max(0, data.getInteger("KageRobe"));
        akatsukiHeadband = Math.max(0, data.getInteger("AkatsukiHeadband"));
        akatsukiHat = Math.max(0, data.getInteger("AkatsukiHat"));
        akatsukiRobe = Math.max(0, data.getInteger("AkatsukiRobe"));
    }

    public static List<Entry> entries() { return entries; }
    public static int revision() { return revision; }
    public static boolean loaded() { return loaded; }
    public static boolean buildingLocked() { return buildingLocked; }
    public static boolean serviceAvailable() { return serviceAvailable; }
    public static boolean preview() { return preview; }
    public static String village() { return village; }
    public static boolean kage() { return kage; }
    public static boolean scopeEnabled(String key) {
        Boolean enabled = scopes.get(key);
        return enabled == null || enabled.booleanValue();
    }
    public static int quota(String kind) {
        if (net.rebornaddon.store.StoreCatalog.QUOTA_ANBU_MASK.equals(kind)) return anbuMasks;
        if (net.rebornaddon.store.StoreCatalog.QUOTA_ANBU_CLOAK.equals(kind)) return anbuCloaks;
        if (net.rebornaddon.store.StoreCatalog.QUOTA_KAGE_HAT.equals(kind)) return kageHat;
        if (net.rebornaddon.store.StoreCatalog.QUOTA_KAGE_ROBE.equals(kind)) return kageRobe;
        if (net.rebornaddon.store.StoreCatalog.QUOTA_AKATSUKI_HEADBAND.equals(kind)) return akatsukiHeadband;
        if (net.rebornaddon.store.StoreCatalog.QUOTA_AKATSUKI_HAT.equals(kind)) return akatsukiHat;
        if (net.rebornaddon.store.StoreCatalog.QUOTA_AKATSUKI_ROBE.equals(kind)) return akatsukiRobe;
        return 0;
    }

    public static String message() {
        return System.currentTimeMillis() < expiresAt ? message : "";
    }

    public static boolean success() {
        return success;
    }

    public static long balance(long localBalance) {
        return System.currentTimeMillis() < balanceExpiresAt ? balance : localBalance;
    }

    public static String formatRyo(long value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(Math.max(0L, value));
    }

    public static void reset() {
        message = "";
        success = false;
        balance = 0L;
        expiresAt = 0L;
        balanceExpiresAt = 0L;
        entries = Collections.emptyList();
        loaded = false;
        buildingLocked = true;
        serviceAvailable = false;
        preview = false;
        village = "";
        kage = false;
        anbuMasks = 0;
        anbuCloaks = 0;
        kageHat = 0;
        kageRobe = 0;
        akatsukiHeadband = 0;
        akatsukiHat = 0;
        akatsukiRobe = 0;
        scopes = Collections.emptyMap();
        revision++;
    }

    private static String itemName(String registryName) {
        try {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(registryName));
            if (item != null) {
                return new ItemStack(item).getDisplayName();
            }
        } catch (RuntimeException ignored) {
        }
        return ClientLocalization.format("gui.rebornaddon.store.item", "Item");
    }

    public static final class Entry {
        public final String key;
        public final String category;
        public final String subcategory;
        public final ItemStack stack;
        public final String name;
        public final int price;
        public final boolean purchasable;
        public final int tier;
        public final String quotaKind;

        private Entry(String key, String category, String subcategory, ItemStack stack,
                      String name, int price, boolean purchasable, int tier, String quotaKind) {
            this.key = key;
            this.category = category;
            this.subcategory = subcategory;
            this.stack = stack;
            this.name = name;
            this.price = price;
            this.purchasable = purchasable;
            this.tier = tier;
            this.quotaKind = quotaKind;
        }
    }
}
