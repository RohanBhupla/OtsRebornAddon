package net.rebornaddon.gui.tabs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.ItemStack;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.gameplay.client.ClientGameplayData;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.util.List;

public final class MarketplaceTab implements HubTab {
    private static final int VIEW_BASE = 2400;
    private static final int ROW_BASE = 2420;
    private static final int PREVIOUS = 2460;
    private static final int NEXT = 2461;
    private static final int PRIMARY = 2462;
    private static final int CREATE = 2463;
    private static final String[] VIEWS = {"listings", "buy-orders", "commissions", "mine"};
    private static final String[] LABELS = {"Listings", "Buy Orders", "Commissions", "My Posts"};
    private int view;
    private int selected = -1;
    private int page;
    private int revision = -1;
    private boolean refresh;
    private int left;
    private int top;
    private int width;
    private int height;
    private GuiTextField price;
    private GuiTextField amount;
    private GuiTextField description;

    @Override
    public String getTabName() { return text("gui.rebornaddon.tab.market", "Market"); }

    @Override
    public int getTabColorActive() { return Theme.COPPER; }

    @Override
    public int getTabColorHover() { return Theme.COPPER_BRIGHT; }

    @Override
    public void buildButtons(List<GuiButton> buttons, int left, int top, int width, int height) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        prepareFields();
        int viewWidth = Math.max(52, (width - 9) / 4);
        for (int i = 0; i < VIEWS.length; i++) {
            ThemedButton button = button(VIEW_BASE + i, left + i * (viewWidth + 3), top,
                    i == 3 ? width - i * (viewWidth + 3) : viewWidth, 21,
                    text("gui.rebornaddon.market." + VIEWS[i].replace('-', '_'), LABELS[i]),
                    view == i ? Theme.COPPER : Theme.TAB_INACTIVE_BG, Theme.COPPER_BRIGHT);
            button.setSelected(view == i);
            buttons.add(button);
        }
        JsonArray entries = entries();
        int rows = rowsPerPage();
        int start = page * rows;
        if (start >= entries.size() && page > 0) {
            page = Math.max(0, (entries.size() - 1) / rows);
            start = page * rows;
        }
        for (int i = 0; i < rows && start + i < entries.size(); i++) {
            JsonObject entry = object(entries.get(start + i));
            String label = string(entry, "title", "Listing") + "  "
                    + string(entry, "priceLabel", "");
            ThemedButton row = button(ROW_BASE + i, left + 10, top + 85 + i * 27,
                    width - 20, 23, label,
                    selected == start + i ? Theme.PURPLE_DARK : Theme.TAB_INACTIVE_BG,
                    Theme.COPPER_BRIGHT);
            row.setSelected(selected == start + i);
            row.setTextAlignment(ThemedButton.Alignment.LEFT);
            row.setTextInset(9);
            buttons.add(row);
        }
        int bottom = top + height - 23;
        buttons.add(button(PREVIOUS, left + 10, bottom, 30, 19, "<",
                Theme.TAB_INACTIVE_BG, Theme.COPPER));
        buttons.add(button(NEXT, left + 44, bottom, 30, 19, ">",
                Theme.TAB_INACTIVE_BG, Theme.COPPER));
        JsonObject chosen = selectedEntry();
        String action = string(chosen, "action", "");
        if (!action.isEmpty()) {
            buttons.add(button(PRIMARY, left + width - 160, bottom, 150, 19,
                    string(chosen, "actionLabel", "Select"), Theme.TEAL_DARK, Theme.TEAL));
        }
        if (view > 0) {
            String label = view == 1
                    ? text("gui.rebornaddon.market.create_buy_order", "Create from Held")
                    : view == 2
                    ? text("gui.rebornaddon.market.create_commission", "Post Commission")
                    : text("gui.rebornaddon.market.list_held", "List Held Item");
            buttons.add(button(CREATE, left + width - 160, top + 47, 150, 19, label,
                    Theme.COPPER, Theme.COPPER_BRIGHT));
        }
    }

    @Override
    public void drawContent(GuiScreen screen, int left, int top, int width, int height,
                            int mouseX, int mouseY) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        JsonObject data = ClientGameplayData.get("marketplace");
        GuiChrome.header(left, top + 27, width, 50, Theme.COPPER);
        font.drawString(text("gui.rebornaddon.market.title", "Shinobi Marketplace"),
                left + 12, top + 36, Theme.TEXT_LIGHT);
        String message = string(data, "message", "Loading marketplace...");
        font.drawString(trim(font, message, width - 24),
                left + 12, view > 0 ? top + 67 : top + 51,
                bool(data, "available", false) ? Theme.TEXT_MUTED : Theme.GOLD);
        if (view > 0) {
            font.drawString(text("gui.rebornaddon.market.price", "Price (Ryo)"),
                    left + width - 320, top + 31, Theme.TEXT_MUTED);
            font.drawString(text("gui.rebornaddon.market.amount", "Amount"),
                    left + width - 220, top + 31, Theme.TEXT_MUTED);
            price.drawTextBox();
            amount.drawTextBox();
            if (view == 2) {
                font.drawString(text("gui.rebornaddon.market.description", "Commission request"),
                        left + 12, top + 31, Theme.TEXT_MUTED);
                description.drawTextBox();
            } else {
                ItemStack held = Minecraft.getMinecraft().player == null ? ItemStack.EMPTY
                        : Minecraft.getMinecraft().player.getHeldItemMainhand();
                String heldName = held.isEmpty() ? "Hold the item this post refers to" : held.getDisplayName();
                font.drawString(trim(font, heldName, Math.max(40, width - 350)),
                        left + 12, top + 54, Theme.COPPER_BRIGHT);
            }
        }
        JsonObject chosen = selectedEntry();
        if (chosen.size() > 0) {
            font.drawString(trim(font, string(chosen, "detail", ""), width - 200),
                    left + 84, top + height - 20, Theme.TEXT_MUTED);
        }
    }

    @Override
    public boolean handleButtonClick(int id) {
        if (id >= VIEW_BASE && id < VIEW_BASE + VIEWS.length) {
            view = id - VIEW_BASE;
            selected = -1;
            page = 0;
            request(true);
            return true;
        }
        if (id == PREVIOUS || id == NEXT) {
            page = Math.max(0, page + (id == PREVIOUS ? -1 : 1));
            return true;
        }
        if (id >= ROW_BASE && id < ROW_BASE + rowsPerPage()) {
            selected = page * rowsPerPage() + id - ROW_BASE;
            return true;
        }
        if (id == PRIMARY) {
            JsonObject entry = selectedEntry();
            RebornAddonNetwork.sendGameplayAction("marketplace", string(entry, "action", ""),
                    "{\"id\":\"" + escape(string(entry, "id", "")) + "\"}");
            return false;
        }
        if (id == CREATE) {
            long priceValue = number(price, 0L);
            int count = (int) Math.min(64L, number(amount, 1L));
            String action = view == 1 ? "buy-order.create-held-reference"
                    : view == 2 ? "commission.create" : "listing.create-held";
            String extra = view == 2 ? ",\"description\":\"" + escape(description.getText()) + "\"" : "";
            RebornAddonNetwork.sendGameplayAction("marketplace", action,
                    "{\"price\":" + priceValue + ",\"amount\":" + count + extra + "}");
            return false;
        }
        return false;
    }

    @Override
    public void handleKeyTyped(char typedChar, int keyCode) {
        if (price != null) price.textboxKeyTyped(typedChar, keyCode);
        if (amount != null) amount.textboxKeyTyped(typedChar, keyCode);
        if (view == 2 && description != null) description.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (price != null) price.mouseClicked(mouseX, mouseY, mouseButton);
        if (amount != null) amount.mouseClicked(mouseX, mouseY, mouseButton);
        if (view == 2 && description != null) description.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void onTick() {
        if (price != null) price.updateCursorCounter();
        if (amount != null) amount.updateCursorCounter();
        if (description != null) description.updateCursorCounter();
        if (revision != ClientGameplayData.revision()) {
            revision = ClientGameplayData.revision();
            refresh = true;
        }
        request(false);
    }

    @Override
    public boolean consumeButtonRefresh() {
        boolean value = refresh;
        refresh = false;
        return value;
    }

    private void prepareFields() {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String oldPrice = price == null ? "0" : price.getText();
        String oldAmount = amount == null ? "1" : amount.getText();
        String oldDescription = description == null ? "" : description.getText();
        boolean priceFocus = price != null && price.isFocused();
        boolean amountFocus = amount != null && amount.isFocused();
        boolean descriptionFocus = description != null && description.isFocused();
        price = new GuiTextField(2470, font, left + width - 320, top + 43, 92, 18);
        amount = new GuiTextField(2471, font, left + width - 220, top + 43, 48, 18);
        description = new GuiTextField(2472, font, left + 12, top + 43,
                Math.max(40, width - 340), 18);
        price.setText(oldPrice);
        amount.setText(oldAmount);
        description.setText(oldDescription);
        description.setMaxStringLength(160);
        price.setFocused(priceFocus);
        amount.setFocused(amountFocus);
        description.setFocused(descriptionFocus);
    }

    private void request(boolean force) {
        if (force || ClientGameplayData.shouldRequest("marketplace")) {
            RebornAddonNetwork.sendGameplayAction("marketplace", "snapshot",
                    "{\"view\":\"" + VIEWS[view] + "\"}");
        }
    }

    private int rowsPerPage() { return Math.max(2, (height - 163) / 27); }

    private JsonArray entries() {
        return array(ClientGameplayData.get("marketplace"), VIEWS[view]);
    }

    private JsonObject selectedEntry() {
        JsonArray values = entries();
        return selected >= 0 && selected < values.size() ? object(values.get(selected)) : new JsonObject();
    }

    private static long number(GuiTextField field, long fallback) {
        try { return Math.max(0L, Long.parseLong(field == null ? "" : field.getText().trim())); }
        catch (NumberFormatException invalid) { return fallback; }
    }

    private static JsonArray array(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonArray()
                ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static JsonObject object(JsonElement value) {
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static String string(JsonObject object, String key, String fallback) {
        try { return object != null && object.has(key) ? object.get(key).getAsString() : fallback; }
        catch (Throwable ignored) { return fallback; }
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        try { return object != null && object.has(key) ? object.get(key).getAsBoolean() : fallback; }
        catch (Throwable ignored) { return fallback; }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static ThemedButton button(int id, int x, int y, int width, int height,
                                       String label, int base, int hover) {
        return new ThemedButton(id, x, y, width, height, label, base, hover, Theme.BUTTON_TEXT);
    }

    private static String trim(FontRenderer font, String value, int width) {
        return font.trimStringToWidth(value == null ? "" : value, Math.max(10, width));
    }

    private static String text(String key, String fallback) {
        return ClientLocalization.format(key, fallback);
    }
}
