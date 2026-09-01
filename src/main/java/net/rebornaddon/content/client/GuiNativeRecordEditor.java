package net.rebornaddon.content.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.client.RebornScaledGuiScreen;
import net.rebornaddon.content.entity.EntityMissionNpc;
import net.rebornaddon.content.NativeContentModel;
import net.rebornaddon.gui.GuiHub;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class GuiNativeRecordEditor extends RebornScaledGuiScreen {
    private static final Gson GSON = new Gson();
    private static final int ROW_BASE = 3000;
    private static final int PREVIOUS = 3090;
    private static final int NEXT = 3091;
    private static final int TOGGLE = 3092;
    private static final int ADD = 3093;
    private static final int REMOVE = 3094;
    private static final int POSITION = 3095;
    private static final int SAVE = 3096;
    private static final int CANCEL = 3097;
    private static final int PICK = 3098;
    private static final int SKIN_LIBRARY = 3099;
    private static final int GROUP_BASE = 3300;

    private final String type;
    private final JsonObject record;
    private final List<Node> nodes = new ArrayList<Node>();
    private final boolean duplicate;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int split;
    private int listTop;
    private int rowsPerPage;
    private int contentTop;
    private int groupIndex;
    private int page;
    private int selectedIndex;
    private GuiTextField valueField;
    private GuiTextField hoursField;
    private GuiTextField minutesField;
    private GuiTextField secondsField;
    private String durationContext = "";
    private EntityMissionNpc preview;
    private String previewState = "";
    private boolean previewDragging;
    private int previewLastX;
    private int previewLastY;
    private float previewYaw = 20.0F;
    private float previewPitch = -8.0F;
    private int previewLeft;
    private int previewTop;
    private int previewRight;
    private int previewBottom;
    private long lastAddAt;
    private final Map<String, String> expandedArrays = new HashMap<String, String>();
    private final Set<String> expandedObjects = new HashSet<String>();

    public GuiNativeRecordEditor(String type, JsonObject source, boolean duplicate) {
        this.type = type == null ? "templates" : type;
        this.duplicate = duplicate;
        this.record = source == null ? defaultRecord(this.type)
                : new JsonParser().parse(source.toString()).getAsJsonObject();
        ensureEditorSchema(this.type, this.record);
        if (duplicate) record.addProperty("id", value(record, "id") + "-copy");
        rebuildNodes();
    }

    @Override
    public void initGui() {
        super.initGui();
        panelWidth = Math.max(330, Math.min(760, width - 14));
        panelHeight = Math.max(250, Math.min(680, height - 14));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        split = left + Math.max(150, panelWidth * 43 / 100);
        String[] groups = editorGroups();
        int groupColumns = groupColumns();
        int groupRows = (groups.length + groupColumns - 1) / groupColumns;
        contentTop = top + 50 + groupRows * 22;
        listTop = contentTop + 7;
        rowsPerPage = Math.max(4, (top + panelHeight - 59 - listTop) / 22);
        if (mc.world != null) {
            preview = new EntityMissionNpc(mc.world);
            previewState = "";
        }
        rebuildButtons();
    }

    private void rebuildButtons() {
        buttonList.clear();
        prepareField();
        addGroupButtons();
        int pages = Math.max(1, (nodes.size() + rowsPerPage - 1) / rowsPerPage);
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * rowsPerPage;
        int end = Math.min(nodes.size(), start + rowsPerPage);
        for (int i = start; i < end; i++) {
            Node node = nodes.get(i);
            ThemedButton row = button(ROW_BASE + i - start, left + 12,
                    listTop + (i - start) * 22, split - left - 24, 19,
                    node.label(), Theme.TAB_INACTIVE_BG, Theme.GOLD);
            row.setSelected(i == selectedIndex);
            row.setTextAlignment(ThemedButton.Alignment.LEFT);
            row.setTextInset(7);
            buttonList.add(row);
        }
        int pagerY = top + panelHeight - 52;
        ThemedButton previous = button(PREVIOUS, left + 12, pagerY, 28, 19,
                "<", Theme.TAB_INACTIVE_BG, Theme.NEUTRAL);
        previous.enabled = page > 0;
        buttonList.add(previous);
        ThemedButton next = button(NEXT, split - 40, pagerY, 28, 19,
                ">", Theme.TAB_INACTIVE_BG, Theme.NEUTRAL);
        next.enabled = page + 1 < pages;
        buttonList.add(next);

        Node selected = selected();
        int rightX = split + 12;
        int rightWidth = left + panelWidth - rightX - 12;
        int actionY = contentTop + 76;
        if (selected != null && selected.value != null && selected.value.isJsonPrimitive()
                && selected.value.getAsJsonPrimitive().isBoolean()) {
            boolean state = selected.value.getAsBoolean();
            buttonList.add(button(TOGGLE, rightX, actionY, rightWidth, 20,
                    state ? text("gui.rebornaddon.world.enabled", "Enabled")
                            : text("gui.rebornaddon.world.disabled", "Disabled"),
                    state ? Theme.TEAL_DARK : Theme.RANKED_RED_DARK,
                    state ? Theme.TEAL : Theme.RANKED_RED));
        }
        if (selected != null && selected.value != null && (selected.value.isJsonArray()
                 || selected.value.isJsonObject() && "overrides".equals(selected.key()))) {
            buttonList.add(button(ADD, rightX, actionY, rightWidth, 20,
                    selected.value.isJsonArray() && "jutsus".equals(selected.key())
                            ? text("gui.rebornaddon.world.add_jutsu", "Add Jutsu")
                            : selected.value.isJsonArray() ? text("gui.rebornaddon.world.add_entry", "Add Entry")
                            : text("gui.rebornaddon.world.add_overrides", "Add Override Fields"),
                    Theme.TEAL_DARK, Theme.TEAL));
        }
        String picker = pickerKind(selected);
        if (picker != null) {
            buttonList.add(button(PICK, rightX, actionY, rightWidth, 20,
                    pickerLabel(picker),
                    Theme.PURPLE_DARK, Theme.PURPLE_LIGHT));
        }
        if (selected != null && selected.removableArrayIndex() >= 0) {
            buttonList.add(button(REMOVE, rightX, actionY + 24, rightWidth, 20,
                    text("gui.rebornaddon.world.remove_entry", "Remove Entry"),
                    Theme.RANKED_RED_DARK, Theme.RANKED_RED));
        }
        if (selected != null && selected.coordinateObject(record) != null) {
            buttonList.add(button(POSITION, rightX, actionY + 48, rightWidth, 20,
                    text("gui.rebornaddon.world.use_position", "Use Current Position"),
                    Theme.TAB_INACTIVE_BG, Theme.GOLD));
        }
        int footerY = top + panelHeight - 29;
        buttonList.add(button(SAVE, left + panelWidth - 218, footerY, 100, 20,
                text("gui.rebornaddon.world.save", "Save"), Theme.TEAL_DARK, Theme.TEAL));
        buttonList.add(button(CANCEL, left + panelWidth - 112, footerY, 100, 20,
                text("gui.rebornaddon.world.cancel", "Cancel"),
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
    }

    private void addGroupButtons() {
        String[] groups = editorGroups();
        int columns = groupColumns();
        int gap = 3;
        int buttonWidth = Math.max(58, (panelWidth - 24 - gap * (columns - 1)) / columns);
        for (int i = 0; i < groups.length; i++) {
            int row = i / columns;
            int column = i % columns;
            ThemedButton button = button(GROUP_BASE + i, left + 12 + column * (buttonWidth + gap),
                    top + 48 + row * 22, buttonWidth, 19, groups[i],
                    i == groupIndex ? Theme.GOLD_DARK : Theme.TAB_INACTIVE_BG, Theme.GOLD);
            button.setSelected(i == groupIndex);
            buttonList.add(button);
        }
    }

    private void prepareField() {
        Node selected = selected();
        String existing = selected == null || selected.value == null || !selected.value.isJsonPrimitive()
                || selected.value.getAsJsonPrimitive().isBoolean() ? "" : selected.value.getAsString();
        int rightX = split + 12;
        int rightWidth = left + panelWidth - rightX - 12;
        String oldDurationContext = durationContext;
        String oldHours = hoursField == null ? "" : hoursField.getText();
        String oldMinutes = minutesField == null ? "" : minutesField.getText();
        String oldSeconds = secondsField == null ? "" : secondsField.getText();
        boolean oldHoursFocused = hoursField != null && hoursField.isFocused();
        boolean oldMinutesFocused = minutesField != null && minutesField.isFocused();
        boolean oldSecondsFocused = secondsField != null && secondsField.isFocused();
        String nextDurationContext = isDurationNode(selected) ? selected.pathKey() : "";
        valueField = new GuiTextField(3200, fontRenderer, rightX, contentTop + 45,
                Math.max(80, rightWidth), 20);
        valueField.setMaxStringLength(8192);
        valueField.setText(existing);
        int gap = 4;
        int durationWidth = Math.max(38, (rightWidth - gap * 2) / 3);
        hoursField = durationField(3201, rightX, durationWidth);
        minutesField = durationField(3202, rightX + durationWidth + gap, durationWidth);
        secondsField = durationField(3203, rightX + (durationWidth + gap) * 2,
                rightWidth - durationWidth * 2 - gap * 2);
        if (!nextDurationContext.isEmpty()) {
            long totalSeconds = durationSeconds(selected);
            if (nextDurationContext.equals(oldDurationContext)) {
                hoursField.setText(oldHours);
                minutesField.setText(oldMinutes);
                secondsField.setText(oldSeconds);
                hoursField.setFocused(oldHoursFocused);
                minutesField.setFocused(oldMinutesFocused);
                secondsField.setFocused(oldSecondsFocused);
            } else {
                hoursField.setText(Long.toString(totalSeconds / 3600L));
                minutesField.setText(Long.toString(totalSeconds % 3600L / 60L));
                secondsField.setText(Long.toString(totalSeconds % 60L));
            }
        }
        durationContext = nextDurationContext;
    }

    private GuiTextField durationField(int id, int x, int fieldWidth) {
        GuiTextField field = new GuiTextField(id, fontRenderer, x, contentTop + 52,
                Math.max(30, fieldWidth), 20);
        field.setMaxStringLength(8);
        return field;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id >= GROUP_BASE && button.id < GROUP_BASE + editorGroups().length) {
            commitField();
            groupIndex = button.id - GROUP_BASE;
            page = 0;
            selectedIndex = 0;
            rebuildNodes();
            rebuildButtons();
            return;
        }
        if (button.id >= ROW_BASE && button.id < ROW_BASE + rowsPerPage) {
            commitField();
            int index = page * rowsPerPage + button.id - ROW_BASE;
            Node clicked = index >= 0 && index < nodes.size() ? nodes.get(index) : null;
            if (clicked != null && clicked.isCollapsible()) {
                toggleExpanded(clicked);
                rebuildNodesKeepingKey(clicked.pathKey());
            } else {
                selectedIndex = index;
            }
            rebuildButtons();
            return;
        }
        if (button.id == PREVIOUS || button.id == NEXT) {
            commitField();
            page += button.id == PREVIOUS ? -1 : 1;
            selectedIndex = Math.min(nodes.size() - 1, Math.max(0, page * rowsPerPage));
            rebuildButtons();
            return;
        }
        Node selected = selected();
        if (button.id == TOGGLE && selected != null) {
            selected.replace(record, new JsonPrimitive(!selected.value.getAsBoolean()));
            rebuildNodesKeeping(selected.pathText());
            rebuildButtons();
        } else if (button.id == ADD && selected != null && (selected.value.isJsonArray()
                || selected.value.isJsonObject())) {
            long now = System.currentTimeMillis();
            if (now - lastAddAt < 250L) return;
            lastAddAt = now;
            if (selected.value.isJsonArray() && "jutsus".equals(selected.key())) {
                mc.displayGuiScreen(new GuiNativeCatalogPicker(this, "jutsu"));
                return;
            }
            String addedKey = "";
            if (selected.value.isJsonArray()) {
                JsonArray array = selected.value.getAsJsonArray();
                array.add(defaultArrayElement(selected.key()));
                expandedObjects.add(selected.pathKey());
                String entryKey = selected.pathKey() + "/[" + (array.size() - 1) + "]";
                expandedArrays.put(selected.pathKey(), entryKey);
                addedKey = entryKey;
            }
            else {
                JsonObject target = selected.value.getAsJsonObject();
                for (java.util.Map.Entry<String, JsonElement> entry : defaultOverrides().entrySet())
                    if (!target.has(entry.getKey())) target.add(entry.getKey(), entry.getValue());
            }
            if (addedKey.isEmpty()) rebuildNodesKeeping(selected.pathText());
            else rebuildNodesKeepingKey(addedKey);
            rebuildButtons();
        } else if (button.id == REMOVE && selected != null) {
            removeArrayElement(selected);
            rebuildNodes();
            selectedIndex = Math.max(0, Math.min(selectedIndex, nodes.size() - 1));
            rebuildButtons();
        } else if (button.id == POSITION && selected != null) {
            JsonObject coordinates = selected.coordinateObject(record);
            if (coordinates != null && mc.player != null) {
                coordinates.addProperty("dimension", mc.player.dimension);
                coordinates.addProperty("x", Math.floor(mc.player.posX) + 0.5D);
                coordinates.addProperty("y", Math.floor(mc.player.posY));
                coordinates.addProperty("z", Math.floor(mc.player.posZ) + 0.5D);
                if (coordinates.has("spawnX")) {
                    coordinates.addProperty("spawnX", Math.floor(mc.player.posX) + 0.5D);
                    coordinates.addProperty("spawnY", Math.floor(mc.player.posY));
                    coordinates.addProperty("spawnZ", Math.floor(mc.player.posZ) + 0.5D);
                }
                rebuildNodesKeeping(selected.pathText());
                rebuildButtons();
            }
        } else if (button.id == PICK && selected != null) {
            String kind = pickerKind(selected);
            if (kind != null) mc.displayGuiScreen(new GuiNativeCatalogPicker(this, kind));
        } else if (button.id == SAVE) {
            commitField();
            JsonObject payload = new JsonObject();
            payload.addProperty("type", type);
            payload.add("record", record);
            RebornAddonNetwork.sendNativeContentAction("admin.draft.upsert", payload.toString());
            mc.displayGuiScreen(new GuiHub("world"));
        } else if (button.id == CANCEL) {
            mc.displayGuiScreen(new GuiHub("world"));
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (isDurationNode(selected())) {
            if (hoursField.textboxKeyTyped(typedChar, keyCode)
                    || minutesField.textboxKeyTyped(typedChar, keyCode)
                    || secondsField.textboxKeyTyped(typedChar, keyCode)) return;
        }
        if (valueField != null && valueField.textboxKeyTyped(typedChar, keyCode)) return;
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (isDurationNode(selected())) {
            hoursField.mouseClicked(mouseX, mouseY, mouseButton);
            minutesField.mouseClicked(mouseX, mouseY, mouseButton);
            secondsField.mouseClicked(mouseX, mouseY, mouseButton);
        } else if (valueField != null) valueField.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton == 0 && mouseX >= previewLeft && mouseX < previewRight
                && mouseY >= previewTop && mouseY < previewBottom) {
            previewDragging = true;
            previewLastX = mouseX;
            previewLastY = mouseY;
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton,
                                  long timeSinceLastClick) {
        if (previewDragging && clickedMouseButton == 0) {
            previewYaw = (previewYaw + (mouseX - previewLastX) * 1.25F) % 360.0F;
            previewPitch = Math.max(-75.0F, Math.min(75.0F,
                    previewPitch - (mouseY - previewLastY) * 0.9F));
            previewLastX = mouseX;
            previewLastY = mouseY;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        previewDragging = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (valueField != null) valueField.updateCursorCounter();
        if (hoursField != null) hoursField.updateCursorCounter();
        if (minutesField != null) minutesField.updateCursorCounter();
        if (secondsField != null) secondsField.updateCursorCounter();
    }

    @Override
    protected void drawScaledScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        GuiChrome.frame(left, top, panelWidth, panelHeight, Theme.GOLD);
        GuiChrome.header(left + 3, top + 5, panelWidth - 6, 39, Theme.GOLD);
        fontRenderer.drawString(text("gui.rebornaddon.world.editor_title", "Server Content Editor"),
                left + 15, top + 15, Theme.TEXT_LIGHT);
        String scope = humanize(type) + (duplicate ? " / "
                + text("gui.rebornaddon.world.copy", "Copy") : "");
        fontRenderer.drawString(scope, left + panelWidth - 15 - fontRenderer.getStringWidth(scope),
                top + 15, Theme.TEXT_MUTED);
        GuiChrome.section(left + 7, contentTop, split - left - 10,
                top + panelHeight - contentTop - 56, Theme.GOLD);
        GuiChrome.section(split + 6, contentTop, left + panelWidth - split - 13,
                top + panelHeight - contentTop - 56, Theme.GOLD);
        Node selected = selected();
        int rightX = split + 14;
        int rightWidth = left + panelWidth - rightX - 12;
        if (selected != null) {
            fontRenderer.drawString(fontRenderer.trimStringToWidth(selected.pathText(), rightWidth),
                    rightX, contentTop + 13, Theme.GOLD);
            fontRenderer.drawString(fontRenderer.trimStringToWidth(fieldDescription(selected), rightWidth),
                    rightX, contentTop + 28, Theme.TEXT_MUTED);
            if (selected.value != null && selected.value.isJsonPrimitive()
                    && !selected.value.getAsJsonPrimitive().isBoolean()) {
                if (isDurationNode(selected)) {
                    drawDurationFields(rightX);
                } else {
                    valueField.drawTextBox();
                }
            }
            else if (selected.value != null && selected.value.isJsonArray()) {
                fontRenderer.drawString(selected.value.getAsJsonArray().size() + " "
                                + text("gui.rebornaddon.world.entries", "entries"),
                        rightX, contentTop + 49, Theme.TEXT_LIGHT);
            }
        }
        JsonObject previewData = previewRecord();
        if (preview != null && mc.world != null && previewData != null && panelHeight >= 390) {
            String state = previewData.toString();
            if (!state.equals(previewState)) {
                NativeContentModel.NpcTemplate template = GSON.fromJson(
                        previewData, NativeContentModel.NpcTemplate.class);
                preview.configure(template, "preview", "preview", null, "preview");
                previewState = state;
            }
            int center = split + (left + panelWidth - split) / 2;
            previewLeft = split + 10;
            previewRight = left + panelWidth - 10;
            previewTop = contentTop + 126;
            previewBottom = top + panelHeight - 33;
            int previewHeight = Math.max(80, previewBottom - previewTop);
            int modelScale = Math.max(76, Math.min(110, previewHeight * 11 / 40));
            int modelY = previewTop + Math.min(previewHeight - 18, previewHeight * 72 / 100);
            drawPreviewEntity(center, modelY, modelScale, previewYaw, previewPitch, preview);
            int instructionY = Math.max(contentTop + 58, previewTop - 65);
            String label = text("gui.rebornaddon.world.preview", "Model Preview");
            fontRenderer.drawString(label, center - fontRenderer.getStringWidth(label) / 2,
                    instructionY, Theme.TEXT_MUTED);
            String hint = text("gui.rebornaddon.world.preview_rotate", "Drag horizontally and vertically to rotate");
            fontRenderer.drawString(hint, center - fontRenderer.getStringWidth(hint) / 2,
                    instructionY + 13, Theme.TEXT_MUTED);
        } else {
            previewLeft = previewTop = previewRight = previewBottom = 0;
        }
        drawScaledControls(mouseX, mouseY, partialTicks);
    }

    private void drawDurationFields(int rightX) {
        fontRenderer.drawString(text("gui.rebornaddon.time.hours", "Hours"),
                hoursField.x, contentTop + 42, Theme.TEXT_MUTED);
        fontRenderer.drawString(text("gui.rebornaddon.time.minutes", "Minutes"),
                minutesField.x, contentTop + 42, Theme.TEXT_MUTED);
        fontRenderer.drawString(text("gui.rebornaddon.time.seconds", "Seconds"),
                secondsField.x, contentTop + 42, Theme.TEXT_MUTED);
        hoursField.drawTextBox();
        minutesField.drawTextBox();
        secondsField.drawTextBox();
    }

    private JsonObject previewRecord() {
        if ("templates".equals(type) || "skinLibrary".equals(type)) return record;
        Node selected = selected();
        JsonObject holder = selected == null ? null : selected.configurationObject(record);
        if (holder == null && record.has("templateId")) holder = record;
        if (holder == null) return null;
        String templateId = value(holder, "templateId");
        JsonObject base = template(templateId);
        if (base == null) return null;
        JsonObject merged = new JsonParser().parse(base.toString()).getAsJsonObject();
        if (holder.has("overrides") && holder.get("overrides").isJsonObject()) {
            for (java.util.Map.Entry<String, JsonElement> entry
                    : holder.getAsJsonObject("overrides").entrySet()) {
                merged.add(entry.getKey(), entry.getValue());
            }
        }
        return merged;
    }

    private static JsonObject template(String id) {
        JsonObject wrapper = ClientNativeContentData.admin();
        if (!wrapper.has("data") || !wrapper.get("data").isJsonObject()) return null;
        JsonObject data = wrapper.getAsJsonObject("data");
        if (!data.has("templates") || !data.get("templates").isJsonArray()) return null;
        for (JsonElement element : data.getAsJsonArray("templates")) {
            if (element.isJsonObject() && id.equals(value(element.getAsJsonObject(), "id"))) {
                return element.getAsJsonObject();
            }
        }
        return null;
    }

    private void commitField() {
        Node selected = selected();
        if (selected == null || valueField == null || selected.value == null
                || !selected.value.isJsonPrimitive() || selected.value.getAsJsonPrimitive().isBoolean()) return;
        JsonPrimitive old = selected.value.getAsJsonPrimitive();
        if (isDurationNode(selected)) {
            Long seconds = parsedDurationSeconds();
            if (seconds == null) return;
            long stored = durationUsesTicks(selected.key())
                    ? Math.min(Integer.MAX_VALUE, seconds.longValue() * 20L) : seconds.longValue();
            String path = selected.pathKey();
            selected.replace(record, new JsonPrimitive(Long.valueOf(stored)));
            rebuildNodesKeepingKey(path);
            return;
        }
        String input = valueField.getText();
        JsonElement replacement;
        if (old.isNumber()) {
            try { replacement = new JsonPrimitive(Double.valueOf(input.trim())); }
            catch (NumberFormatException ignored) { return; }
        } else replacement = new JsonPrimitive(input);
        String path = selected.pathText();
        selected.replace(record, replacement);
        rebuildNodesKeeping(path);
    }

    private void rebuildNodes() {
        List<Node> flattened = new ArrayList<Node>();
        flatten(record, new ArrayList<Object>(), "", flattened, 0);
        nodes.clear();
        for (Node node : flattened) if (nodeGroup(node) == groupIndex) nodes.add(node);
        selectedIndex = Math.max(0, Math.min(selectedIndex, nodes.size() - 1));
    }

    private String[] editorGroups() {
        if ("templates".equals(type)) return new String[] {
                "Identity", "Appearance", "Combat", "Behavior", "Equipment", "Jutsu", "Factions"
        };
        if ("actors".equals(type)) return new String[] {"Identity", "Configuration", "Spawn", "Drops"};
        if ("quests".equals(type)) return new String[] {"Details", "Progression", "Objectives", "Rewards"};
        if ("bosses".equals(type)) return new String[] {"Identity", "Configuration", "Spawn", "Drops"};
        if ("dungeons".equals(type)) return new String[] {"Setup", "Location", "Encounters"};
        if ("paths".equals(type)) return new String[] {"Setup", "Route Points"};
        if ("worldBossPools".equals(type)) return new String[] {"Schedule", "Boss Pool", "Locations"};
        return new String[] {"Details"};
    }

    private int groupColumns() {
        return panelWidth >= 330 ? 4 : 2;
    }

    private int nodeGroup(Node node) {
        String root = node.rootKey();
        if ("templates".equals(type)) {
            if ("skin".equals(root) || "slim".equals(root)) return 1;
            if (combatTemplateField(root)) return 2;
            if ("equipment".equals(root)) return 4;
            if ("jutsus".equals(root)) return 5;
            if ("faction".equals(root) || root.endsWith("Villages") || root.endsWith("Factions")) return 6;
            if (!("id".equals(root) || "name".equals(root))) return 3;
            return 0;
        }
        if ("actors".equals(type) || "bosses".equals(type)) {
            if ("drops".equals(root)) return 3;
            if ("templateId".equals(root) || "overrides".equals(root)) return 1;
            if (!("id".equals(root) || "name".equals(root))) return 2;
            return 0;
        }
        if ("quests".equals(type)) {
            if ("steps".equals(root)) return 2;
            if (root.startsWith("reward") || "rewards".equals(root) || "completionText".equals(root)
                    || "completionNpc".equals(root)) return 3;
            if ("prerequisites".equals(root) || "nextQuestId".equals(root)
                    || "repeatable".equals(root) || "enabled".equals(root)) return 1;
            return 0;
        }
        if ("dungeons".equals(type)) {
            if ("spawns".equals(root)) return 2;
            if ("dimension".equals(root) || "x".equals(root) || "y".equals(root)
                    || "z".equals(root) || root.endsWith("Radius")) return 1;
            return 0;
        }
        if ("paths".equals(type)) return "points".equals(root) ? 1 : 0;
        if ("worldBossPools".equals(type)) {
            if ("bosses".equals(root)) return 1;
            if ("locations".equals(root)) return 2;
            return 0;
        }
        return 0;
    }

    private static boolean combatTemplateField(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("damage") || lower.contains("knockback") || lower.contains("health")
                || lower.contains("combat") || lower.contains("attack") || lower.contains("melee")
                || lower.contains("regen") || "hostile".equals(lower)
                || "aggression".equals(lower) || "followrange".equals(lower);
    }

    private void rebuildNodesKeeping(String path) {
        rebuildNodes();
        for (int i = 0; i < nodes.size(); i++) if (nodes.get(i).pathText().equals(path)) {
            selectedIndex = i;
            page = selectedIndex / Math.max(1, rowsPerPage);
            break;
        }
    }

    private void rebuildNodesKeepingKey(String pathKey) {
        rebuildNodes();
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).pathKey().equals(pathKey)) {
                selectedIndex = i;
                page = selectedIndex / Math.max(1, rowsPerPage);
                return;
            }
        }
    }

    private boolean isExpanded(Node node) {
        if (node.isArrayElementObject()) {
            return node.pathKey().equals(expandedArrays.get(node.arrayParentKey()));
        }
        return expandedObjects.contains(node.pathKey());
    }

    private void toggleExpanded(Node node) {
        if (node.isArrayElementObject()) {
            String parent = node.arrayParentKey();
            if (node.pathKey().equals(expandedArrays.get(parent))) expandedArrays.remove(parent);
            else expandedArrays.put(parent, node.pathKey());
        } else if (!expandedObjects.remove(node.pathKey())) {
            expandedObjects.add(node.pathKey());
        }
    }

    private String arrayEntryLabel(String arrayKey, JsonArray array, int index) {
        JsonElement element = array.get(index);
        String base = "";
        if (element != null && element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if ("jutsus".equals(arrayKey)) base = jutsuName(value(object, "id"));
            else if ("spawns".equals(arrayKey)) {
                base = templateName(value(object, "templateId"));
                if (base.isEmpty()) base = value(object, "name");
            } else if (object.has("templateId")) base = templateName(value(object, "templateId"));
            if (base.isEmpty()) base = firstValue(object, "title", "name", "objective", "id", "item", "type");
        } else if (element != null && element.isJsonPrimitive()) {
            base = element.getAsString();
        }
        if (base.isEmpty()) base = humanize(arrayKey) + " " + (index + 1);

        int total = 0;
        int ordinal = 0;
        for (int i = 0; i < array.size(); i++) {
            String candidate = simpleArrayEntryName(arrayKey, array.get(i));
            if (candidate.equalsIgnoreCase(base)) {
                total++;
                if (i <= index) ordinal++;
            }
        }
        return total > 1 ? base + " " + ordinal : base;
    }

    private String simpleArrayEntryName(String arrayKey, JsonElement element) {
        if (element == null || element.isJsonNull()) return "";
        if (element.isJsonPrimitive()) return element.getAsString();
        if (!element.isJsonObject()) return "";
        JsonObject object = element.getAsJsonObject();
        if ("jutsus".equals(arrayKey)) return jutsuName(value(object, "id"));
        if ("spawns".equals(arrayKey) || object.has("templateId")) {
            String name = templateName(value(object, "templateId"));
            if (!name.isEmpty()) return name;
        }
        return firstValue(object, "title", "name", "objective", "id", "item", "type");
    }

    private static String firstValue(JsonObject object, String... keys) {
        for (String key : keys) {
            String found = value(object, key);
            if (!found.isEmpty()) return found;
        }
        return "";
    }

    private static String templateName(String id) {
        JsonObject template = template(id);
        return template == null ? "" : firstValue(template, "name", "id");
    }

    private static String jutsuName(String id) {
        JsonObject wrapper = ClientNativeContentData.admin();
        if (wrapper.has("jutsuCatalog") && wrapper.get("jutsuCatalog").isJsonArray()) {
            for (JsonElement element : wrapper.getAsJsonArray("jutsuCatalog")) {
                if (!element.isJsonObject()) continue;
                JsonObject entry = element.getAsJsonObject();
                if (id.equals(value(entry, "id"))) {
                    String name = firstValue(entry, "name", "displayName", "title");
                    if (!name.isEmpty()) return name;
                }
            }
        }
        return id.isEmpty() ? text("gui.rebornaddon.world.unassigned", "Unassigned") : id;
    }

    private static boolean isDurationNode(Node node) {
        return node != null && node.value != null && node.value.isJsonPrimitive()
                && node.value.getAsJsonPrimitive().isNumber() && isDurationKey(node.key());
    }

    private static boolean isDurationKey(String key) {
        String lower = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return lower.endsWith("seconds") || lower.endsWith("ticks")
                || lower.contains("cooldown") || lower.contains("duration")
                || lower.contains("interval") || lower.contains("respawn")
                || lower.contains("delay") || lower.startsWith("wait");
    }

    private static boolean durationUsesTicks(String key) {
        return key != null && key.toLowerCase(Locale.ROOT).endsWith("ticks");
    }

    private static long durationSeconds(Node node) {
        if (!isDurationNode(node)) return 0L;
        double stored = node.value.getAsDouble();
        double seconds = durationUsesTicks(node.key()) ? stored / 20.0D : stored;
        return Math.max(0L, Math.round(seconds));
    }

    private Long parsedDurationSeconds() {
        try {
            long hours = parseDurationPart(hoursField);
            long minutes = parseDurationPart(minutesField);
            long seconds = parseDurationPart(secondsField);
            if (hours > 100000L || minutes > 1000000L || seconds > 100000000L) return null;
            return Long.valueOf(Math.addExact(Math.addExact(Math.multiplyExact(hours, 3600L),
                    Math.multiplyExact(minutes, 60L)), seconds));
        } catch (ArithmeticException | NumberFormatException ignored) {
            return null;
        }
    }

    private static long parseDurationPart(GuiTextField field) {
        String value = field == null ? "" : field.getText().trim();
        if (value.isEmpty()) return 0L;
        long parsed = Long.parseLong(value);
        if (parsed < 0L) throw new NumberFormatException();
        return parsed;
    }

    private void flatten(JsonElement value, List<Object> path, String label,
                         List<Node> output, int depth) {
        if (depth > 8 || output.size() >= 4096) return;
        Node current = null;
        if (!path.isEmpty()) {
            current = new Node(new ArrayList<Object>(path), label, value);
            current.expanded = isExpanded(current);
            output.add(current);
        }
        if (value == null || value.isJsonNull() || value.isJsonPrimitive()) return;
        if (current != null && current.isCollapsible() && !isExpanded(current)) return;
        if (value.isJsonObject()) {
            for (java.util.Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                if (technicalField(entry.getKey())) continue;
                path.add(entry.getKey());
                flatten(entry.getValue(), path, humanize(entry.getKey()), output, depth + 1);
                path.remove(path.size() - 1);
            }
        } else {
            JsonArray array = value.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                path.add(Integer.valueOf(i));
                flatten(array.get(i), path,
                        arrayEntryLabel(current == null ? "" : current.key(), array, i),
                        output, depth + 1);
                path.remove(path.size() - 1);
            }
        }
    }

    private void removeArrayElement(Node selected) {
        for (int i = selected.path.size() - 1; i >= 0; i--) if (selected.path.get(i) instanceof Integer) {
            JsonElement parent = at(record, selected.path.subList(0, i));
            int index = ((Integer) selected.path.get(i)).intValue();
            if (parent != null && parent.isJsonArray() && index < parent.getAsJsonArray().size()) parent.getAsJsonArray().remove(index);
            return;
        }
    }

    private Node selected() {
        return selectedIndex >= 0 && selectedIndex < nodes.size() ? nodes.get(selectedIndex) : null;
    }

    private static String pickerKind(Node node) {
        if (node == null || node.value == null || !node.value.isJsonPrimitive()
                || node.value.getAsJsonPrimitive().isBoolean()) return null;
        String key = node.key();
        if ("skin".equals(key)) return "skin";
        if ("id".equals(key) && node.hasAncestor("jutsus")) return "jutsu";
        if ("templateId".equals(key)) return "template";
        if ("pathId".equals(key) || "linkedTarget".equals(key)) return "path";
        if ("bossId".equals(key)) return "boss";
        if ("nextQuestId".equals(key) || node.hasAncestor("prerequisites")) return "quest";
        if (node.hasAncestor("equipment")
                && ("mainHand".equals(key) || "offHand".equals(key) || "head".equals(key)
                || "chest".equals(key) || "legs".equals(key) || "feet".equals(key))) {
            return "item:" + key;
        }
        if ("item".equals(key)) return "item";
        if ("type".equals(key) && node.hasAncestor("steps")) return "objective";
        if ("aggression".equals(key)) return "aggression";
        if ("role".equals(key)) return "role";
        if ("mode".equals(key)) return "dungeonMode";
        if ("rewardDelivery".equals(key)) return "rewardDelivery";
        if ("village".equals(key) || node.hasAncestor("friendlyVillages")
                || node.hasAncestor("hostileVillages")) return "village";
        if ("rank".equals(key)) return "rank";
        if ("speaker".equals(key)) return "speaker";
        return null;
    }

    private static String pickerLabel(String kind) {
        if ("jutsu".equals(kind)) return text("gui.rebornaddon.world.choose_jutsu", "Choose Jutsu");
        if ("skin".equals(kind)) return text("gui.rebornaddon.world.choose_skin", "Choose Skin");
        if ("dungeonMode".equals(kind)) {
            return text("gui.rebornaddon.world.choose_dungeon_mode", "Choose Dungeon Type");
        }
        if (kind.startsWith("item:")) {
            return text("gui.rebornaddon.world.choose_equipment", "Choose %s Equipment",
                    humanize(kind.substring("item:".length())));
        }
        if ("rewardDelivery".equals(kind)) {
            return text("gui.rebornaddon.world.choose_reward_delivery", "Choose Reward Delivery");
        }
        return text("gui.rebornaddon.world.choose_value", "Choose %s", humanize(kind));
    }

    void applyCatalogValue(String kind, String id, String name, String skin, boolean slim, int meta) {
        if ("skin".equals(kind)) {
            Node selected = selected();
            if (selected != null && "skin".equals(selected.key())) {
                selected.replace(record, new JsonPrimitive(skin));
                selected.setSiblingBoolean(record, "slim", slim);
            } else {
                record.addProperty("skin", skin);
                record.addProperty("slim", slim);
            }
            if (value(record, "name").isEmpty() && name != null) record.addProperty("name", name);
            rebuildNodes();
        } else {
            Node selected = selected();
            if (selected != null) {
                String path = selected.pathText();
                String selectedKey = "";
                if ("jutsu".equals(kind) && selected.value != null && selected.value.isJsonArray()) {
                    JsonObject jutsu = defaultArrayElement("jutsus").getAsJsonObject();
                    jutsu.addProperty("id", id);
                    JsonArray array = selected.value.getAsJsonArray();
                    array.add(jutsu);
                    expandedObjects.add(selected.pathKey());
                    selectedKey = selected.pathKey() + "/[" + (array.size() - 1) + "]";
                    expandedArrays.put(selected.pathKey(), selectedKey);
                } else {
                    selected.replace(record, new JsonPrimitive(id));
                    if (kind.startsWith("item")) selected.setSiblingNumber(record, meta);
                }
                if (selectedKey.isEmpty()) rebuildNodesKeeping(path);
                else rebuildNodesKeepingKey(selectedKey);
            }
        }
    }

    private static JsonElement defaultArrayElement(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        if (normalized.contains("jutsu")) return parse("{\"id\":\"\",\"damage\":-1,\"cooldownTicks\":100,\"durationTicks\":100,\"obeyCooldowns\":true,\"obeyGlobalRules\":true,\"power\":1,\"weight\":1,\"minimumRange\":2,\"maximumRange\":24}");
        if (normalized.contains("choice")) return parse("{\"id\":\"choice\",\"text\":\"\",\"nextPageId\":\"\"}");
        if (normalized.contains("dialogue") || normalized.equals("pages")) return parse("{\"id\":\"page\",\"speaker\":\"npc\",\"text\":\"\",\"choices\":[]}");
        if (normalized.contains("step")) return parse("{\"id\":\"step\",\"objective\":\"\",\"type\":\"travel\",\"dimension\":0,\"x\":0,\"y\":64,\"z\":0,\"radius\":8,\"spawnNpc\":false,\"spawnX\":0,\"spawnY\":64,\"spawnZ\":0,\"amount\":1,\"target\":\"\",\"pathId\":\"\",\"templateId\":\"\",\"consumeItems\":true,\"overrides\":{},\"dialogue\":[],\"requirements\":[],\"rewards\":[]}");
        if (normalized.contains("drop") || normalized.contains("reward") || normalized.contains("requirement") || normalized.contains("item")) return parse("{\"item\":\"minecraft:stone\",\"count\":1,\"meta\":0,\"nbt\":\"\",\"chance\":1,\"ignoreMeta\":false,\"ignoreNbt\":false}");
        if (normalized.contains("spawn")) return parse("{\"id\":\"spawn\",\"name\":\"Spawn\",\"templateId\":\"\",\"overrides\":{},\"dimension\":0,\"x\":0,\"y\":64,\"z\":0,\"activationRadius\":32,\"leashRadius\":48,\"respawnSeconds\":300,\"enabled\":true,\"count\":1,\"wave\":1,\"waveDelaySeconds\":0,\"drops\":[]}");
        if (normalized.contains("phase")) return parse("{\"id\":\"phase\",\"name\":\"Boss Phase\",\"healthPercent\":75,\"telegraph\":\"\",\"invulnerableSeconds\":0,\"jutsus\":[],\"summons\":[]}");
        if (normalized.contains("summon")) return parse("{\"templateId\":\"\",\"overrides\":{},\"count\":1,\"radius\":4}");
        if (normalized.equals("points")) return parse("{\"dimension\":0,\"x\":0,\"y\":64,\"z\":0,\"waitTicks\":0,\"speed\":0.85}");
        if (normalized.contains("location")) return parse("{\"dimension\":0,\"x\":0,\"y\":64,\"z\":0,\"weight\":1,\"activationRadius\":32}");
        if (normalized.contains("boss")) return parse("{\"bossId\":\"\",\"weight\":1}");
        return new JsonPrimitive("");
    }

    private static JsonObject defaultRecord(String type) {
        if ("templates".equals(type)) return defaultOverridesWithId();
        if ("quests".equals(type)) return object("{\"id\":\"new-quest\",\"title\":\"New Quest\",\"category\":\"Missions\",\"rank\":\"D\",\"village\":\"\",\"description\":\"\",\"completionText\":\"\",\"completionNpc\":\"\",\"enabled\":true,\"repeatable\":false,\"randomReward\":false,\"rewardDelivery\":\"direct\",\"rewardExperience\":0,\"rewardLevels\":0,\"nextQuestId\":\"\",\"prerequisites\":[],\"rewardCommands\":[],\"rewards\":[],\"rewardMail\":{\"sender\":\"\",\"subject\":\"\",\"body\":\"\",\"items\":[]},\"steps\":[]}");
        if ("actors".equals(type)) return object("{\"id\":\"new-actor\",\"name\":\"Placed NPC\",\"templateId\":\"\",\"overrides\":{},\"dimension\":0,\"x\":0,\"y\":64,\"z\":0,\"activationRadius\":48,\"leashRadius\":64,\"respawnSeconds\":0,\"enabled\":true,\"drops\":[]}");
        if ("bosses".equals(type)) return object("{\"id\":\"new-boss\",\"name\":\"New Boss\",\"templateId\":\"\",\"overrides\":{},\"dimension\":0,\"x\":0,\"y\":64,\"z\":0,\"activationRadius\":32,\"leashRadius\":64,\"arenaRadius\":64,\"respawnSeconds\":3600,\"enabled\":true,\"worldBoss\":false,\"minimumContribution\":15,\"poolId\":\"\",\"healthPerAdditionalPlayer\":0.35,\"maximumScaledPlayers\":8,\"enrageSeconds\":0,\"enrageDamageMultiplier\":1.5,\"enrageSpeedMultiplier\":1.15,\"phases\":[],\"drops\":[]}");
        if ("dungeons".equals(type)) return object("{\"id\":\"new-dungeon\",\"name\":\"New Dungeon\",\"enabled\":true,\"mode\":\"waves\",\"dimension\":0,\"x\":0,\"y\":64,\"z\":0,\"activationRadius\":96,\"resetSeconds\":900,\"announceCompletion\":false,\"spawns\":[]}");
        if ("paths".equals(type)) return object("{\"id\":\"new-path\",\"name\":\"NPC Path\",\"loop\":true,\"points\":[]}");
        if ("worldBossPools".equals(type)) return object("{\"id\":\"new-pool\",\"name\":\"World Boss Pool\",\"enabled\":false,\"intervalSeconds\":86400,\"bosses\":[],\"locations\":[]}");
        return object("{\"id\":\"new-skin\",\"name\":\"New Skin\",\"skin\":\"\",\"slim\":false}");
    }

    private static void ensureEditorSchema(String type, JsonObject record) {
        if ("quests".equals(type) && !record.has("rewardDelivery")) {
            record.addProperty("rewardDelivery", "direct");
        }
        if ("templates".equals(type)) {
            if (!record.has("chakraControl")) record.addProperty("chakraControl", false);
            if (!record.has("chakraWaterWalking")) record.addProperty("chakraWaterWalking", false);
            if (!record.has("chakraWallClimbing")) record.addProperty("chakraWallClimbing", false);
        }
    }

    private static JsonObject defaultOverrides() {
        return object("{\"name\":\"Quest NPC\",\"skin\":\"\",\"slim\":false,\"health\":20,\"meleeDamage\":2,\"damageReduction\":0,\"projectileDamageReduction\":0,\"magicDamageReduction\":0,\"explosionDamageReduction\":0,\"knockbackResistance\":0,\"attackKnockback\":0,\"fireImmune\":false,\"invulnerable\":false,\"canDrown\":true,\"takesFallDamage\":true,\"pathAroundWater\":true,\"chakraControl\":false,\"chakraWaterWalking\":false,\"chakraWallClimbing\":false,\"canOpenDoors\":false,\"stationary\":false,\"retaliates\":true,\"assistsAllies\":true,\"immuneToPoison\":false,\"immuneToWither\":false,\"immuneToNegativeEffects\":false,\"ignoresWebs\":false,\"burnsInSun\":false,\"attacksInvisible\":false,\"requiresLineOfSight\":true,\"sprintsInCombat\":false,\"hostile\":false,\"aggression\":\"passive\",\"followRange\":24,\"moveSpeed\":0.28,\"wanderRadius\":12,\"meleeReach\":0,\"regenerationPerSecond\":0,\"combatRegenerationPerSecond\":0,\"meleeIntervalTicks\":20,\"role\":\"none\",\"linkedTarget\":\"\",\"faction\":\"neutral\",\"ambientSound\":\"\",\"hurtSound\":\"\",\"deathSound\":\"\",\"equipment\":{\"mainHand\":\"\",\"mainHandMeta\":0,\"mainHandNbt\":\"\",\"offHand\":\"\",\"offHandMeta\":0,\"offHandNbt\":\"\",\"head\":\"\",\"headMeta\":0,\"headNbt\":\"\",\"chest\":\"\",\"chestMeta\":0,\"chestNbt\":\"\",\"legs\":\"\",\"legsMeta\":0,\"legsNbt\":\"\",\"feet\":\"\",\"feetMeta\":0,\"feetNbt\":\"\"},\"jutsus\":[],\"friendlyVillages\":[],\"hostileVillages\":[],\"friendlyFactions\":[],\"hostileFactions\":[]}");
    }

    private static JsonObject defaultOverridesWithId() {
        JsonObject value = defaultOverrides();
        value.addProperty("id", "new-npc");
        value.addProperty("name", "New NPC");
        return value;
    }

    private static JsonElement parse(String value) { return new JsonParser().parse(value); }
    private static JsonObject object(String value) { return parse(value).getAsJsonObject(); }

    private static JsonElement at(JsonObject root, List<Object> path) {
        JsonElement current = root;
        for (Object part : path) {
            if (part instanceof String && current.isJsonObject()) current = current.getAsJsonObject().get((String) part);
            else if (part instanceof Integer && current.isJsonArray()) current = current.getAsJsonArray().get(((Integer) part).intValue());
            else return JsonNull.INSTANCE;
            if (current == null) return JsonNull.INSTANCE;
        }
        return current;
    }

    private static String fieldDescription(Node node) {
        if (node.value == null || node.value.isJsonNull()) return "Empty";
        if (node.value.isJsonArray()) return "Collection";
        if (node.value.isJsonObject()) return "rewardMail".equals(node.key())
                ? "Optional mail message used only when reward delivery is set to Mail." : "Group";
        JsonPrimitive primitive = node.value.getAsJsonPrimitive();
        if (primitive.isBoolean()) return booleanDescription(node.key());
        if (!primitive.isNumber()) return textDescription(node.key());
        String key = node.key();
        if ("health".equals(key)) return "Health points; 2 points equal one player heart.";
        if (key.toLowerCase(Locale.ROOT).contains("damagereduction")) return "0 to 0.95; 0.25 reduces this damage by 25%.";
        if ("knockbackResistance".equals(key)) return "0 to 1; 1 prevents all incoming knockback.";
        if ("attackKnockback".equals(key)) return "Extra knockback strength applied by melee hits; 0 disables it.";
        if ("meleeDamage".equals(key) || "damage".equals(key)) return "Damage points dealt per successful hit; 2 points equal one heart.";
        if (key.toLowerCase(Locale.ROOT).contains("radius") || "followRange".equals(key)
                || "minimumRange".equals(key) || "maximumRange".equals(key)) return "Distance in blocks.";
        if (key.toLowerCase(Locale.ROOT).endsWith("ticks") || "waitTicks".equals(key)) return "Duration in server ticks; 20 ticks equal one second.";
        if (key.toLowerCase(Locale.ROOT).endsWith("seconds")) return "Duration in real time.";
        if ("chance".equals(key)) return "Drop chance from 0 to 1; 0.25 means 25%.";
        if ("moveSpeed".equals(key) || "speed".equals(key)) return "Movement speed multiplier; typical player-like speed is about 0.28.";
        if ("count".equals(key) || "amount".equals(key) || "weight".equals(key)) return "Whole-number quantity or selection weight.";
        return "Numeric server value for " + humanize(key).toLowerCase(Locale.ROOT) + ".";
    }

    private static String booleanDescription(String key) {
        if ("canDrown".equals(key)) return "Enabled allows drowning damage when the NPC runs out of air.";
        if ("takesFallDamage".equals(key)) return "Enabled allows normal fall damage.";
        if ("pathAroundWater".equals(key)) return "Enabled makes ground pathfinding avoid water where possible.";
        if ("chakraControl".equals(key)) return "Master switch for this NPC's chakra-assisted movement.";
        if ("chakraWaterWalking".equals(key)) return "Allows this NPC to walk on the water surface.";
        if ("chakraWallClimbing".equals(key)) return "Allows this NPC to climb solid walls while pathing forward.";
        if ("obeyCooldowns".equals(key)) return "Enabled makes this NPC jutsu wait for its configured cooldown.";
        if ("obeyGlobalRules".equals(key)) return "Enabled applies the server's jutsu enable, cost, and damage rules.";
        return "On / Off setting for " + humanize(key).toLowerCase(Locale.ROOT) + ".";
    }

    private static String textDescription(String key) {
        if ("skin".equals(key)) return "Packaged mod resource or approved HTTPS skin URL.";
        if ("id".equals(key)) return "Stable internal identifier; use lowercase words separated by hyphens.";
        if ("templateId".equals(key)) return "Reusable NPC base used by this encounter.";
        if ("rewardDelivery".equals(key)) return "Deliver rewards directly or through the player's mailbox.";
        return "Text value for " + humanize(key).toLowerCase(Locale.ROOT) + ".";
    }

    private static boolean technicalField(String key) {
        String lower = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return "meta".equals(lower) || "nbt".equals(lower) || "ignoremeta".equals(lower)
                || "ignorenbt".equals(lower) || lower.endsWith("meta") || lower.endsWith("nbt");
    }

    private static void drawPreviewEntity(int x, int y, int scale, float yaw, float pitch,
                                          EntityMissionNpc entity) {
        GlStateManager.enableColorMaterial();
        GlStateManager.pushMatrix();
        float oldRenderYaw = entity.renderYawOffset;
        float oldYaw = entity.rotationYaw;
        float oldPitch = entity.rotationPitch;
        float oldHeadYaw = entity.rotationYawHead;
        float oldPreviousHeadYaw = entity.prevRotationYawHead;
        try {
            GlStateManager.translate((float) x, (float) y, 50.0F);
            GlStateManager.scale((float) -scale, (float) scale, (float) scale);
            GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
            GlStateManager.rotate(yaw, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(pitch, 1.0F, 0.0F, 0.0F);
            RenderHelper.enableStandardItemLighting();
            entity.renderYawOffset = 0.0F;
            entity.rotationYaw = 0.0F;
            entity.rotationPitch = 0.0F;
            entity.rotationYawHead = 0.0F;
            entity.prevRotationYawHead = 0.0F;
            RenderManager manager = net.minecraft.client.Minecraft.getMinecraft().getRenderManager();
            manager.setPlayerViewY(180.0F);
            manager.setRenderShadow(false);
            manager.renderEntity(entity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, false);
            manager.setRenderShadow(true);
        } finally {
            entity.renderYawOffset = oldRenderYaw;
            entity.rotationYaw = oldYaw;
            entity.rotationPitch = oldPitch;
            entity.rotationYawHead = oldHeadYaw;
            entity.prevRotationYawHead = oldPreviousHeadYaw;
            GlStateManager.popMatrix();
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableRescaleNormal();
            GlStateManager.setActiveTexture(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit);
            GlStateManager.disableTexture2D();
            GlStateManager.setActiveTexture(net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit);
        }
    }

    private static String value(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static String humanize(String value) {
        String clean = value == null ? "" : value;
        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.endsWith("seconds") || lower.endsWith("ticks")) {
            int suffix = lower.endsWith("seconds") ? "seconds".length() : "ticks".length();
            String base = clean.substring(0, clean.length() - suffix);
            String baseLower = base.toLowerCase(Locale.ROOT);
            if (baseLower.contains("interval") || baseLower.contains("duration")
                    || baseLower.contains("cooldown") || baseLower.contains("respawn")
                    || baseLower.contains("delay") || baseLower.startsWith("wait")) {
                clean = baseLower.endsWith("time") ? base : base + "Time";
            }
        }
        String spaced = clean.replace('_', ' ').replace('-', ' ').replaceAll("([a-z])([A-Z])", "$1 $2");
        return spaced.isEmpty() ? "Entry" : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static String text(String key, String fallback, Object... arguments) {
        return ClientLocalization.format(key, fallback, arguments);
    }
    private static ThemedButton button(int id, int x, int y, int width, int height, String label, int base, int hover) {
        return new ThemedButton(id, x, y, Math.max(12, width), height, label, base, hover, Theme.BUTTON_TEXT);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    private static final class Node {
        private final List<Object> path;
        private final String display;
        private JsonElement value;
        private boolean expanded;
        private Node(List<Object> path, String display, JsonElement value) { this.path = path; this.display = display; this.value = value; }
        private String label() {
            String prefix = "";
            for (Object part : path) if (part instanceof Integer) prefix += "  ";
            String suffix = value != null && value.isJsonArray() ? "  (" + value.getAsJsonArray().size() + ")" : "";
            String disclosure = isCollapsible() ? (expanded ? "v " : "> ") : "";
            return prefix + disclosure + display + suffix;
        }
        private String pathText() {
            StringBuilder result = new StringBuilder();
            for (Object part : path) {
                if (result.length() > 0) result.append(" / ");
                result.append(part instanceof String ? humanize((String) part) : Integer.toString(((Integer) part).intValue() + 1));
            }
            return result.toString();
        }
        private String key() {
            for (int i = path.size() - 1; i >= 0; i--) if (path.get(i) instanceof String) return (String) path.get(i);
            return "entry";
        }
        private String rootKey() {
            for (Object part : path) if (part instanceof String) return (String) part;
            return "";
        }
        private int removableArrayIndex() {
            for (int i = path.size() - 1; i >= 0; i--) if (path.get(i) instanceof Integer) return ((Integer) path.get(i)).intValue();
            return -1;
        }
        private boolean isArrayElementObject() {
            return value != null && value.isJsonObject() && !path.isEmpty()
                    && path.get(path.size() - 1) instanceof Integer;
        }
        private boolean isCollapsible() {
            if (value == null || !value.isJsonObject()) return false;
            if (isArrayElementObject()) return true;
            String key = key();
            return "rewardMail".equals(key) || "equipment".equals(key) || "overrides".equals(key);
        }
        private String pathKey() {
            StringBuilder result = new StringBuilder();
            for (Object part : path) {
                if (part instanceof Integer) result.append("/[").append(part).append(']');
                else result.append('/').append(part);
            }
            return result.toString();
        }
        private String arrayParentKey() {
            if (!isArrayElementObject()) return "";
            String key = pathKey();
            int slash = key.lastIndexOf("/[");
            return slash < 0 ? "" : key.substring(0, slash);
        }
        private boolean hasAncestor(String name) {
            for (Object part : path) if (part instanceof String && name.equals(part)) return true;
            return false;
        }
        private void replace(JsonObject root, JsonElement replacement) {
            if (path.isEmpty()) return;
            JsonElement parent = at(root, path.subList(0, path.size() - 1));
            Object last = path.get(path.size() - 1);
            if (last instanceof String && parent.isJsonObject()) parent.getAsJsonObject().add((String) last, replacement);
            else if (last instanceof Integer && parent.isJsonArray()) parent.getAsJsonArray().set(((Integer) last).intValue(), replacement);
            value = replacement;
        }
        private JsonObject coordinateObject(JsonObject root) {
            for (int end = path.size(); end >= 0; end--) {
                JsonElement candidate = at(root, path.subList(0, end));
                if (candidate != null && candidate.isJsonObject()) {
                    JsonObject object = candidate.getAsJsonObject();
                    if (object.has("x") && object.has("y") && object.has("z")) return object;
                }
            }
            return root.has("x") && root.has("y") && root.has("z") ? root : null;
        }

        private JsonObject configurationObject(JsonObject root) {
            for (int end = path.size(); end >= 0; end--) {
                JsonElement candidate = at(root, path.subList(0, end));
                if (candidate != null && candidate.isJsonObject()
                        && candidate.getAsJsonObject().has("templateId")) {
                    return candidate.getAsJsonObject();
                }
            }
            return null;
        }

        private void setSiblingNumber(JsonObject root, int number) {
            if (path.isEmpty()) return;
            JsonElement parent = at(root, path.subList(0, path.size() - 1));
            if (parent == null || !parent.isJsonObject()) return;
            String key = key();
            JsonObject object = parent.getAsJsonObject();
            if (object.has("meta")) object.addProperty("meta", number);
            else if (object.has(key + "Meta")) object.addProperty(key + "Meta", number);
        }

        private void setSiblingBoolean(JsonObject root, String sibling, boolean state) {
            if (path.isEmpty()) return;
            JsonElement parent = at(root, path.subList(0, path.size() - 1));
            if (parent != null && parent.isJsonObject()) {
                parent.getAsJsonObject().addProperty(sibling, state);
            }
        }
    }
}
