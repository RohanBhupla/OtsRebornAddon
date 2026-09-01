package net.rebornaddon.gui.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;
import net.rebornaddon.store.RyoCurrency;
import net.rebornaddon.store.StoreCatalog;
import net.rebornaddon.store.client.ClientStoreData;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StoreTab implements HubTab {
    private static final int CATEGORY_BASE = 600;
    private static final int CATEGORY_PREVIOUS = 590;
    private static final int CATEGORY_NEXT = 591;
    private static final int SUBCATEGORY_BASE = 620;
    private static final int SUBCATEGORY_PREVIOUS = 592;
    private static final int SUBCATEGORY_NEXT = 593;
    private static final int ITEM_BASE = 650;
    private static final int PAGE_PREVIOUS = 690;
    private static final int PAGE_NEXT = 691;
    private static final int BUY = 692;
    private static final int ARMOR_COLLECTION_BASE = 710;
    private static final int ARMOR_TIER_BASE = 720;

    private int category;
    private int categoryFirst;
    private int categorySlots;
    private int subcategory;
    private int subcategoryFirst;
    private int subcategorySlots;
    private int armorCollection;
    private int armorTier;
    private int page;
    private int selectedIndex;
    private int listWidth;
    private int listTop;
    private int rowsPerPage;
    private int builtRevision = -1;
    private long builtBalance = -1L;
    private int requestTicks;
    private List<ClientStoreData.Entry> visibleCategory = Collections.emptyList();

    @Override
    public String getTabName() {
        return ClientLocalization.format("gui.rebornaddon.tab.store", "Store");
    }

    @Override
    public int getTabColorActive() {
        return Theme.COPPER;
    }

    @Override
    public int getTabColorHover() {
        return Theme.COPPER_BRIGHT;
    }

    @Override
    public void buildButtons(List<GuiButton> buttons, int left, int top, int width, int height) {
        builtRevision = ClientStoreData.revision();
        builtBalance = currentBalance();
        buildCategoryButtons(buttons, left, top, width);

        boolean building = StoreCatalog.BUILDING.equals(activeCategory());
        boolean armor = StoreCatalog.ARMOR.equals(activeCategory());
        boolean locked = building && ClientStoreData.buildingLocked();
        int controlsHeight = 25;
        if (armor) {
            buildArmorCollectionButtons(buttons, left, top + 25, width);
            buildArmorTierButtons(buttons, left, top + 50, width);
            controlsHeight += 50;
        } else if ((building && !locked) || StoreCatalog.SCROLLS.equals(activeCategory())) {
            buildSubcategoryButtons(buttons, left, top + 25, width);
            controlsHeight += 25;
        }
        listTop = top + controlsHeight + 4;
        listWidth = Math.max(72, Math.min(252, width * 46 / 100));
        rowsPerPage = Math.max(2, Math.min(9, (height - controlsHeight - 31) / 25));
        String subcategoryFilter = armor ? activeArmorCollection() : activeSubcategory();
        visibleCategory = locked ? Collections.<ClientStoreData.Entry>emptyList()
                : filteredEntries(activeCategory(), subcategoryFilter, armor ? armorTier : -1);

        int pageCount = Math.max(1, (visibleCategory.size() + rowsPerPage - 1) / rowsPerPage);
        page = Math.max(0, Math.min(page, pageCount - 1));
        if (selectedIndex < 0 || selectedIndex >= visibleCategory.size()) {
            selectedIndex = visibleCategory.isEmpty() ? -1 : 0;
        }

        if (!locked) {
            int start = page * rowsPerPage;
            int end = Math.min(visibleCategory.size(), start + rowsPerPage);
            for (int i = start; i < end; i++) {
                ClientStoreData.Entry entry = visibleCategory.get(i);
                CatalogButton button = new CatalogButton(ITEM_BASE + i - start, left,
                        listTop + (i - start) * 24, listWidth, 21, entry);
                button.setSelected(i == selectedIndex);
                buttons.add(button);
            }
            int pagerY = top + height - 20;
            ThemedButton previous = smallButton(PAGE_PREVIOUS, left, pagerY, "<");
            previous.enabled = page > 0;
            buttons.add(previous);
            ThemedButton next = smallButton(PAGE_NEXT, left + listWidth - 24, pagerY, ">");
            next.enabled = page + 1 < pageCount;
            buttons.add(next);
        }

        ClientStoreData.Entry selected = selected();
        if (selected != null) {
            int detailLeft = left + listWidth + 9;
            int detailWidth = width - listWidth - 9;
            boolean affordable = Minecraft.getMinecraft().player != null
                    && (Minecraft.getMinecraft().player.capabilities.isCreativeMode
                    || builtBalance >= selected.price);
            boolean canBuy = selected.purchasable && affordable;
            String label = selected.purchasable
                    ? ClientLocalization.format("gui.rebornaddon.store.buy", "Buy - %s Ryo",
                    ClientStoreData.formatRyo(selected.price))
                    : ClientLocalization.format("gui.rebornaddon.store.unavailable", "Unavailable");
            ThemedButton buy = new ThemedButton(BUY, detailLeft + 10, top + height - 27,
                    Math.max(40, detailWidth - 20), 22, label,
                    canBuy ? Theme.SUCCESS : Theme.RANKED_RED_DARK,
                    canBuy ? Theme.SUCCESS_BRIGHT : Theme.DANGER, Theme.BUTTON_TEXT);
            buy.enabled = canBuy;
            buttons.add(buy);
        }
    }

    private void buildCategoryButtons(List<GuiButton> buttons, int left, int top, int width) {
        categorySlots = Math.max(1, Math.min(StoreCatalog.CATEGORIES.size(), (width - 48) / 66));
        if (category < categoryFirst) categoryFirst = category;
        if (category >= categoryFirst + categorySlots) categoryFirst = category - categorySlots + 1;
        categoryFirst = Math.max(0, Math.min(categoryFirst, StoreCatalog.CATEGORIES.size() - categorySlots));
        int buttonWidth = (width - 48 - Math.max(0, categorySlots - 1) * 3) / categorySlots;
        ThemedButton previous = smallButton(CATEGORY_PREVIOUS, left, top, "<");
        previous.enabled = categoryFirst > 0;
        buttons.add(previous);
        for (int slot = 0; slot < categorySlots; slot++) {
            int index = categoryFirst + slot;
            String categoryKey = StoreCatalog.CATEGORIES.get(index);
            ThemedButton button = new ThemedButton(CATEGORY_BASE + index,
                    left + 27 + slot * (buttonWidth + 3), top, buttonWidth, 20,
                    categoryName(categoryKey),
                    index == category ? Theme.COPPER : Theme.TAB_INACTIVE_BG,
                    index == category ? Theme.COPPER_BRIGHT : Theme.TAB_HOVER_BG,
                    Theme.BUTTON_TEXT);
            button.setSelected(index == category);
            button.enabled = ClientStoreData.scopeEnabled(StoreCatalog.categoryScope(categoryKey));
            buttons.add(button);
        }
        ThemedButton next = smallButton(CATEGORY_NEXT, left + width - 24, top, ">");
        next.enabled = categoryFirst + categorySlots < StoreCatalog.CATEGORIES.size();
        buttons.add(next);
    }

    private void buildSubcategoryButtons(List<GuiButton> buttons, int left, int top, int width) {
        List<String> categories = activeSubcategories();
        subcategorySlots = Math.max(1, Math.min(categories.size(), (width - 48) / 76));
        if (subcategory < subcategoryFirst) subcategoryFirst = subcategory;
        if (subcategory >= subcategoryFirst + subcategorySlots) {
            subcategoryFirst = subcategory - subcategorySlots + 1;
        }
        subcategoryFirst = Math.max(0, Math.min(subcategoryFirst,
                categories.size() - subcategorySlots));
        int buttonWidth = (width - 48 - Math.max(0, subcategorySlots - 1) * 3) / subcategorySlots;
        ThemedButton previous = smallButton(SUBCATEGORY_PREVIOUS, left, top, "<");
        previous.enabled = subcategoryFirst > 0;
        buttons.add(previous);
        for (int slot = 0; slot < subcategorySlots; slot++) {
            int index = subcategoryFirst + slot;
            ThemedButton button = new ThemedButton(SUBCATEGORY_BASE + index,
                    left + 27 + slot * (buttonWidth + 3), top, buttonWidth, 20,
                    subcategoryName(categories.get(index)),
                    index == subcategory ? Theme.GOLD_DARK : Theme.TAB_INACTIVE_BG,
                    index == subcategory ? Theme.GOLD : Theme.TAB_HOVER_BG, Theme.BUTTON_TEXT);
            button.setSelected(index == subcategory);
            String subcategoryKey = categories.get(index);
            button.enabled = StoreCatalog.SCROLL_ALL.equals(subcategoryKey)
                    || ClientStoreData.scopeEnabled(StoreCatalog.subcategoryScope(
                    activeCategory(), subcategoryKey));
            buttons.add(button);
        }
        ThemedButton next = smallButton(SUBCATEGORY_NEXT, left + width - 24, top, ">");
        next.enabled = subcategoryFirst + subcategorySlots < categories.size();
        buttons.add(next);
    }

    private void buildArmorCollectionButtons(List<GuiButton> buttons, int left, int top, int width) {
        int count = ClientStoreData.kage() ? StoreCatalog.ARMOR_CATEGORIES.size() : 1;
        armorCollection = Math.max(0, Math.min(armorCollection, count - 1));
        int gap = 4;
        int buttonWidth = (width - gap * (count - 1)) / count;
        for (int i = 0; i < count; i++) {
            String collection = StoreCatalog.ARMOR_CATEGORIES.get(i);
            ThemedButton button = new ThemedButton(ARMOR_COLLECTION_BASE + i,
                    left + i * (buttonWidth + gap), top, buttonWidth, 20,
                    armorCollectionName(collection),
                    i == armorCollection ? Theme.COPPER : Theme.TAB_INACTIVE_BG,
                    i == armorCollection ? Theme.COPPER_BRIGHT : Theme.TAB_HOVER_BG,
                    Theme.BUTTON_TEXT);
            button.setSelected(i == armorCollection);
            button.enabled = ClientStoreData.scopeEnabled(StoreCatalog.subcategoryScope(
                    StoreCatalog.ARMOR, collection));
            buttons.add(button);
        }
    }

    private void buildArmorTierButtons(List<GuiButton> buttons, int left, int top, int width) {
        int count = 5;
        int gap = 3;
        int buttonWidth = (width - gap * (count - 1)) / count;
        boolean compact = buttonWidth < 50;
        for (int tier = 0; tier < count; tier++) {
            ThemedButton button = new ThemedButton(ARMOR_TIER_BASE + tier,
                    left + tier * (buttonWidth + gap), top, buttonWidth, 20,
                    tierName(tier, compact),
                    tier == armorTier ? Theme.GOLD_DARK : Theme.TAB_INACTIVE_BG,
                    tier == armorTier ? Theme.GOLD : Theme.TAB_HOVER_BG,
                    Theme.BUTTON_TEXT);
            button.setSelected(tier == armorTier);
            button.enabled = ClientStoreData.scopeEnabled(StoreCatalog.tierScope(tier));
            buttons.add(button);
        }
    }

    private static ThemedButton smallButton(int id, int x, int y, String text) {
        return new ThemedButton(id, x, y, 24, 20, text,
                Theme.TAB_INACTIVE_BG, Theme.COPPER, Theme.BUTTON_TEXT);
    }

    @Override
    public boolean handleButtonClick(int buttonId) {
        if (buttonId >= CATEGORY_BASE && buttonId < CATEGORY_BASE + StoreCatalog.CATEGORIES.size()) {
            category = buttonId - CATEGORY_BASE;
            page = 0;
            selectedIndex = 0;
            armorCollection = 0;
            armorTier = 0;
            subcategory = 0;
            subcategoryFirst = 0;
            return true;
        }
        if (buttonId == CATEGORY_PREVIOUS) {
            categoryFirst = Math.max(0, categoryFirst - categorySlots);
            return true;
        }
        if (buttonId == CATEGORY_NEXT) {
            categoryFirst = Math.min(StoreCatalog.CATEGORIES.size() - categorySlots,
                    categoryFirst + categorySlots);
            return true;
        }
        if (buttonId >= SUBCATEGORY_BASE
                && buttonId < SUBCATEGORY_BASE + activeSubcategories().size()) {
            subcategory = buttonId - SUBCATEGORY_BASE;
            page = 0;
            selectedIndex = 0;
            return true;
        }
        if (buttonId >= ARMOR_COLLECTION_BASE
                && buttonId < ARMOR_COLLECTION_BASE + StoreCatalog.ARMOR_CATEGORIES.size()) {
            int selected = buttonId - ARMOR_COLLECTION_BASE;
            if (selected == 0 || ClientStoreData.kage()) {
                armorCollection = selected;
                page = 0;
                selectedIndex = 0;
            }
            return true;
        }
        if (buttonId >= ARMOR_TIER_BASE && buttonId < ARMOR_TIER_BASE + 5) {
            armorTier = buttonId - ARMOR_TIER_BASE;
            page = 0;
            selectedIndex = 0;
            return true;
        }
        if (buttonId == SUBCATEGORY_PREVIOUS) {
            subcategoryFirst = Math.max(0, subcategoryFirst - subcategorySlots);
            return true;
        }
        if (buttonId == SUBCATEGORY_NEXT) {
            subcategoryFirst = Math.min(activeSubcategories().size() - subcategorySlots,
                    subcategoryFirst + subcategorySlots);
            return true;
        }
        if (buttonId >= ITEM_BASE && buttonId < ITEM_BASE + rowsPerPage) {
            int index = page * rowsPerPage + buttonId - ITEM_BASE;
            if (index < visibleCategory.size()) selectedIndex = index;
            return true;
        }
        if (buttonId == PAGE_PREVIOUS) {
            page = Math.max(0, page - 1);
            selectedIndex = page * rowsPerPage;
            return true;
        }
        if (buttonId == PAGE_NEXT) {
            page++;
            selectedIndex = page * rowsPerPage;
            return true;
        }
        if (buttonId == BUY) {
            ClientStoreData.Entry selected = selected();
            if (selected != null && selected.purchasable) {
                RebornAddonNetwork.purchaseStoreItem(selected.key);
            }
            return false;
        }
        return false;
    }

    @Override
    public void onTick() {
        if (!ClientStoreData.loaded() && ++requestTicks >= 20) {
            requestTicks = 0;
            RebornAddonNetwork.requestStoreCatalog();
        }
    }

    @Override
    public boolean consumeButtonRefresh() {
        return builtRevision != ClientStoreData.revision() || currentBalance() != builtBalance;
    }

    @Override
    public void drawContent(GuiScreen screen, int left, int top, int width, int height,
                            int mouseX, int mouseY) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        boolean armor = StoreCatalog.ARMOR.equals(activeCategory());
        boolean hasSubcategories = StoreCatalog.SCROLLS.equals(activeCategory())
                || StoreCatalog.BUILDING.equals(activeCategory()) && !ClientStoreData.buildingLocked();
        int bodyTop = top + (armor ? 77 : hasSubcategories ? 52 : 27);
        int bodyHeight = height - (bodyTop - top);

        if (StoreCatalog.BUILDING.equals(activeCategory()) && ClientStoreData.buildingLocked()) {
            drawComingSoon(font, left, bodyTop, width, bodyHeight);
            return;
        }

        int detailLeft = left + listWidth + 9;
        int detailWidth = width - listWidth - 9;
        GuiChrome.section(left - 3, bodyTop, listWidth + 6, bodyHeight, Theme.COPPER);
        GuiChrome.section(detailLeft, bodyTop, detailWidth, bodyHeight, Theme.GOLD);

        if (!ClientStoreData.loaded()) {
            drawCentered(font, ClientLocalization.format("gui.rebornaddon.store.loading", "Loading stock..."),
                    detailLeft + detailWidth / 2, bodyTop + 22, detailWidth - 14, Theme.TEXT_MUTED);
            return;
        }
        if (visibleCategory.isEmpty() || selectedIndex < 0) {
            drawCentered(font, ClientLocalization.format("gui.rebornaddon.store.empty",
                            "No stock in this category"), detailLeft + detailWidth / 2,
                    bodyTop + 22, detailWidth - 14, Theme.TEXT_MUTED);
            return;
        }

        ClientStoreData.Entry selected = selected();
        if (selected == null) return;
        int centerX = detailLeft + detailWidth / 2;
        boolean compact = height < 260 || detailWidth < 112;
        int plateSize = compact ? Math.min(34, Math.max(28, detailWidth - 18))
                : Math.min(48, Math.max(38, detailWidth - 24));
        int plateX = centerX - plateSize / 2;
        int plateY = bodyTop + (compact ? 8 : 11);
        float iconScale = compact ? 1.0F : 1.5F;
        int iconSize = Math.round(16.0F * iconScale);
        GuiChrome.itemPlate(plateX, plateY, plateSize,
                selected.purchasable ? Theme.COPPER : Theme.DANGER);
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX - iconSize / 2,
                plateY + (plateSize - iconSize) / 2, 0.0F);
        GlStateManager.scale(iconScale, iconScale, 1.0F);
        Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(selected.stack, 0, 0);
        GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();

        if (!selected.purchasable) {
            Gui.drawRect(plateX + 2, plateY + plateSize / 2 - 6,
                    plateX + plateSize - 2, plateY + plateSize / 2 + 7, 0xD0902622);
            String x = "X";
            font.drawString(x, centerX - font.getStringWidth(x) / 2,
                    plateY + plateSize / 2 - 4, Theme.WHITE);
        }

        int nameY = plateY + plateSize + (compact ? 4 : 7);
        int textBottom = drawWrappedCentered(font, selected.name, centerX, nameY,
                detailWidth - 14, compact ? 1 : 2, Theme.TEXT_LIGHT);
        int ruleY = textBottom + 6;
        if (armor) {
            String enchantments = selected.tier == 0
                    ? ClientLocalization.format("gui.rebornaddon.store.tier_base_detail", "No enchantments")
                    : ClientLocalization.format("gui.rebornaddon.store.tier_enchants",
                    "Protection %s  |  Unbreaking %s", roman(selected.tier), roman(selected.tier));
            drawCentered(font, enchantments, centerX, ruleY,
                    detailWidth - 16, selected.tier == 0 ? Theme.TEXT_MUTED : Theme.GOLD);
            ruleY += 13;
        }
        GuiChrome.rule(detailLeft + 9, detailLeft + detailWidth - 8, ruleY,
                selected.purchasable ? Theme.COPPER : Theme.DANGER);

        String status = ClientStoreData.message();
        String quota = armor && ClientStoreData.kage() ? armorQuotaLine() : "";
        if (compact) {
            String summary = selected.purchasable
                    ? ClientLocalization.format("gui.rebornaddon.store.compact_balance", "%s / %s Ryo",
                    ClientStoreData.formatRyo(selected.price), ClientStoreData.formatRyo(currentBalance()))
                    : ClientLocalization.format("gui.rebornaddon.store.unavailable", "Unavailable");
            int color = selected.purchasable ? Theme.GOLD : Theme.DANGER_BRIGHT;
            if (!status.isEmpty()) {
                summary = status;
                color = ClientStoreData.success() ? Theme.SUCCESS_BRIGHT : Theme.DANGER_BRIGHT;
            } else if (!quota.isEmpty()) {
                summary = quota;
                color = Theme.TEXT_MUTED;
            }
            drawCentered(font, summary, centerX, ruleY + 8, detailWidth - 12, color);
        } else {
            String price = ClientLocalization.format("gui.rebornaddon.store.price", "Price: %s Ryo",
                    ClientStoreData.formatRyo(selected.price));
            String balance = quota.isEmpty()
                    ? ClientLocalization.format("gui.rebornaddon.store.balance", "Balance: %s Ryo",
                    ClientStoreData.formatRyo(currentBalance())) : quota;
            drawCentered(font, price, centerX, ruleY + 9, detailWidth - 18,
                    selected.purchasable ? Theme.GOLD : Theme.TEXT_MUTED);
            drawCentered(font, balance, centerX, ruleY + 22, detailWidth - 18, Theme.TEXT_LIGHT);
            if (!status.isEmpty()) {
                drawWrappedCentered(font, status, centerX, top + height - 50,
                        detailWidth - 20, 2,
                        ClientStoreData.success() ? Theme.SUCCESS_BRIGHT : Theme.DANGER_BRIGHT);
            } else if (!ClientStoreData.serviceAvailable() && !ClientStoreData.preview()) {
                drawCentered(font, ClientLocalization.format("gui.rebornaddon.store.service_unavailable",
                                "The server store is not configured yet."),
                        centerX, top + height - 47, detailWidth - 20, Theme.DANGER_BRIGHT);
            }
        }

        int pages = Math.max(1, (visibleCategory.size() + rowsPerPage - 1) / rowsPerPage);
        String pager = (page + 1) + " / " + pages;
        font.drawString(pager, left + (listWidth - font.getStringWidth(pager)) / 2,
                top + height - 15, Theme.TEXT_MUTED);

    }

    private static void drawComingSoon(FontRenderer font, int left, int top, int width, int height) {
        GuiChrome.section(left, top, width, height, Theme.GOLD);
        int centerX = left + width / 2;
        int markTop = top + Math.max(16, height / 4);
        Gui.drawRect(centerX - 34, markTop, centerX + 34, markTop + 2, Theme.GOLD_DARK);
        Gui.drawRect(centerX - 25, markTop + 7, centerX + 25, markTop + 9, Theme.COPPER);
        Gui.drawRect(centerX - 16, markTop + 14, centerX + 16, markTop + 16, Theme.GOLD_DARK);
        String heading = ClientLocalization.format("gui.rebornaddon.store.coming_soon_title", "COMING SOON");
        drawCentered(font, heading, centerX, markTop + 27, width - 30, Theme.GOLD);
        String line = ClientLocalization.format("gui.rebornaddon.store.building_preparing",
                "Building supply catalog in preparation");
        drawCentered(font, line, centerX, markTop + 42, width - 36, Theme.TEXT_MUTED);
        GuiChrome.rule(left + width / 4, left + width * 3 / 4, markTop + 57, Theme.COPPER);
    }

    private List<ClientStoreData.Entry> filteredEntries(String wantedCategory, String wantedSubcategory,
                                                         int wantedTier) {
        List<ClientStoreData.Entry> result = new ArrayList<ClientStoreData.Entry>();
        for (ClientStoreData.Entry entry : ClientStoreData.entries()) {
            if (wantedCategory.equals(entry.category)
                    && (wantedSubcategory.isEmpty() || wantedSubcategory.equals(entry.subcategory))
                    && (wantedTier < 0 || wantedTier == entry.tier)) {
                result.add(entry);
            }
        }
        return result;
    }

    private String activeCategory() {
        category = Math.max(0, Math.min(category, StoreCatalog.CATEGORIES.size() - 1));
        return StoreCatalog.CATEGORIES.get(category);
    }

    private String activeSubcategory() {
        List<String> categories = activeSubcategories();
        if (categories.isEmpty()) return "";
        subcategory = Math.max(0, Math.min(subcategory, categories.size() - 1));
        String selected = categories.get(subcategory);
        return StoreCatalog.SCROLL_ALL.equals(selected) ? "" : selected;
    }

    private List<String> activeSubcategories() {
        if (StoreCatalog.BUILDING.equals(activeCategory())) return StoreCatalog.BUILDING_CATEGORIES;
        if (StoreCatalog.SCROLLS.equals(activeCategory())) return StoreCatalog.SCROLL_CATEGORIES;
        return Collections.emptyList();
    }

    private String activeArmorCollection() {
        int maximum = ClientStoreData.kage() ? StoreCatalog.ARMOR_CATEGORIES.size() - 1 : 0;
        armorCollection = Math.max(0, Math.min(armorCollection, maximum));
        return StoreCatalog.ARMOR_CATEGORIES.get(armorCollection);
    }

    private String armorQuotaLine() {
        String collection = activeArmorCollection();
        if (StoreCatalog.ARMOR_ANBU.equals(collection)) {
            return ClientLocalization.format("gui.rebornaddon.store.anbu_quota", "Masks %s/8  |  Cloaks %s/8",
                    ClientStoreData.quota(StoreCatalog.QUOTA_ANBU_MASK),
                    ClientStoreData.quota(StoreCatalog.QUOTA_ANBU_CLOAK));
        }
        if (StoreCatalog.ARMOR_KAGE.equals(collection)) {
            return ClientLocalization.format("gui.rebornaddon.store.kage_quota", "Hat %s/1  |  Robe %s/1",
                    ClientStoreData.quota(StoreCatalog.QUOTA_KAGE_HAT),
                    ClientStoreData.quota(StoreCatalog.QUOTA_KAGE_ROBE));
        }
        for (ClientStoreData.Entry entry : visibleCategory) {
            if (entry.quotaKind.startsWith("akatsuki_")) {
                return ClientLocalization.format("gui.rebornaddon.store.akatsuki_quota",
                        "Headband %s/1  |  Headwear %s/1  |  Robe %s/1",
                        ClientStoreData.quota(StoreCatalog.QUOTA_AKATSUKI_HEADBAND),
                        ClientStoreData.quota(StoreCatalog.QUOTA_AKATSUKI_HAT),
                        ClientStoreData.quota(StoreCatalog.QUOTA_AKATSUKI_ROBE));
            }
        }
        return "";
    }

    private ClientStoreData.Entry selected() {
        if (visibleCategory.isEmpty() || selectedIndex < 0) return null;
        return visibleCategory.get(Math.min(selectedIndex, visibleCategory.size() - 1));
    }

    private static long currentBalance() {
        EntityPlayer player = Minecraft.getMinecraft().player;
        long local = player == null ? 0L : RyoCurrency.balance(player.inventory);
        return ClientStoreData.balance(local);
    }

    private static String categoryName(String category) {
        if (StoreCatalog.ARMOR.equals(category)) return ClientLocalization.format("gui.rebornaddon.store.armor", "Armor");
        if (StoreCatalog.WEAPONS.equals(category)) return ClientLocalization.format("gui.rebornaddon.store.weapons", "Weapons");
        if (StoreCatalog.FOOD.equals(category)) return ClientLocalization.format("gui.rebornaddon.store.food", "Food");
        if (StoreCatalog.BUILDING.equals(category)) return ClientLocalization.format("gui.rebornaddon.store.building", "Building");
        if (StoreCatalog.TOOLS.equals(category)) return ClientLocalization.format("gui.rebornaddon.store.tools", "Tools");
        if (StoreCatalog.MUSIC.equals(category)) return ClientLocalization.format("gui.rebornaddon.store.music", "Music");
        return ClientLocalization.format("gui.rebornaddon.store.scrolls", "Scrolls");
    }

    private static String subcategoryName(String category) {
        if (StoreCatalog.BUILDING_BLOCKS.equals(category)) return ClientLocalization.format("gui.rebornaddon.store.blocks", "Blocks");
        if (StoreCatalog.BUILDING_DECORATION.equals(category)) return ClientLocalization.format("gui.rebornaddon.store.decoration", "Decor");
        if (StoreCatalog.BUILDING_DOORS.equals(category)) return ClientLocalization.format("gui.rebornaddon.store.doors", "Doors");
        if (StoreCatalog.BUILDING_NATURE.equals(category)) return ClientLocalization.format("gui.rebornaddon.store.nature", "Nature");
        if (StoreCatalog.BUILDING_LIGHTING.equals(category)) return ClientLocalization.format("gui.rebornaddon.store.lighting", "Lighting");
        if (StoreCatalog.BUILDING_FURNITURE.equals(category)) return ClientLocalization.format("gui.rebornaddon.store.furniture", "Furniture");
        if (StoreCatalog.BUILDING_HARDWARE.equals(category)) return ClientLocalization.format("gui.rebornaddon.store.hardware", "Hardware");
        if (StoreCatalog.SCROLL_ALL.equals(category)) return ClientLocalization.format("gui.rebornaddon.store.scroll_all", "All");
        if (StoreCatalog.SCROLL_FIRE.equals(category)) return ClientLocalization.format("gui.rebornaddon.store.scroll_fire", "Fire");
        if (StoreCatalog.SCROLL_WATER.equals(category)) return ClientLocalization.format("gui.rebornaddon.store.scroll_water", "Water");
        if (StoreCatalog.SCROLL_WIND.equals(category)) return ClientLocalization.format("gui.rebornaddon.store.scroll_wind", "Wind");
        if (StoreCatalog.SCROLL_LIGHTNING.equals(category)) return ClientLocalization.format("gui.rebornaddon.store.scroll_lightning", "Lightning");
        if (StoreCatalog.SCROLL_EARTH.equals(category)) return ClientLocalization.format("gui.rebornaddon.store.scroll_earth", "Earth");
        if (StoreCatalog.SCROLL_KEKKEI.equals(category)) return ClientLocalization.format("gui.rebornaddon.store.scroll_kekkei", "Kekkei");
        if (StoreCatalog.SCROLL_MEDICAL.equals(category)) return ClientLocalization.format("gui.rebornaddon.store.scroll_medical", "Medical");
        if (StoreCatalog.SCROLL_SEALING.equals(category)) return ClientLocalization.format("gui.rebornaddon.store.scroll_sealing", "Sealing");
        if (StoreCatalog.SCROLL_DOJUTSU.equals(category)) return ClientLocalization.format("gui.rebornaddon.store.scroll_dojutsu", "Dojutsu");
        if (StoreCatalog.SCROLL_TAIJUTSU.equals(category)) return ClientLocalization.format("gui.rebornaddon.store.scroll_taijutsu", "Taijutsu");
        if (StoreCatalog.SCROLL_SUMMONING.equals(category)) return ClientLocalization.format("gui.rebornaddon.store.scroll_summoning", "Summoning");
        return ClientLocalization.format("gui.rebornaddon.store.scroll_other", "Other");
    }

    private static String armorCollectionName(String category) {
        if (StoreCatalog.ARMOR_ANBU.equals(category)) {
            return ClientLocalization.format("gui.rebornaddon.store.armor_anbu", "ANBU");
        }
        if (StoreCatalog.ARMOR_KAGE.equals(category)) {
            return ClientLocalization.format("gui.rebornaddon.store.armor_kage", "Kage");
        }
        return ClientLocalization.format("gui.rebornaddon.store.armor_village", "Village Armor");
    }

    private static String tierName(int tier, boolean compact) {
        if (tier == 0) return ClientLocalization.format("gui.rebornaddon.store.tier_base", "Base");
        return compact ? "T" + tier : ClientLocalization.format("gui.rebornaddon.store.tier", "Tier %s", tier);
    }

    private static String roman(int level) {
        switch (level) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            default: return "IV";
        }
    }

    private static void drawCentered(FontRenderer font, String value, int centerX, int y, int width, int color) {
        String text = font.trimStringToWidth(value, Math.max(20, width));
        font.drawString(text, centerX - font.getStringWidth(text) / 2, y, color);
    }

    private static int drawWrappedCentered(FontRenderer font, String value, int centerX, int y,
                                           int width, int maxLines, int color) {
        List<String> lines = font.listFormattedStringToWidth(value, Math.max(28, width));
        int count = Math.min(maxLines, lines.size());
        for (int line = 0; line < count; line++) {
            String text = lines.get(line);
            if (line == count - 1 && lines.size() > count) {
                text = font.trimStringToWidth(text,
                        Math.max(8, width - font.getStringWidth("..."))) + "...";
            }
            font.drawString(text, centerX - font.getStringWidth(text) / 2,
                    y + line * (font.FONT_HEIGHT + 1), color);
        }
        return y + count * (font.FONT_HEIGHT + 1);
    }

    private static final class CatalogButton extends ThemedButton {
        private final ItemStack stack;
        private final boolean purchasable;

        private CatalogButton(int id, int x, int y, int width, int height, ClientStoreData.Entry entry) {
            super(id, x, y, width, height, entry.name,
                    Theme.TAB_INACTIVE_BG, Theme.COPPER, Theme.BUTTON_TEXT);
            stack = entry.stack;
            purchasable = entry.purchasable;
            setTextAlignment(Alignment.LEFT);
            setTextInset(24);
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
            super.drawButton(minecraft, mouseX, mouseY, partialTicks);
            if (!visible) return;
            RenderHelper.enableGUIStandardItemLighting();
            minecraft.getRenderItem().renderItemAndEffectIntoGUI(stack, x + 4, y + 2);
            RenderHelper.disableStandardItemLighting();
            if (!purchasable) {
                Gui.drawRect(x + width - 18, y + 2, x + width - 2, y + height - 2, 0xC08F2521);
                minecraft.fontRenderer.drawString("X", x + width - 13, y + 6, Theme.WHITE);
            }
        }
    }
}
