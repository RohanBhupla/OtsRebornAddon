package net.rebornaddon.gui.tabs;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.ItemStack;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;
import net.rebornaddon.quest.client.ClientQuestData;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.util.List;
import java.util.ArrayList;

public final class MailTab implements HubTab {
    private static final int INBOX = 4000;
    private static final int COMPOSE = 4001;
    private static final int ROW_BASE = 4010;
    private static final int PREVIOUS = 4090;
    private static final int NEXT = 4091;
    private static final int CLAIM = 4092;
    private static final int DELETE = 4093;
    private static final int ATTACHMENT = 4094;
    private static final int SEND = 4095;
    private static final int REFRESH = 4096;
    private static final int CLEAR_ATTACHMENTS = 4097;
    private static final int MAX_ATTACHMENTS = 9;

    private int mode;
    private int page;
    private int selectedIndex = -1;
    private int rowsPerPage;
    private int left;
    private int top;
    private int width;
    private int height;
    private int listWidth;
    private int observedRevision = -1;
    private final List<Integer> attachmentSlots = new ArrayList<Integer>();
    private GuiTextField recipient;
    private GuiTextField subject;
    private GuiTextField body;
    private boolean refreshButtons;

    @Override
    public String getTabName() {
        int unread = ClientQuestData.get().mail.unread;
        return unread > 0 ? text("gui.rebornaddon.tab.mail_unread", "Mail (%s)", unread)
                : text("gui.rebornaddon.tab.mail", "Mail");
    }

    @Override
    public int getTabColorActive() { return Theme.COPPER; }
    @Override
    public int getTabColorHover() { return Theme.GOLD; }

    @Override
    public void buildButtons(List<GuiButton> buttons, int left, int top, int width, int height) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        listWidth = Math.max(120, Math.min(235, width * 43 / 100));
        int modeWidth = Math.max(70, Math.min(105, (width - 64) / 2));
        buttons.add(modeButton(INBOX, left, top, modeWidth,
                text("gui.rebornaddon.mail.inbox", "Inbox"), mode == 0));
        buttons.add(modeButton(COMPOSE, left + modeWidth + 4, top, modeWidth,
                text("gui.rebornaddon.mail.compose", "Compose"), mode == 1));
        buttons.add(new ThemedButton(REFRESH, left + width - 56, top, 56, 20,
                text("gui.rebornaddon.mail.refresh", "Refresh"),
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL, Theme.BUTTON_TEXT));
        if (mode == 0) buildInbox(buttons);
        else buildCompose(buttons);
    }

    private void buildInbox(List<GuiButton> buttons) {
        int listTop = top + 34;
        int pagerY = top + height - 21;
        rowsPerPage = Math.max(2, Math.min(8, (pagerY - listTop - 2) / 23));
        List<ClientQuestData.MailMessage> messages = ClientQuestData.get().mail.messages;
        int pages = Math.max(1, (messages.size() + rowsPerPage - 1) / rowsPerPage);
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * rowsPerPage;
        int end = Math.min(messages.size(), start + rowsPerPage);
        for (int i = start; i < end; i++) {
            ClientQuestData.MailMessage message = messages.get(i);
            ThemedButton row = new ThemedButton(ROW_BASE + i - start, left, listTop + (i - start) * 23,
                    listWidth, 20, (message.read ? "" : "* ") + message.subject,
                    Theme.TAB_INACTIVE_BG, Theme.COPPER, Theme.BUTTON_TEXT);
            row.setSelected(i == selectedIndex);
            row.setTextAlignment(ThemedButton.Alignment.LEFT);
            row.setTextInset(8);
            buttons.add(row);
        }
        ThemedButton previous = new ThemedButton(PREVIOUS, left, pagerY, 22, 18, "<",
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL, Theme.BUTTON_TEXT);
        previous.enabled = page > 0;
        buttons.add(previous);
        ThemedButton next = new ThemedButton(NEXT, left + listWidth - 22, pagerY, 22, 18, ">",
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL, Theme.BUTTON_TEXT);
        next.enabled = page + 1 < pages;
        buttons.add(next);
        ClientQuestData.MailMessage selected = selected();
        if (selected != null) {
            int rightX = left + listWidth + 10;
            int rightWidth = width - listWidth - 10;
            int actionWidth = Math.max(55, (rightWidth - 4) / 2);
            ThemedButton claim = new ThemedButton(CLAIM, rightX, pagerY, actionWidth, 18,
                    text("gui.rebornaddon.mail.claim", "Claim Items"),
                    Theme.TEAL_DARK, Theme.TEAL, Theme.BUTTON_TEXT);
            claim.enabled = !selected.claimed && !selected.items.isEmpty();
            buttons.add(claim);
            ThemedButton delete = new ThemedButton(DELETE, rightX + actionWidth + 4, pagerY,
                    rightWidth - actionWidth - 4, 18,
                    text("gui.rebornaddon.mail.delete", "Delete"),
                    Theme.RANKED_RED_DARK, Theme.RANKED_RED, Theme.BUTTON_TEXT);
            delete.enabled = selected.claimed || selected.items.isEmpty();
            buttons.add(delete);
        }
    }

    private void buildCompose(List<GuiButton> buttons) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String recipientText = recipient == null ? "" : recipient.getText();
        String subjectText = subject == null ? "" : subject.getText();
        String bodyText = body == null ? "" : body.getText();
        recipient = field(font, 4100, left + 8, top + 51, width - 16, 18, 40, recipientText);
        subject = field(font, 4101, left + 8, top + 91, width - 16, 18, 80, subjectText);
        body = field(font, 4102, left + 8, top + 131, width - 16, 20, 1000, bodyText);
        int actionY = top + height - 25;
        int clearWidth = Math.max(42, Math.min(62, width / 7));
        int attachWidth = Math.max(100, width * 48 / 100);
        buttons.add(new ThemedButton(ATTACHMENT, left + 8, actionY, attachWidth, 20,
                attachmentLabel(), Theme.TAB_INACTIVE_BG, Theme.COPPER, Theme.BUTTON_TEXT));
        ThemedButton clear = new ThemedButton(CLEAR_ATTACHMENTS, left + 12 + attachWidth,
                actionY, clearWidth, 20, text("gui.rebornaddon.mail.clear", "Clear"),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED, Theme.BUTTON_TEXT);
        clear.enabled = !attachmentSlots.isEmpty();
        buttons.add(clear);
        buttons.add(new ThemedButton(SEND, left + 16 + attachWidth + clearWidth, actionY,
                width - attachWidth - clearWidth - 24, 20,
                text("gui.rebornaddon.mail.send", "Send"),
                Theme.TEAL_DARK, Theme.TEAL, Theme.BUTTON_TEXT));
    }

    @Override
    public void drawContent(GuiScreen screen, int left, int top, int width, int height,
                            int mouseX, int mouseY) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        if (mode == 1) {
            GuiChrome.section(left, top + 29, width, Math.max(80, height - 58), Theme.COPPER);
            font.drawString(text("gui.rebornaddon.mail.recipient", "Recipient"), left + 8, top + 38, Theme.TEXT_MUTED);
            recipient.drawTextBox();
            font.drawString(text("gui.rebornaddon.mail.subject", "Subject"), left + 8, top + 78, Theme.TEXT_MUTED);
            subject.drawTextBox();
            font.drawString(text("gui.rebornaddon.mail.note", "Note"), left + 8, top + 118, Theme.TEXT_MUTED);
            body.drawTextBox();
            return;
        }
        int bodyTop = top + 29;
        int rightX = left + listWidth + 10;
        int rightWidth = width - listWidth - 10;
        GuiChrome.section(left - 3, top + 29, listWidth + 6, Math.max(50, height - 51), Theme.COPPER);
        GuiChrome.section(rightX - 3, bodyTop, rightWidth + 3, Math.max(50, height - 50), Theme.GOLD);
        ClientQuestData.MailMessage selected = selected();
        if (selected == null) {
            font.drawString(text("gui.rebornaddon.mail.empty", "No message selected."),
                    rightX + 8, bodyTop + 13, Theme.TEXT_MUTED);
            return;
        }
        font.drawString(font.trimStringToWidth(selected.subject, rightWidth - 16),
                rightX + 8, bodyTop + 10, Theme.GOLD);
        font.drawString(font.trimStringToWidth(text("gui.rebornaddon.mail.from", "From %s", selected.sender),
                rightWidth - 16), rightX + 8, bodyTop + 23, Theme.TEXT_MUTED);
        GuiChrome.rule(rightX + 8, left + width - 8, bodyTop + 36, Theme.COPPER);
        int y = bodyTop + 44;
        for (String line : font.listFormattedStringToWidth(selected.body, Math.max(40, rightWidth - 16))) {
            if (y > top + height - 55) break;
            font.drawString(line, rightX + 8, y, Theme.TEXT_LIGHT);
            y += 10;
        }
        if (!selected.items.isEmpty() && y < top + height - 65) {
            y += 5;
            font.drawString(text("gui.rebornaddon.mail.attachments", "Attachments"), rightX + 8, y, Theme.COPPER);
            y += 11;
            for (String item : selected.items) {
                if (y > top + height - 55) break;
                font.drawString(font.trimStringToWidth(item, rightWidth - 16), rightX + 8, y, Theme.TEXT_LIGHT);
                y += 10;
            }
        }
    }

    @Override
    public boolean handleButtonClick(int id) {
        if (id == INBOX || id == COMPOSE) {
            mode = id == INBOX ? 0 : 1;
            return true;
        }
        if (id == REFRESH) {
            RebornAddonNetwork.requestQuestSync(ClientQuestData.get().catalogVersion);
            return false;
        }
        if (id == PREVIOUS || id == NEXT) {
            page += id == PREVIOUS ? -1 : 1;
            return true;
        }
        if (id >= ROW_BASE && id < ROW_BASE + rowsPerPage) {
            selectedIndex = page * rowsPerPage + id - ROW_BASE;
            ClientQuestData.MailMessage selected = selected();
            if (selected != null && !selected.read) sendId("mail.read", selected.id);
            return true;
        }
        ClientQuestData.MailMessage selected = selected();
        if (id == CLAIM && selected != null) sendId("mail.claim", selected.id);
        else if (id == DELETE && selected != null) {
            sendId("mail.delete", selected.id);
            selectedIndex = -1;
        } else if (id == ATTACHMENT) {
            cycleAttachment();
            return true;
        } else if (id == CLEAR_ATTACHMENTS) {
            attachmentSlots.clear();
            return true;
        } else if (id == SEND) {
            JsonObject payload = new JsonObject();
            payload.addProperty("target", recipient.getText().trim());
            payload.addProperty("subject", subject.getText().trim());
            payload.addProperty("body", body.getText().trim());
            JsonArray attachments = new JsonArray();
            List<ItemStack> inventory = Minecraft.getMinecraft().player.inventory.mainInventory;
            for (Integer slot : attachmentSlots) {
                if (slot == null || slot.intValue() < 0 || slot.intValue() >= inventory.size()) continue;
                ItemStack stack = inventory.get(slot.intValue());
                if (stack.isEmpty()) continue;
                JsonObject attachment = new JsonObject();
                attachment.addProperty("slot", slot.intValue());
                attachment.addProperty("count", stack.getCount());
                attachments.add(attachment);
            }
            payload.add("attachments", attachments);
            RebornAddonNetwork.sendNativeContentAction("mail.send", payload.toString());
            subject.setText("");
            body.setText("");
            attachmentSlots.clear();
            return true;
        } else return false;
        return false;
    }

    @Override
    public void handleKeyTyped(char typedChar, int keyCode) {
        if (mode == 1) {
            if (recipient.textboxKeyTyped(typedChar, keyCode)) return;
            if (subject.textboxKeyTyped(typedChar, keyCode)) return;
            body.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    public void handleMouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mode == 1) {
            recipient.mouseClicked(mouseX, mouseY, mouseButton);
            subject.mouseClicked(mouseX, mouseY, mouseButton);
            body.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public void onTick() {
        if (recipient != null) recipient.updateCursorCounter();
        if (subject != null) subject.updateCursorCounter();
        if (body != null) body.updateCursorCounter();
        if (observedRevision != ClientQuestData.getRevision()) {
            observedRevision = ClientQuestData.getRevision();
            refreshButtons = true;
        }
    }

    @Override
    public boolean consumeButtonRefresh() {
        boolean result = refreshButtons;
        refreshButtons = false;
        return result;
    }

    private ClientQuestData.MailMessage selected() {
        List<ClientQuestData.MailMessage> values = ClientQuestData.get().mail.messages;
        return selectedIndex >= 0 && selectedIndex < values.size() ? values.get(selectedIndex) : null;
    }

    private void cycleAttachment() {
        List<ItemStack> inventory = Minecraft.getMinecraft().player.inventory.mainInventory;
        if (attachmentSlots.size() >= MAX_ATTACHMENTS) return;
        int start = attachmentSlots.isEmpty() ? -1 : attachmentSlots.get(attachmentSlots.size() - 1);
        for (int offset = 1; offset <= inventory.size(); offset++) {
            int slot = (start + offset + inventory.size()) % inventory.size();
            if (!inventory.get(slot).isEmpty() && !attachmentSlots.contains(Integer.valueOf(slot))) {
                attachmentSlots.add(Integer.valueOf(slot));
                return;
            }
        }
    }

    private String attachmentLabel() {
        if (attachmentSlots.isEmpty()) {
            return text("gui.rebornaddon.mail.no_attachment", "Add attachments");
        }
        return text("gui.rebornaddon.mail.attachments", "Attachments: %s/%s",
                attachmentSlots.size(), MAX_ATTACHMENTS);
    }

    private void sendId(String action, String id) {
        JsonObject payload = new JsonObject();
        payload.addProperty("messageId", id);
        RebornAddonNetwork.sendNativeContentAction(action, payload.toString());
    }

    private ThemedButton modeButton(int id, int x, int y, int width, String label, boolean selected) {
        ThemedButton button = new ThemedButton(id, x, y, width, 20, label,
                selected ? 0xFF6B392E : Theme.TAB_INACTIVE_BG,
                Theme.COPPER, Theme.BUTTON_TEXT);
        button.setSelected(selected);
        return button;
    }

    private static GuiTextField field(FontRenderer font, int id, int x, int y,
                                      int width, int height, int maximum, String value) {
        GuiTextField field = new GuiTextField(id, font, x, y, Math.max(50, width), height);
        field.setMaxStringLength(maximum);
        field.setText(value == null ? "" : value);
        return field;
    }

    private static String text(String key, String fallback, Object... arguments) {
        return ClientLocalization.format(key, fallback, arguments);
    }
}
