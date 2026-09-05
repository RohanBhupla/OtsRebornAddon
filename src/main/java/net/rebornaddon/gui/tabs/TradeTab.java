package net.rebornaddon.gui.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;
import net.rebornaddon.gui.GuiHub;
import net.rebornaddon.trade.TradeService;
import net.rebornaddon.trade.client.ClientTradeData;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TradeTab implements HubTab {
    private static final int REQUEST = 1600;
    private static final int ACCEPT = 1601;
    private static final int CANCEL = 1602;
    private static final int READY = 1603;
    private static final int PREVIOUS = 1604;
    private static final int NEXT = 1605;
    private static final int PLAYER_BASE = 1650;

    private final List<PlayerOption> players = new ArrayList<PlayerOption>();
    private final List<OfferView> offer = new ArrayList<OfferView>();
    private final List<OfferView> partnerOffer = new ArrayList<OfferView>();
    private NBTTagCompound snapshot = new NBTTagCompound();
    private String state = "idle";
    private String partner = "";
    private String message = "";
    private boolean ready;
    private boolean partnerReady;
    private int revision = -1;
    private int selectedPlayer;
    private int page;
    private boolean refreshButtons;
    private int left;
    private int top;
    private int width;
    private int height;
    private int offerX;
    private int partnerOfferX;
    private int offerY;
    private int inventoryX;
    private int inventoryY;

    @Override
    public String getTabName() {
        return text("gui.rebornaddon.tab.trade", "Trade");
    }

    @Override
    public int getTabColorActive() {
        return Theme.TEAL;
    }

    @Override
    public int getTabColorHover() {
        return Theme.TEAL_LIGHT;
    }

    @Override
    public void buildButtons(List<GuiButton> buttons, int left, int top, int width, int height) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        sync();
        if ("idle".equals(state)) buildIdle(buttons);
        else if ("incoming".equals(state)) buildIncoming(buttons);
        else if ("outgoing".equals(state)) buildOutgoing(buttons);
        else buildActive(buttons);
    }

    private void buildIdle(List<GuiButton> buttons) {
        int listTop = top + 53;
        int rows = Math.max(3, (height - 112) / 25);
        int pages = Math.max(1, (players.size() + rows - 1) / rows);
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * rows;
        int end = Math.min(players.size(), start + rows);
        for (int i = start; i < end; i++) {
            PlayerOption option = players.get(i);
            ThemedButton button = button(PLAYER_BASE + i - start, left + 12,
                    listTop + (i - start) * 25, width - 24, 21, option.name,
                    Theme.TAB_INACTIVE_BG, Theme.TEAL);
            button.setTextAlignment(ThemedButton.Alignment.LEFT);
            button.setTextInset(10);
            button.setSelected(i == selectedPlayer);
            buttons.add(button);
        }
        int bottom = top + height - 25;
        ThemedButton previous = button(PREVIOUS, left + 12, bottom, 32, 21,
                "<", Theme.TAB_INACTIVE_BG, Theme.NEUTRAL);
        previous.enabled = page > 0;
        buttons.add(previous);
        ThemedButton next = button(NEXT, left + 48, bottom, 32, 21,
                ">", Theme.TAB_INACTIVE_BG, Theme.NEUTRAL);
        next.enabled = page + 1 < pages;
        buttons.add(next);
        ThemedButton request = button(REQUEST, left + width - 152, bottom, 140, 21,
                text("gui.rebornaddon.trade.request", "Send Trade Request"),
                Theme.TEAL_DARK, Theme.TEAL);
        request.enabled = !players.isEmpty();
        buttons.add(request);
    }

    private void buildIncoming(List<GuiButton> buttons) {
        int y = top + height - 42;
        int buttonWidth = Math.max(80, (width - 31) / 2);
        buttons.add(button(ACCEPT, left + 12, y, buttonWidth, 24,
                text("gui.rebornaddon.trade.accept", "Accept"), Theme.SUCCESS, Theme.SUCCESS_BRIGHT));
        buttons.add(button(CANCEL, left + 19 + buttonWidth, y,
                width - 31 - buttonWidth, 24,
                text("gui.rebornaddon.trade.decline", "Decline"),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED));
    }

    private void buildOutgoing(List<GuiButton> buttons) {
        int buttonWidth = Math.max(100, Math.min(180, width - 24));
        buttons.add(button(CANCEL, left + (width - buttonWidth) / 2, top + height - 42,
                buttonWidth, 24, text("gui.rebornaddon.trade.cancel", "Cancel Request"),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED));
    }

    private void buildActive(List<GuiButton> buttons) {
        updateSlotLayout();
        int y = top + height - 24;
        int readyWidth = Math.max(80, Math.min(150, (width - 31) / 2));
        buttons.add(button(READY, left + 12, y, readyWidth, 21,
                ready ? text("gui.rebornaddon.trade.not_ready", "Not Ready")
                        : text("gui.rebornaddon.trade.ready", "Ready"),
                ready ? Theme.GOLD_DARK : Theme.SUCCESS, ready ? Theme.GOLD : Theme.SUCCESS_BRIGHT));
        buttons.add(button(CANCEL, left + width - 12 - readyWidth, y, readyWidth, 21,
                text("gui.rebornaddon.trade.cancel_trade", "Cancel Trade"),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED));
    }

    @Override
    public void drawContent(GuiScreen screen, int left, int top, int width, int height,
                            int mouseX, int mouseY) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        GuiChrome.header(left, top, width, 40, Theme.TEAL);
        if ("idle".equals(state)) {
            font.drawString(text("gui.rebornaddon.trade.available", "Available Players"),
                    left + 13, top + 10, Theme.TEXT_LIGHT);
            drawMessage(font, top + 24);
            if (players.isEmpty()) {
                font.drawString(text("gui.rebornaddon.trade.none_online", "No players are available."),
                        left + 13, top + 61, Theme.TEXT_MUTED);
            }
        } else if ("incoming".equals(state) || "outgoing".equals(state)) {
            String heading = "incoming".equals(state)
                    ? text("gui.rebornaddon.trade.incoming", "Incoming Trade Request")
                    : text("gui.rebornaddon.trade.outgoing", "Trade Request Sent");
            font.drawString(heading, left + 13, top + 10, Theme.TEXT_LIGHT);
            font.drawString(partner, left + 13, top + 24, Theme.TEAL_LIGHT);
            GuiChrome.section(left, top + 49, width, Math.max(50, height - 100), Theme.TEAL);
            drawCentered(font, partner, top + Math.max(72, height / 2 - 10), Theme.TEXT_LIGHT);
            drawMessage(font, top + Math.max(88, height / 2 + 7));
        } else {
            drawActive(screen, font, mouseX, mouseY);
        }
    }

    private void drawActive(GuiScreen screen, FontRenderer font, int mouseX, int mouseY) {
        updateSlotLayout();
        font.drawString(text("gui.rebornaddon.trade.with", "Trading with") + " " + partner,
                left + 13, top + 9, Theme.TEXT_LIGHT);
        String status = ready ? text("gui.rebornaddon.trade.you_ready", "You: Ready")
                : text("gui.rebornaddon.trade.you_reviewing", "You: Reviewing");
        String other = partnerReady ? text("gui.rebornaddon.trade.partner_ready", "Partner: Ready")
                : text("gui.rebornaddon.trade.partner_reviewing", "Partner: Reviewing");
        font.drawString(status, left + 13, top + 24, ready ? Theme.SUCCESS_BRIGHT : Theme.TEXT_MUTED);
        font.drawString(other, left + width - 13 - font.getStringWidth(other), top + 24,
                partnerReady ? Theme.SUCCESS_BRIGHT : Theme.TEXT_MUTED);

        font.drawString(text("gui.rebornaddon.trade.your_offer", "Your Offer"),
                offerX, offerY - 13, Theme.TEAL_LIGHT);
        String partnerLabel = text("gui.rebornaddon.trade.their_offer", "Their Offer");
        font.drawString(partnerLabel, partnerOfferX, offerY - 13, Theme.GOLD);
        RenderHelper.enableGUIStandardItemLighting();
        drawOfferSlots(offerX, offerY, offer, mouseX, mouseY, true);
        drawOfferSlots(partnerOfferX, offerY, partnerOffer, mouseX, mouseY, false);

        font.drawString(text("gui.rebornaddon.trade.inventory", "Inventory"),
                inventoryX, inventoryY - 13, Theme.TEXT_LIGHT);
        drawInventory(mouseX, mouseY);
        RenderHelper.disableStandardItemLighting();
        drawMessage(font, top + 42);
        ItemStack hovered = hoveredStack(mouseX, mouseY);
        if (!hovered.isEmpty() && screen instanceof GuiHub) {
            ((GuiHub) screen).drawItemTooltip(hovered, mouseX, mouseY);
        }
    }

    private void drawOfferSlots(int x, int y, List<OfferView> offers,
                                int mouseX, int mouseY, boolean own) {
        for (int i = 0; i < 12; i++) {
            int slotX = x + i % 6 * 18;
            int slotY = y + i / 6 * 18;
            boolean hovered = inside(mouseX, mouseY, slotX, slotY, 18, 18);
            drawSlot(slotX, slotY, hovered, own ? Theme.TEAL : Theme.GOLD_DARK);
            if (i < offers.size()) renderStack(offers.get(i).stack, slotX + 1, slotY + 1);
        }
    }

    private void drawInventory(int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null) return;
        for (int display = 0; display < 36; display++) {
            int row = display / 9;
            int column = display % 9;
            int slot = row < 3 ? 9 + display : column;
            int x = inventoryX + column * 18;
            int y = inventoryY + row * 18;
            drawSlot(x, y, inside(mouseX, mouseY, x, y, 18, 18),
                    offeredSlot(slot) ? Theme.COPPER_BRIGHT : Theme.BUTTON_BORDER);
            renderStack(minecraft.player.inventory.mainInventory.get(slot), x + 1, y + 1);
        }
    }

    private static void drawSlot(int x, int y, boolean hovered, int accent) {
        Gui.drawRect(x, y, x + 18, y + 18, Theme.EDGE_DARK);
        Gui.drawRect(x + 1, y + 1, x + 17, y + 17, hovered ? Theme.TAB_HOVER_BG : Theme.PARCHMENT_DARK);
        Gui.drawRect(x + 1, y + 1, x + 17, y + 2, accent);
    }

    private static void renderStack(ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        minecraft.getRenderItem().renderItemOverlayIntoGUI(minecraft.fontRenderer, stack, x, y, null);
    }

    private void drawMessage(FontRenderer font, int y) {
        if (!message.isEmpty()) {
            int color = message.toLowerCase().contains("completed") ? Theme.SUCCESS_BRIGHT : Theme.TEXT_MUTED;
            font.drawString(trim(font, message, width - 26), left + 13, y, color);
        }
    }

    private void drawCentered(FontRenderer font, String value, int y, int color) {
        font.drawString(value, left + (width - font.getStringWidth(value)) / 2, y, color);
    }

    @Override
    public boolean handleButtonClick(int buttonId) {
        if (buttonId == REQUEST && !players.isEmpty()) {
            selectedPlayer = Math.max(0, Math.min(selectedPlayer, players.size() - 1));
            RebornAddonNetwork.sendTradeAction(TradeService.REQUEST, players.get(selectedPlayer).id, -1, 0);
        } else if (buttonId == ACCEPT) {
            RebornAddonNetwork.sendTradeAction(TradeService.ACCEPT, null, -1, 0);
        } else if (buttonId == CANCEL) {
            RebornAddonNetwork.sendTradeAction(TradeService.CANCEL, null, -1, 0);
        } else if (buttonId == READY) {
            RebornAddonNetwork.sendTradeAction(TradeService.READY, null, -1, 0);
        } else if (buttonId == PREVIOUS || buttonId == NEXT) {
            page += buttonId == PREVIOUS ? -1 : 1;
        } else {
            int rows = Math.max(3, (height - 112) / 25);
            if (buttonId < PLAYER_BASE || buttonId >= PLAYER_BASE + rows) return false;
            int index = page * rows + buttonId - PLAYER_BASE;
            if (index >= 0 && index < players.size()) selectedPlayer = index;
        }
        return true;
    }

    @Override
    public void handleMouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!"active".equals(state)) return;
        updateSlotLayout();
        for (int i = 0; i < offer.size(); i++) {
            int x = offerX + i % 6 * 18;
            int y = offerY + i / 6 * 18;
            if (inside(mouseX, mouseY, x, y, 18, 18)) {
                RebornAddonNetwork.sendTradeAction(TradeService.REMOVE, null, offer.get(i).slot, 0);
                return;
            }
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null) return;
        for (int display = 0; display < 36; display++) {
            int row = display / 9;
            int column = display % 9;
            int slot = row < 3 ? 9 + display : column;
            int x = inventoryX + column * 18;
            int y = inventoryY + row * 18;
            if (!inside(mouseX, mouseY, x, y, 18, 18)) continue;
            ItemStack stack = minecraft.player.inventory.mainInventory.get(slot);
            if (!stack.isEmpty()) {
                int amount = mouseButton == 1 ? 1 : stack.getCount();
                RebornAddonNetwork.sendTradeAction(TradeService.OFFER, null, slot, amount);
            }
            return;
        }
    }

    @Override
    public void onTick() {
        if (revision != ClientTradeData.revision()) {
            sync();
            refreshButtons = true;
        }
        if ("idle".equals(state) && ClientTradeData.shouldRequest()) {
            RebornAddonNetwork.requestTradeSync();
        }
    }

    @Override
    public boolean consumeButtonRefresh() {
        boolean value = refreshButtons;
        refreshButtons = false;
        return value;
    }

    private void sync() {
        int currentRevision = ClientTradeData.revision();
        if (revision == currentRevision) return;
        UUID selectedId = players.isEmpty() || selectedPlayer >= players.size()
                ? null : players.get(selectedPlayer).id;
        snapshot = ClientTradeData.snapshot();
        state = snapshot.getString("State");
        if (state.isEmpty()) state = "idle";
        partner = snapshot.getString("Partner");
        message = snapshot.getString("Message");
        ready = snapshot.getBoolean("Ready");
        partnerReady = snapshot.getBoolean("PartnerReady");
        players.clear();
        NBTTagList playerTags = snapshot.getTagList("Players", 10);
        for (int i = 0; i < playerTags.tagCount(); i++) {
            PlayerOption option = PlayerOption.parse(playerTags.getCompoundTagAt(i));
            if (option != null) players.add(option);
        }
        selectedPlayer = 0;
        if (selectedId != null) {
            for (int i = 0; i < players.size(); i++) {
                if (selectedId.equals(players.get(i).id)) selectedPlayer = i;
            }
        }
        offer.clear();
        parseOffers(snapshot.getTagList("Offer", 10), offer);
        partnerOffer.clear();
        parseOffers(snapshot.getTagList("PartnerOffer", 10), partnerOffer);
        revision = currentRevision;
    }

    private static void parseOffers(NBTTagList tags, List<OfferView> target) {
        for (int i = 0; i < tags.tagCount(); i++) {
            NBTTagCompound tag = tags.getCompoundTagAt(i);
            ItemStack stack = new ItemStack(tag.getCompoundTag("Stack"));
            if (!stack.isEmpty()) target.add(new OfferView(tag.getInteger("Slot"), stack));
        }
    }

    private void updateSlotLayout() {
        offerX = left + 12;
        partnerOfferX = left + width - 120;
        offerY = top + 65;
        inventoryX = left + (width - 162) / 2;
        inventoryY = top + height - 105;
    }

    private boolean offeredSlot(int slot) {
        for (OfferView item : offer) if (item.slot == slot) return true;
        return false;
    }

    private ItemStack hoveredStack(int mouseX, int mouseY) {
        for (int i = 0; i < offer.size(); i++) {
            int x = offerX + i % 6 * 18;
            int y = offerY + i / 6 * 18;
            if (inside(mouseX, mouseY, x, y, 18, 18)) return offer.get(i).stack;
        }
        for (int i = 0; i < partnerOffer.size(); i++) {
            int x = partnerOfferX + i % 6 * 18;
            int y = offerY + i / 6 * 18;
            if (inside(mouseX, mouseY, x, y, 18, 18)) return partnerOffer.get(i).stack;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null) return ItemStack.EMPTY;
        for (int display = 0; display < 36; display++) {
            int row = display / 9;
            int column = display % 9;
            int slot = row < 3 ? 9 + display : column;
            int x = inventoryX + column * 18;
            int y = inventoryY + row * 18;
            if (inside(mouseX, mouseY, x, y, 18, 18)) {
                return minecraft.player.inventory.mainInventory.get(slot);
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    private static ThemedButton button(int id, int x, int y, int width, int height,
                                       String label, int background, int hover) {
        return new ThemedButton(id, x, y, Math.max(24, width), height, label,
                background, hover, Theme.BUTTON_TEXT);
    }

    private static String trim(FontRenderer font, String value, int maximumWidth) {
        if (font.getStringWidth(value) <= maximumWidth) return value;
        return font.trimStringToWidth(value, Math.max(8, maximumWidth - font.getStringWidth("..."))) + "...";
    }

    private static String text(String key, String fallback) {
        return ClientLocalization.format(key, fallback);
    }

    private static final class PlayerOption {
        private final UUID id;
        private final String name;

        private PlayerOption(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        private static PlayerOption parse(NBTTagCompound tag) {
            try {
                String name = tag.getString("Name");
                return name.isEmpty() ? null : new PlayerOption(UUID.fromString(tag.getString("Id")), name);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    private static final class OfferView {
        private final int slot;
        private final ItemStack stack;

        private OfferView(int slot, ItemStack stack) {
            this.slot = slot;
            this.stack = stack;
        }
    }
}
