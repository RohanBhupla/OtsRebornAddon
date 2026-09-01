package net.rebornaddon.jutsu.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.client.RebornScaledGuiScreen;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;
import net.rebornaddon.jutsu.JutsuConfigurationService;
import net.rebornaddon.jutsu.JutsuEffectRules;
import net.rebornaddon.jutsu.JutsuValueParser;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GuiJutsuEffectEditor extends RebornScaledGuiScreen {
    private static final int ROW_BASE = 1000;
    private static final int PREVIOUS = 2000;
    private static final int NEXT = 2001;
    private static final int BEHAVIOR = 2002;
    private static final int EXTRA = 2003;
    private static final int APPLY = 2004;
    private static final int SAVE = 2005;
    private static final int DONE = 2006;

    private final GuiScreen parent;
    private final String jutsuId;
    private final String jutsuName;
    private final List<EffectEntry> effects = new ArrayList<EffectEntry>();
    private final Map<String, JutsuEffectRules.Rule> rules =
            new LinkedHashMap<String, JutsuEffectRules.Rule>();
    private final Map<String, ExtraEffect> extras = new LinkedHashMap<String, ExtraEffect>();
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int page;
    private int rowsPerPage;
    private int selected;
    private int behavior = JutsuEffectRules.INHERIT;
    private boolean addExtra;
    private GuiTextField durationMultiplier;
    private GuiTextField strengthAdjustment;
    private GuiTextField hours;
    private GuiTextField minutes;
    private GuiTextField seconds;
    private GuiTextField extraStrength;

    public GuiJutsuEffectEditor(GuiScreen parent, String jutsuId, String jutsuName,
                                String ruleData, String extraData) {
        this.parent = parent;
        this.jutsuId = jutsuId;
        this.jutsuName = jutsuName;
        rules.putAll(JutsuEffectRules.parse(ruleData));
        parseExtras(extraData);
        for (Potion potion : ForgeRegistries.POTIONS.getValuesCollection()) {
            ResourceLocation id = potion.getRegistryName();
            if (id == null || !potion.isBadEffect()) continue;
            String name;
            try { name = I18n.format(potion.getName()); }
            catch (RuntimeException ignored) { name = id.toString(); }
            if (name == null || name.isEmpty() || name.equals(potion.getName())) name = readable(id);
            effects.add(new EffectEntry(id.toString(), name));
        }
        Collections.sort(effects, Comparator.comparing(EffectEntry::sortKey));
    }

    @Override
    public void initGui() {
        panelWidth = Math.max(390, Math.min(690, width - 18));
        panelHeight = Math.max(292, Math.min(410, height - 18));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        rowsPerPage = Math.max(5, (panelHeight - 104) / 23);
        int rightX = left + panelWidth * 43 / 100 + 13;
        int rightWidth = left + panelWidth - rightX - 14;
        int half = Math.max(54, (rightWidth - 4) / 2);
        durationMultiplier = field(3100, rightX, top + 108, half, "1");
        strengthAdjustment = field(3101, rightX + half + 4, top + 108,
                rightWidth - half - 4, "0");
        int gap = 4;
        int timeWidth = Math.max(112, rightWidth * 63 / 100);
        int fieldWidth = Math.max(30, (timeWidth - gap * 2) / 3);
        hours = field(3102, rightX, top + 183, fieldWidth, "0");
        minutes = field(3103, rightX + fieldWidth + gap, top + 183, fieldWidth, "0");
        seconds = field(3104, rightX + (fieldWidth + gap) * 2, top + 183,
                timeWidth - fieldWidth * 2 - gap * 2, "0");
        extraStrength = field(3105, rightX + timeWidth + gap, top + 183,
                rightWidth - timeWidth - gap, "0");
        loadSelection();
        rebuild();
    }

    private void rebuild() {
        buttonList.clear();
        int split = left + panelWidth * 43 / 100;
        int pages = Math.max(1, (effects.size() + rowsPerPage - 1) / rowsPerPage);
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * rowsPerPage;
        int end = Math.min(effects.size(), start + rowsPerPage);
        for (int i = start; i < end; i++) {
            EffectEntry entry = effects.get(i);
            ThemedButton row = button(ROW_BASE + i - start, left + 12,
                    top + 55 + (i - start) * 23, split - left - 20, 20,
                    entry.name, Theme.TAB_INACTIVE_BG, Theme.PURPLE_LIGHT);
            row.setTextAlignment(ThemedButton.Alignment.LEFT);
            row.setSelected(i == selected);
            buttonList.add(row);
        }
        int rightX = split + 13;
        int rightWidth = left + panelWidth - rightX - 14;
        buttonList.add(button(BEHAVIOR, rightX, top + 72, rightWidth, 21,
                behaviorLabel(), behavior == JutsuEffectRules.DISABLED
                        ? Theme.RANKED_RED_DARK : Theme.TAB_INACTIVE_BG,
                behavior == JutsuEffectRules.DISABLED ? Theme.RANKED_RED : Theme.TEAL));
        buttonList.add(button(EXTRA, rightX, top + 139, rightWidth, 21,
                addExtra ? text("gui.rebornaddon.effects.extra_on", "Added by this jutsu")
                        : text("gui.rebornaddon.effects.extra_off", "Not added separately"),
                addExtra ? Theme.TEAL_DARK : Theme.TAB_INACTIVE_BG,
                addExtra ? Theme.TEAL : Theme.NEUTRAL));
        buttonList.add(button(APPLY, rightX, top + panelHeight - 54,
                Math.max(70, (rightWidth - 4) / 2), 21,
                text("gui.rebornaddon.effects.apply", "Apply Effect"), Theme.TEAL_DARK, Theme.TEAL));
        buttonList.add(button(SAVE, rightX + Math.max(70, (rightWidth - 4) / 2) + 4,
                top + panelHeight - 54,
                rightWidth - Math.max(70, (rightWidth - 4) / 2) - 4, 21,
                text("gui.rebornaddon.effects.save", "Save All"), Theme.PURPLE_DARK, Theme.PURPLE_LIGHT));
        int bottom = top + panelHeight - 28;
        ThemedButton previous = button(PREVIOUS, left + 12, bottom, 30, 19, "<",
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL);
        previous.enabled = page > 0;
        buttonList.add(previous);
        ThemedButton next = button(NEXT, left + 46, bottom, 30, 19, ">",
                Theme.TAB_INACTIVE_BG, Theme.NEUTRAL);
        next.enabled = page + 1 < pages;
        buttonList.add(next);
        buttonList.add(button(DONE, rightX, bottom, rightWidth, 19,
                text("gui.rebornaddon.effects.done", "Done"), Theme.TAB_INACTIVE_BG, Theme.NEUTRAL));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == PREVIOUS || button.id == NEXT) {
            page += button.id == PREVIOUS ? -1 : 1;
            rebuild();
            return;
        }
        if (button.id >= ROW_BASE && button.id < ROW_BASE + rowsPerPage) {
            applySelection(false);
            int index = page * rowsPerPage + button.id - ROW_BASE;
            if (index >= 0 && index < effects.size()) selected = index;
            loadSelection();
            rebuild();
            return;
        }
        if (button.id == BEHAVIOR) {
            behavior = behavior == JutsuEffectRules.INHERIT ? JutsuEffectRules.ENABLED
                    : behavior == JutsuEffectRules.ENABLED ? JutsuEffectRules.DISABLED
                    : JutsuEffectRules.INHERIT;
            rebuild();
        } else if (button.id == EXTRA) {
            addExtra = !addExtra;
            rebuild();
        } else if (button.id == APPLY) {
            applySelection(true);
        } else if (button.id == SAVE) {
            if (applySelection(true)) saveAll();
        } else if (button.id == DONE) {
            mc.displayGuiScreen(parent);
        }
    }

    private boolean applySelection(boolean report) {
        EffectEntry effect = selectedEffect();
        if (effect == null) return false;
        try {
            double multiplier = decimal(durationMultiplier.getText(), 0.0D, 1000.0D);
            int adjustment = integer(strengthAdjustment.getText(), -255, 255);
            JutsuEffectRules.Rule rule = new JutsuEffectRules.Rule(behavior, multiplier, adjustment);
            if (behavior == JutsuEffectRules.INHERIT && multiplier == 1.0D && adjustment == 0) {
                rules.remove(effect.id);
            } else rules.put(effect.id, rule);
            if (addExtra) {
                long ticks = durationTicks();
                if (ticks <= 0L) throw new IllegalArgumentException("Extra effect time must be greater than zero.");
                extras.put(effect.id, new ExtraEffect(integer(extraStrength.getText(), 0, 255), ticks));
            } else extras.remove(effect.id);
            if (report && mc.player != null) mc.player.sendStatusMessage(
                    new net.minecraft.util.text.TextComponentString("Effect settings prepared."), true);
            return true;
        } catch (IllegalArgumentException exception) {
            if (report && mc.player != null) mc.player.sendStatusMessage(
                    new net.minecraft.util.text.TextComponentString(
                            net.minecraft.util.text.TextFormatting.RED + exception.getMessage()), true);
            return false;
        }
    }

    private void saveAll() {
        StringBuilder encodedRules = new StringBuilder();
        for (Map.Entry<String, JutsuEffectRules.Rule> entry : rules.entrySet()) {
            if (encodedRules.length() > 0) encodedRules.append(';');
            JutsuEffectRules.Rule rule = entry.getValue();
            encodedRules.append(entry.getKey()).append('|').append(rule.state()).append('|')
                    .append(BigDecimal.valueOf(rule.durationMultiplier()).stripTrailingZeros().toPlainString())
                    .append('|').append(rule.amplifierAdjustment());
        }
        StringBuilder encodedExtras = new StringBuilder();
        for (Map.Entry<String, ExtraEffect> entry : extras.entrySet()) {
            if (encodedExtras.length() > 0) encodedExtras.append(',');
            encodedExtras.append(entry.getKey()).append('|').append(entry.getValue().amplifier)
                    .append('|').append(entry.getValue().ticks).append('t');
        }
        RebornAddonNetwork.sendJutsuAdminAction(0, jutsuId, "negative-effect-rules",
                encodedRules.toString());
        RebornAddonNetwork.sendJutsuAdminAction(0, jutsuId, "additional-negative-effects",
                encodedExtras.toString());
    }

    private void loadSelection() {
        EffectEntry effect = selectedEffect();
        JutsuEffectRules.Rule rule = effect == null ? null : rules.get(effect.id);
        behavior = rule == null ? JutsuEffectRules.INHERIT : rule.state();
        if (durationMultiplier != null) durationMultiplier.setText(rule == null ? "1"
                : BigDecimal.valueOf(rule.durationMultiplier()).stripTrailingZeros().toPlainString());
        if (strengthAdjustment != null) strengthAdjustment.setText(rule == null ? "0"
                : Integer.toString(rule.amplifierAdjustment()));
        ExtraEffect extra = effect == null ? null : extras.get(effect.id);
        addExtra = extra != null;
        setTime(extra == null ? 0L : extra.ticks);
        if (extraStrength != null) extraStrength.setText(extra == null ? "0" : Integer.toString(extra.amplifier));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (durationMultiplier.textboxKeyTyped(typedChar, keyCode)
                || strengthAdjustment.textboxKeyTyped(typedChar, keyCode)
                || hours.textboxKeyTyped(typedChar, keyCode)
                || minutes.textboxKeyTyped(typedChar, keyCode)
                || seconds.textboxKeyTyped(typedChar, keyCode)
                || extraStrength.textboxKeyTyped(typedChar, keyCode)) return;
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        durationMultiplier.mouseClicked(mouseX, mouseY, mouseButton);
        strengthAdjustment.mouseClicked(mouseX, mouseY, mouseButton);
        hours.mouseClicked(mouseX, mouseY, mouseButton);
        minutes.mouseClicked(mouseX, mouseY, mouseButton);
        seconds.mouseClicked(mouseX, mouseY, mouseButton);
        extraStrength.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        durationMultiplier.updateCursorCounter();
        strengthAdjustment.updateCursorCounter();
        hours.updateCursorCounter();
        minutes.updateCursorCounter();
        seconds.updateCursorCounter();
        extraStrength.updateCursorCounter();
    }

    @Override
    protected void drawScaledScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        GuiChrome.frame(left, top, panelWidth, panelHeight, Theme.PURPLE_LIGHT);
        GuiChrome.header(left + 3, top + 5, panelWidth - 6, 38, Theme.PURPLE_LIGHT);
        fontRenderer.drawString(text("gui.rebornaddon.effects.title", "Jutsu Effect Rules"),
                left + 14, top + 16, Theme.TEXT_LIGHT);
        String name = fontRenderer.trimStringToWidth(jutsuName, panelWidth - 190);
        fontRenderer.drawString(name, left + panelWidth - 14 - fontRenderer.getStringWidth(name),
                top + 16, Theme.TEXT_MUTED);
        int split = left + panelWidth * 43 / 100;
        GuiChrome.section(left + 7, top + 47, split - left - 10, panelHeight - 82, Theme.PURPLE_LIGHT);
        GuiChrome.section(split + 6, top + 47, left + panelWidth - split - 13,
                panelHeight - 82, Theme.PURPLE_LIGHT);
        int x = split + 13;
        EffectEntry effect = selectedEffect();
        fontRenderer.drawString(effect == null ? "" : effect.name, x, top + 51, Theme.TEXT_LIGHT);
        fontRenderer.drawString(text("gui.rebornaddon.effects.behavior", "Existing effect behavior"),
                x, top + 61, Theme.TEXT_MUTED);
        fontRenderer.drawString(text("gui.rebornaddon.effects.duration", "Duration multiplier"),
                durationMultiplier.x, top + 97, Theme.TEXT_MUTED);
        durationMultiplier.drawTextBox();
        fontRenderer.drawString(text("gui.rebornaddon.effects.strength", "Strength adjustment"),
                strengthAdjustment.x, top + 97, Theme.TEXT_MUTED);
        strengthAdjustment.drawTextBox();
        fontRenderer.drawString(text("gui.rebornaddon.effects.extra", "Optional additional effect"),
                x, top + 128, Theme.TEXT_MUTED);
        drawFieldLabel(hours, text("gui.rebornaddon.time.hours", "Hours"));
        drawFieldLabel(minutes, text("gui.rebornaddon.time.minutes", "Minutes"));
        drawFieldLabel(seconds, text("gui.rebornaddon.time.seconds", "Seconds"));
        hours.drawTextBox();
        minutes.drawTextBox();
        seconds.drawTextBox();
        fontRenderer.drawString(text("gui.rebornaddon.effects.extra_strength", "Extra effect strength"),
                extraStrength.x, top + 172, Theme.TEXT_MUTED);
        extraStrength.drawTextBox();
        drawScaledControls(mouseX, mouseY, partialTicks);
    }

    private void drawFieldLabel(GuiTextField field, String label) {
        fontRenderer.drawString(label, field.x, top + 172, Theme.TEXT_MUTED);
    }

    private GuiTextField field(int id, int x, int y, int width, String value) {
        GuiTextField field = new GuiTextField(id, fontRenderer, x, y, Math.max(34, width), 18);
        field.setMaxStringLength(24);
        field.setText(value);
        return field;
    }

    private EffectEntry selectedEffect() {
        if (effects.isEmpty()) return null;
        selected = Math.max(0, Math.min(selected, effects.size() - 1));
        return effects.get(selected);
    }

    private String behaviorLabel() {
        if (behavior == JutsuEffectRules.ENABLED) return text("gui.rebornaddon.effects.enabled", "Enabled for this jutsu");
        if (behavior == JutsuEffectRules.DISABLED) return text("gui.rebornaddon.effects.disabled", "Disabled for this jutsu");
        return text("gui.rebornaddon.effects.inherit", "Use global jutsu setting");
    }

    private long durationTicks() {
        long total;
        try {
            total = Math.addExact(Math.addExact(Math.multiplyExact(number(hours), 72000L),
                    Math.multiplyExact(number(minutes), 1200L)), Math.multiplyExact(number(seconds), 20L));
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Effect time is too large.");
        }
        return total;
    }

    private void setTime(long ticks) {
        if (hours == null) return;
        long total = Math.max(0L, ticks / 20L);
        hours.setText(Long.toString(total / 3600L));
        minutes.setText(Long.toString(total % 3600L / 60L));
        seconds.setText(Long.toString(total % 60L));
    }

    private static long number(GuiTextField field) {
        try { return Math.max(0L, Long.parseLong(field.getText().trim())); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("Time fields must be whole numbers."); }
    }

    private static double decimal(String value, double minimum, double maximum) {
        try {
            double number = Double.parseDouble(value.trim());
            if (!Double.isFinite(number) || number < minimum || number > maximum) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Duration multiplier must be from 0 to 1000.");
        }
    }

    private static int integer(String value, int minimum, int maximum) {
        try {
            int number = Integer.parseInt(value.trim());
            if (number < minimum || number > maximum) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Effect strength is outside the allowed range.");
        }
    }

    private void parseExtras(String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) return;
        for (String entry : encoded.split(",")) {
            String[] fields = entry.trim().split("\\|", -1);
            if (fields.length != 3) continue;
            try {
                String id = fields[0].trim().toLowerCase(java.util.Locale.ROOT);
                int amplifier = Integer.parseInt(fields[1].trim());
                long ticks = JutsuValueParser.parseEffectDuration(fields[2].trim());
                if (ticks > 0L) extras.put(id, new ExtraEffect(amplifier, ticks));
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static String readable(ResourceLocation id) {
        String value = id.getResourcePath().replace('_', ' ').replace('-', ' ');
        return value.isEmpty() ? id.toString()
                : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String text(String key, String fallback, Object... arguments) {
        return ClientLocalization.format(key, fallback, arguments);
    }

    private static ThemedButton button(int id, int x, int y, int width, int height,
                                       String label, int base, int hover) {
        return new ThemedButton(id, x, y, Math.max(20, width), height, label,
                base, hover, Theme.BUTTON_TEXT);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    private static final class EffectEntry {
        private final String id;
        private final String name;
        private EffectEntry(String id, String name) { this.id = id; this.name = name; }
        private String sortKey() { return name.toLowerCase(java.util.Locale.ROOT) + '\n' + id; }
    }

    private static final class ExtraEffect {
        private final int amplifier;
        private final long ticks;
        private ExtraEffect(int amplifier, long ticks) { this.amplifier = amplifier; this.ticks = ticks; }
    }
}
