package net.rebornaddon.content.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.client.RebornScaledGuiScreen;
import net.rebornaddon.content.entity.EntityMissionNpc;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class GuiNativeDialogue extends RebornScaledGuiScreen {
    private static final int PREVIOUS = 1;
    private static final int NEXT = 2;
    private static final int CHOICE_BASE = 20;
    private static final Gson GSON = new Gson();

    private final List<Page> pages = new ArrayList<Page>();
    private final String instanceId;
    private final String npcName;
    private final String npcSkin;
    private final boolean npcSlim;
    private int page;
    private final List<Integer> history = new ArrayList<Integer>();
    private final List<String> selectedChoices = new ArrayList<String>();
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private EntityMissionNpc preview;

    public GuiNativeDialogue(JsonObject data) {
        instanceId = text(data, "instanceId", "");
        npcName = text(data, "npcName", "NPC");
        npcSkin = text(data, "npcSkin", "");
        npcSlim = data.has("npcSlim") && data.get("npcSlim").getAsBoolean();
        JsonArray array = data.has("pages") && data.get("pages").isJsonArray()
                ? data.getAsJsonArray("pages") : new JsonArray();
        for (JsonElement element : array) {
            if (element.isJsonObject() && pages.size() < 256) {
                pages.add(GSON.fromJson(element, Page.class));
            }
        }
        if (pages.isEmpty()) pages.add(new Page());
    }

    @Override
    public void initGui() {
        super.initGui();
        panelWidth = Math.max(260, Math.min(620, width - 20));
        panelHeight = Math.max(170, Math.min(360, height - 30));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        preview = new EntityMissionNpc(Minecraft.getMinecraft().world);
        preview.configurePreview(npcName, npcSkin, npcSlim);
        rebuild();
    }

    private void rebuild() {
        buttonList.clear();
        ThemedButton previous = new ThemedButton(PREVIOUS, left + 14, top + panelHeight - 34,
                92, 20, ClientLocalization.format("gui.rebornaddon.dialogue.previous", "Previous"),
                Theme.TAB_INACTIVE_BG, Theme.PURPLE_MID, Theme.BUTTON_TEXT);
        previous.enabled = !history.isEmpty();
        buttonList.add(previous);
        Page current = pages.get(Math.max(0, Math.min(page, pages.size() - 1)));
        if (current.choices == null || current.choices.isEmpty()) {
            String nextLabel = page + 1 >= pages.size()
                    ? ClientLocalization.format("gui.rebornaddon.dialogue.done", "Done")
                    : ClientLocalization.format("gui.rebornaddon.dialogue.next", "Next");
            buttonList.add(new ThemedButton(NEXT, left + panelWidth - 106, top + panelHeight - 34,
                    92, 20, nextLabel, Theme.TEAL_DARK, Theme.TEAL, Theme.BUTTON_TEXT));
        } else {
            int shown = Math.min(8, current.choices.size());
            int y = top + panelHeight - 58 - shown * 22;
            for (int i = 0; i < shown; i++) {
                Choice choice = current.choices.get(i);
                ThemedButton button = new ThemedButton(CHOICE_BASE + i, left + 22, y + i * 22,
                        panelWidth - 44, 19, choice.text == null ? "" : choice.text,
                        Theme.TAB_INACTIVE_BG, Theme.GOLD, Theme.BUTTON_TEXT);
                button.setTextAlignment(ThemedButton.Alignment.LEFT);
                button.setTextInset(8);
                buttonList.add(button);
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == PREVIOUS && !history.isEmpty()) {
            page = history.remove(history.size() - 1).intValue();
            if (!selectedChoices.isEmpty()) selectedChoices.remove(selectedChoices.size() - 1);
            rebuild();
        } else if (button.id == NEXT && page + 1 < pages.size()) {
            history.add(Integer.valueOf(page));
            page++;
            rebuild();
        } else if (button.id == NEXT) {
            complete();
        } else if (button.id >= CHOICE_BASE && button.id < CHOICE_BASE + 8) {
            Page current = pages.get(Math.max(0, Math.min(page, pages.size() - 1)));
            int index = button.id - CHOICE_BASE;
            if (current.choices == null || index < 0 || index >= current.choices.size()) return;
            Choice choice = current.choices.get(index);
            history.add(Integer.valueOf(page));
            selectedChoices.add(choice.id == null ? Integer.toString(index) : choice.id);
            int next = pageIndex(choice.nextPageId);
            if (next < 0) complete();
            else {
                page = next;
                rebuild();
            }
        }
    }

    private void complete() {
            JsonObject payload = new JsonObject();
            payload.addProperty("instanceId", instanceId);
            JsonArray choices = new JsonArray();
            for (String choice : selectedChoices) choices.add(choice);
            payload.add("choices", choices);
            RebornAddonNetwork.sendNativeContentAction("dialogue.complete", payload.toString());
            mc.displayGuiScreen(null);
    }

    private int pageIndex(String id) {
        if (id == null || id.isEmpty()) return -1;
        for (int i = 0; i < pages.size(); i++) if (id.equals(pages.get(i).id)) return i;
        return -1;
    }

    @Override
    protected void drawScaledScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        GuiChrome.frame(left, top, panelWidth, panelHeight, Theme.GOLD);
        GuiChrome.header(left + 3, top + 5, panelWidth - 6, 38, Theme.GOLD);
        Page current = pages.get(Math.max(0, Math.min(page, pages.size() - 1)));
        String speaker = "player".equalsIgnoreCase(current.speaker)
                ? mc.player.getName() : npcName;
        fontRenderer.drawString(speaker, left + 58, top + 17, Theme.TEXT_LIGHT);
        String count = (page + 1) + " / " + pages.size();
        fontRenderer.drawString(count, left + panelWidth - 16 - fontRenderer.getStringWidth(count),
                top + 17, Theme.TEXT_MUTED);
        drawFaces(current);
        int textLeft = left + 22;
        int textWidth = panelWidth - 44;
        int y = top + 58;
        int choiceSpace = current.choices == null ? 0 : Math.min(8, current.choices.size()) * 22 + 26;
        for (String line : fontRenderer.listFormattedStringToWidth(
                current.text == null ? "" : current.text, textWidth)) {
            if (y > top + panelHeight - 45 - choiceSpace) break;
            fontRenderer.drawString(line, textLeft, y, Theme.TEXT_LIGHT);
            y += 11;
        }
        drawScaledControls(mouseX, mouseY, partialTicks);
    }

    private void drawFaces(Page current) {
        ResourceLocation npcTexture = RenderMissionNpc.textureFor(preview);
        ResourceLocation playerTexture = mc.player.getLocationSkin();
        drawFace(npcTexture, left + 14, top + 10, !"player".equalsIgnoreCase(current.speaker));
        drawFace(playerTexture, left + panelWidth - 46, top + 10,
                "player".equalsIgnoreCase(current.speaker));
    }

    private void drawFace(ResourceLocation texture, int x, int y, boolean active) {
        mc.getTextureManager().bindTexture(texture);
        GlStateManager.color(1.0F, 1.0F, 1.0F, active ? 1.0F : 0.55F);
        drawScaledCustomSizeModalRect(x, y, 8.0F, 8.0F, 8, 8, 32, 32, 64.0F, 64.0F);
        drawScaledCustomSizeModalRect(x, y, 40.0F, 8.0F, 8, 8, 32, 32, 64.0F, 64.0F);
        drawRect(x - 2, y - 2, x + 34, y, active ? Theme.GOLD : Theme.BUTTON_BORDER);
        drawRect(x - 2, y + 32, x + 34, y + 34, active ? Theme.GOLD : Theme.BUTTON_BORDER);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static String text(JsonObject object, String key, String fallback) {
        return object.has(key) ? object.get(key).getAsString() : fallback;
    }

    private static final class Page {
        private String id = "";
        private String speaker = "npc";
        private String text = "";
        private List<Choice> choices = new ArrayList<Choice>();
    }

    private static final class Choice {
        private String id = "";
        private String text = "";
        private String nextPageId = "";
    }
}
