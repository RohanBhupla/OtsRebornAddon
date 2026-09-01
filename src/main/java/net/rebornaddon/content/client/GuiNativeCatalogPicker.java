package net.rebornaddon.content.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.EntityLiving;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.client.RebornScaledGuiScreen;
import net.rebornaddon.compat.ShinobiAddonRestrictionHandler;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

final class GuiNativeCatalogPicker extends RebornScaledGuiScreen {
    private static final int ROW_BASE = 5000;
    private static final int PREVIOUS = 5090;
    private static final int NEXT = 5091;
    private static final int CANCEL = 5092;

    private final GuiNativeRecordEditor parent;
    private final String kind;
    private final List<JsonObject> allValues = new ArrayList<JsonObject>();
    private final List<JsonObject> values = new ArrayList<JsonObject>();
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int page;
    private int rowsPerPage;
    private GuiTextField search;

    GuiNativeCatalogPicker(GuiNativeRecordEditor parent, String kind) {
        this.parent = parent;
        this.kind = kind;
        JsonObject wrapper = ClientNativeContentData.admin();
        JsonArray array = new JsonArray();
        if ("jutsu".equals(kind) && wrapper.has("jutsuCatalog")) {
            array = wrapper.getAsJsonArray("jutsuCatalog");
        } else if (wrapper.has("data") && wrapper.get("data").isJsonObject()) {
            JsonObject data = wrapper.getAsJsonObject("data");
            String collection = collection(kind);
            if (!collection.isEmpty() && data.has(collection) && data.get(collection).isJsonArray()) {
                array = data.getAsJsonArray(collection);
            }
        }
        for (JsonElement element : array) if (element.isJsonObject()) allValues.add(element.getAsJsonObject());
        if ("skin".equals(kind)) {
            allValues.addAll(ModSkinCatalog.values());
            deduplicate(allValues);
        }
        if (kind.startsWith("item")) {
            for (Item item : ForgeRegistries.ITEMS.getValuesCollection()) {
                if (item.getRegistryName() == null) continue;
                NonNullList<ItemStack> variants = NonNullList.create();
                try { item.getSubItems(CreativeTabs.SEARCH, variants); }
                catch (Throwable ignored) {
                }
                if (variants.isEmpty()) variants.add(new ItemStack(item));
                for (ItemStack stack : variants) {
                    if (stack.isEmpty() || !includeItem(stack, kind)) continue;
                    JsonObject entry = new JsonObject();
                    entry.addProperty("id", item.getRegistryName().toString());
                    entry.addProperty("meta", stack.getMetadata());
                    String name;
                    try { name = stack.getDisplayName(); }
                    catch (Throwable ignored) { name = item.getRegistryName().toString(); }
                    entry.addProperty("name", name);
                    allValues.add(entry);
                    if (allValues.size() >= 4096) break;
                }
                if (allValues.size() >= 4096) break;
            }
            deduplicate(allValues);
        }
        addOptions(kind, allValues);
        values.addAll(allValues);
    }

    @Override
    public void initGui() {
        panelWidth = Math.max(280, Math.min(520, width - 24));
        panelHeight = Math.max(220, Math.min(380, height - 24));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        rowsPerPage = Math.max(4, (panelHeight - 112) / 23);
        search = new GuiTextField(5100, fontRenderer, left + 14, top + 45, panelWidth - 28, 19);
        search.setMaxStringLength(80);
        rebuild();
    }

    private void rebuild() {
        buttonList.clear();
        int pages = Math.max(1, (values.size() + rowsPerPage - 1) / rowsPerPage);
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * rowsPerPage;
        int end = Math.min(values.size(), start + rowsPerPage);
        for (int i = start; i < end; i++) {
            JsonObject entry = values.get(i);
            String name = value(entry, "name");
            String id = value(entry, "id");
            String label = name.isEmpty() ? id : name;
            if (!("skin".equals(kind) || "jutsu".equals(kind)) && name.isEmpty()) label = id;
            ThemedButton row = new ThemedButton(ROW_BASE + i - start, left + 14,
                    top + 70 + (i - start) * 23, panelWidth - 28, 20,
                    label,
                    Theme.TAB_INACTIVE_BG, Theme.GOLD, Theme.BUTTON_TEXT);
            row.setTextAlignment(ThemedButton.Alignment.LEFT);
            row.setTextInset(8);
            buttonList.add(row);
        }
        int y = top + panelHeight - 28;
        ThemedButton previous = new ThemedButton(PREVIOUS, left + 14, y, 28, 19, "<",
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL, Theme.BUTTON_TEXT);
        previous.enabled = page > 0;
        buttonList.add(previous);
        ThemedButton next = new ThemedButton(NEXT, left + 46, y, 28, 19, ">",
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL, Theme.BUTTON_TEXT);
        next.enabled = page + 1 < pages;
        buttonList.add(next);
        buttonList.add(new ThemedButton(CANCEL, left + panelWidth - 104, y, 90, 19,
                ClientLocalization.format("gui.rebornaddon.world.cancel", "Cancel"),
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL, Theme.BUTTON_TEXT));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == PREVIOUS || button.id == NEXT) {
            page += button.id == PREVIOUS ? -1 : 1;
            rebuild();
        } else if (button.id == CANCEL) {
            mc.displayGuiScreen(parent);
        } else if (button.id >= ROW_BASE && button.id < ROW_BASE + rowsPerPage) {
            int index = page * rowsPerPage + button.id - ROW_BASE;
            if (index < values.size()) {
            JsonObject selected = values.get(index);
                parent.applyCatalogValue(kind, value(selected, "id"), value(selected, "name"),
                        value(selected, "skin"),
                        selected.has("slim") && selected.get("slim").getAsBoolean(),
                        selected.has("meta") ? selected.get("meta").getAsInt() : 0);
                mc.displayGuiScreen(parent);
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (search != null && search.textboxKeyTyped(typedChar, keyCode)) {
            filter();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (search != null) search.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (search != null) search.updateCursorCounter();
    }

    @Override
    protected void drawScaledScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        GuiChrome.frame(left, top, panelWidth, panelHeight, Theme.GOLD);
        GuiChrome.header(left + 3, top + 5, panelWidth - 6, 35, Theme.GOLD);
        String title = "skin".equals(kind)
                ? ClientLocalization.format("gui.rebornaddon.world.skin_library", "Skin Library")
                : "jutsu".equals(kind)
                ? ClientLocalization.format("gui.rebornaddon.world.jutsu_library", "Jutsu Library")
                : ClientLocalization.format("gui.rebornaddon.world.choose_value", "Choose %s", title(kind));
        fontRenderer.drawString(title, left + 15, top + 17, Theme.TEXT_LIGHT);
        if (search != null) search.drawTextBox();
        if (values.isEmpty()) fontRenderer.drawString(
                ClientLocalization.format("gui.rebornaddon.world.library_empty", "No entries are available."),
                left + 16, top + 77, Theme.TEXT_MUTED);
        drawScaledControls(mouseX, mouseY, partialTicks);
    }

    private static String value(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private void filter() {
        boolean focused = search != null && search.isFocused();
        int cursor = search == null ? 0 : search.getCursorPosition();
        String query = search == null ? "" : search.getText().trim().toLowerCase(java.util.Locale.ROOT);
        values.clear();
        for (JsonObject entry : allValues) {
            String id = value(entry, "id").toLowerCase(java.util.Locale.ROOT);
            String name = value(entry, "name").toLowerCase(java.util.Locale.ROOT);
            if (query.isEmpty() || id.contains(query) || name.contains(query)) values.add(entry);
        }
        page = 0;
        rebuild();
        if (search != null) {
            search.setFocused(focused);
            search.setCursorPosition(Math.min(cursor, search.getText().length()));
        }
    }

    private static boolean includeItem(ItemStack stack, String kind) {
        if (ShinobiAddonRestrictionHandler.shouldHide(stack)) return false;
        if (!kind.startsWith("item:")) return true;
        EntityEquipmentSlot slot = equipmentSlot(kind.substring("item:".length()));
        if (slot == null) return true;
        try {
            if (slot == EntityEquipmentSlot.MAINHAND) return isHandEquipment(stack, false);
            if (slot == EntityEquipmentSlot.OFFHAND) return isHandEquipment(stack, true);
            return EntityLiving.getSlotForItemStack(stack) == slot
                    || stack.getItem().isValidArmor(stack, slot, MinecraftHolder.player());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isHandEquipment(ItemStack stack, boolean offHand) {
        Item item = stack.getItem();
        if (offHand && item instanceof ItemShield) return true;
        if (item instanceof ItemSword || item instanceof ItemBow || item instanceof ItemTool) return true;
        if (item instanceof ItemArmor) return false;
        String id = item.getRegistryName() == null ? "" : item.getRegistryName().toString();
        String name;
        try { name = stack.getDisplayName(); }
        catch (Throwable ignored) { name = ""; }
        String descriptor = (id + ' ' + name + ' ' + item.getClass().getName())
                .toLowerCase(java.util.Locale.ROOT);
        if (offHand) {
            return containsAny(descriptor, "shield", "buckler", "fan", "gunbai");
        }
        return containsAny(descriptor, "sword", "blade", "kunai", "shuriken", "senbon",
                "scythe", "spear", "staff", "bow", "axe", "hammer", "club", "weapon");
    }

    private static boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    private static EntityEquipmentSlot equipmentSlot(String key) {
        if ("head".equals(key)) return EntityEquipmentSlot.HEAD;
        if ("chest".equals(key)) return EntityEquipmentSlot.CHEST;
        if ("legs".equals(key)) return EntityEquipmentSlot.LEGS;
        if ("feet".equals(key)) return EntityEquipmentSlot.FEET;
        if ("offHand".equals(key)) return EntityEquipmentSlot.OFFHAND;
        if ("mainHand".equals(key)) return EntityEquipmentSlot.MAINHAND;
        return null;
    }

    private static final class MinecraftHolder {
        private static net.minecraft.entity.EntityLivingBase player() {
            return net.minecraft.client.Minecraft.getMinecraft().player;
        }
    }

    private static String collection(String kind) {
        if ("skin".equals(kind)) return "skinLibrary";
        if ("template".equals(kind)) return "templates";
        if ("path".equals(kind)) return "paths";
        if ("boss".equals(kind)) return "bosses";
        if ("quest".equals(kind)) return "quests";
        return "";
    }

    private static void addOptions(String kind, List<JsonObject> values) {
        if ("objective".equals(kind)) options(values, "travel", "talk", "fight", "escort", "item", "kill");
        else if ("aggression".equals(kind)) options(values, "passive", "defensive", "hostile");
        else if ("role".equals(kind)) options(values, "none", "mailman", "store", "quests", "transport", "boss");
        else if ("village".equals(kind)) options(values, "", "leaf", "stone", "cloud", "sand", "mist", "rain", "akatsuki");
        else if ("rank".equals(kind)) options(values, "D", "C", "B", "A", "S");
        else if ("speaker".equals(kind)) options(values, "npc", "player");
        else if ("dungeonMode".equals(kind)) options(values, "waves", "fixed");
        else if ("rewardDelivery".equals(kind)) options(values, "direct", "mail");
    }

    private static void deduplicate(List<JsonObject> values) {
        Map<String, JsonObject> unique = new LinkedHashMap<String, JsonObject>();
        for (JsonObject value : values) {
            String key = value(value, "id") + ':' + (value.has("meta") ? value.get("meta").getAsInt() : 0);
            if (!unique.containsKey(key)) unique.put(key, value);
        }
        values.clear();
        values.addAll(unique.values());
    }

    private static void options(List<JsonObject> values, String... ids) {
        for (String id : ids) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", id);
            entry.addProperty("name", id.isEmpty() ? "Any" : title(id));
            values.add(entry);
        }
    }

    private static String title(String value) {
        if (value == null || value.isEmpty()) return "Any";
        if (value.startsWith("item:")) return title(value.substring("item:".length())) + " Equipment";
        if ("rewardDelivery".equals(value)) return "Reward Delivery";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1).replace('_', ' ');
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
