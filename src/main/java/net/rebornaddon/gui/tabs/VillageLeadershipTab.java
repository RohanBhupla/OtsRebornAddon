package net.rebornaddon.gui.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.rebornaddon.gui.theme.GuiChrome;
import net.rebornaddon.gui.theme.Theme;
import net.rebornaddon.gui.widgets.ThemedButton;
import net.rebornaddon.client.ClientLocalization;
import net.rebornaddon.village.Village;
import net.rebornaddon.village.VillageLeadershipService;
import net.rebornaddon.village.client.ClientVillageLeadershipData;
import net.rebornaddon.village.network.RebornAddonNetwork;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class VillageLeadershipTab implements HubTab {
    private static final int TAB_BASE = 1000;
    private static final int VILLAGE_PREVIOUS = 1010;
    private static final int VILLAGE_NEXT = 1011;
    private static final int ROW_BASE = 1020;
    private static final int PAGE_PREVIOUS = 1030;
    private static final int PAGE_NEXT = 1031;
    private static final int ADD_ADVISOR = 1040;
    private static final int REMOVE_ADVISOR = 1041;
    private static final int VOTE_REMOVE = 1042;
    private static final int ADD_ANBU = 1050;
    private static final int REMOVE_ANBU = 1051;
    private static final int SET_CAPTAIN = 1052;
    private static final int START_HUNT = 1060;
    private static final int EVENT_VILLAGE = 1070;
    private static final int EVENT_PICK = 1071;
    private static final int LOCATION_PICK = 1072;
    private static final int START_EVENT = 1073;
    private static final int EVENT_SERVER = 1074;
    private static final int ROWS_PER_PAGE = 7;

    private int section;
    private int page;
    private UUID selected;
    private int eventIndex;
    private int locationIndex;
    private boolean serverEvent;
    private int builtRevision = -1;
    private int refreshTicks;
    private int rowsPerPage = ROWS_PER_PAGE;
    private List<ClientVillageLeadershipData.Member> visibleRows = Collections.emptyList();

    @Override
    public String getTabName() {
        return ClientLocalization.format("gui.rebornaddon.tab.village", "Village");
    }

    @Override
    public int getTabColorActive() {
        Village village = Village.byId(ClientVillageLeadershipData.get().villageId);
        return village == null ? Theme.TEAL : village.accentColor();
    }

    @Override
    public int getTabColorHover() {
        Village village = Village.byId(ClientVillageLeadershipData.get().villageId);
        return village == null ? Theme.TEAL_LIGHT : village.hoverColor();
    }

    @Override
    public void buildButtons(List<GuiButton> buttons, int left, int top, int width, int height) {
        ClientVillageLeadershipData.Snapshot data = ClientVillageLeadershipData.get();
        builtRevision = ClientVillageLeadershipData.getRevision();
        int gap = 4;
        int tabWidth = Math.max(26, (width - gap * 3) / 4);
        String[] labels = new String[]{
                ClientLocalization.format("gui.rebornaddon.leadership.members", "Members"),
                ClientLocalization.format("gui.rebornaddon.leadership.anbu", "ANBU"),
                ClientLocalization.format("gui.rebornaddon.leadership.hunts", "Hunts"),
                ClientLocalization.format("gui.rebornaddon.leadership.events", "Events")
        };
        for (int i = 0; i < labels.length; i++) {
            ThemedButton tab = new ThemedButton(TAB_BASE + i, left + i * (tabWidth + gap), top,
                    tabWidth, 19, labels[i], section == i ? getTabColorActive() : Theme.TAB_INACTIVE_BG,
                    getTabColorHover(), Theme.BUTTON_TEXT);
            tab.setSelected(section == i);
            buttons.add(tab);
        }

        int bodyTop = top + 27;
        if (data.switchVillage) {
            buttons.add(new ThemedButton(VILLAGE_PREVIOUS, left, bodyTop, 20, 18, "<",
                    Theme.TAB_INACTIVE_BG, Theme.TAB_HOVER_BG, Theme.BUTTON_TEXT));
            buttons.add(new ThemedButton(VILLAGE_NEXT, left + width - 20, bodyTop, 20, 18, ">",
                    Theme.TAB_INACTIVE_BG, Theme.TAB_HOVER_BG, Theme.BUTTON_TEXT));
            bodyTop += 24;
        }
        bodyTop += 16;

        if (section == 0 || section == 1 || section == 2) {
            buildRosterButtons(buttons, data, left, bodyTop, width, height - (bodyTop - top));
        } else {
            buildEventButtons(buttons, data, left, bodyTop, width, height - (bodyTop - top));
        }
    }

    private void buildRosterButtons(List<GuiButton> buttons, ClientVillageLeadershipData.Snapshot data,
                                    int left, int top, int width, int height) {
        List<ClientVillageLeadershipData.Member> source = section == 2 ? data.online : data.members;
        int actionHeight = 27;
        int listHeight = Math.max(24, height - actionHeight);
        rowsPerPage = Math.max(2, Math.min(ROWS_PER_PAGE, listHeight / 18));
        int maxPage = Math.max(0, (source.size() - 1) / rowsPerPage);
        page = Math.max(0, Math.min(page, maxPage));
        int start = page * rowsPerPage;
        int end = Math.min(source.size(), start + rowsPerPage);
        visibleRows = source.subList(start, end);
        int rowHeight = Math.max(18, Math.min(22, listHeight / rowsPerPage));
        for (int i = 0; i < visibleRows.size(); i++) {
            ClientVillageLeadershipData.Member member = visibleRows.get(i);
            boolean active = member.id.equals(selected);
            ThemedButton row = new ThemedButton(ROW_BASE + i, left, top + i * rowHeight,
                    width, rowHeight - 2, rowLabel(member),
                    active ? Theme.GOLD_DARK : Theme.TAB_INACTIVE_BG,
                    active ? Theme.GOLD : Theme.TAB_HOVER_BG, Theme.BUTTON_TEXT);
            row.setSelected(active);
            row.setTextAlignment(ThemedButton.Alignment.LEFT);
            row.setTextInset(9);
            buttons.add(row);
        }

        int bottom = top + height - 20;
        ThemedButton previous = new ThemedButton(PAGE_PREVIOUS, left, bottom, 20, 18, "<",
                Theme.TAB_INACTIVE_BG, Theme.TAB_HOVER_BG, Theme.BUTTON_TEXT);
        previous.enabled = page > 0;
        buttons.add(previous);
        ThemedButton next = new ThemedButton(PAGE_NEXT, left + 23, bottom, 20, 18, ">",
                Theme.TAB_INACTIVE_BG, Theme.TAB_HOVER_BG, Theme.BUTTON_TEXT);
        next.enabled = page < maxPage;
        buttons.add(next);

        int actionLeft = left + 49;
        int actionWidth = Math.max(28, (width - 49 - 8) / 3);
        if (section == 0) {
            addAction(buttons, ADD_ADVISOR, actionLeft, bottom, actionWidth,
                    ClientLocalization.format("gui.rebornaddon.leadership.appoint", "Appoint"), data.manageAdvisors);
            addAction(buttons, REMOVE_ADVISOR, actionLeft + actionWidth + 4, bottom, actionWidth,
                    ClientLocalization.format("gui.rebornaddon.leadership.dismiss", "Dismiss"), data.manageAdvisors);
            addAction(buttons, VOTE_REMOVE, actionLeft + (actionWidth + 4) * 2, bottom, actionWidth,
                    ClientLocalization.format("gui.rebornaddon.leadership.vote_out", "Vote Out"), data.vote);
        } else if (section == 1) {
            addAction(buttons, ADD_ANBU, actionLeft, bottom, actionWidth,
                    ClientLocalization.format("gui.rebornaddon.leadership.add", "Add"), data.manageAnbu);
            addAction(buttons, REMOVE_ANBU, actionLeft + actionWidth + 4, bottom, actionWidth,
                    ClientLocalization.format("gui.rebornaddon.leadership.remove", "Remove"), data.manageAnbu);
            addAction(buttons, SET_CAPTAIN, actionLeft + (actionWidth + 4) * 2, bottom, actionWidth,
                    ClientLocalization.format("gui.rebornaddon.leadership.captain", "Captain"), data.manageAnbu);
        } else {
            addAction(buttons, START_HUNT, actionLeft, bottom, width - 49,
                    ClientLocalization.format("gui.rebornaddon.leadership.start_hunt", "Start Hunt"), data.hunt);
        }
    }

    private void buildEventButtons(List<GuiButton> buttons, ClientVillageLeadershipData.Snapshot data,
                                   int left, int top, int width, int height) {
        List<ClientVillageLeadershipData.Preset> events = serverEvent ? data.serverEvents : data.villageEvents;
        eventIndex = clamp(eventIndex, events.size());
        locationIndex = clamp(locationIndex, data.locations.size());
        int rowWidth = Math.max(80, width - 16);
        int x = left + (width - rowWidth) / 2;
        int scopeWidth = (rowWidth - 4) / 2;
        boolean compact = height < 125;
        int buttonHeight = compact ? 16 : 21;
        int scopeY = top + (compact ? 14 : 22);
        int eventY = top + (compact ? 33 : 54);
        int locationY = top + (compact ? 52 : 82);
        int startY = compact ? top + Math.min(height - 18, 74)
                : top + Math.min(height - 25, 119);
        ThemedButton villageScope = new ThemedButton(EVENT_VILLAGE, x, scopeY, scopeWidth, buttonHeight,
                ClientLocalization.format("gui.rebornaddon.leadership.village_scope", "Village"),
                serverEvent ? Theme.TAB_INACTIVE_BG : getTabColorActive(),
                getTabColorHover(), Theme.BUTTON_TEXT);
        villageScope.setSelected(!serverEvent);
        buttons.add(villageScope);
        ThemedButton serverScope = new ThemedButton(EVENT_SERVER, x + scopeWidth + 4, scopeY,
                rowWidth - scopeWidth - 4, buttonHeight,
                ClientLocalization.format("gui.rebornaddon.leadership.server_scope", "Server-wide"),
                serverEvent ? Theme.RANKED_RED : Theme.TAB_INACTIVE_BG,
                Theme.RANKED_RED_HOVER, Theme.BUTTON_TEXT);
        serverScope.setSelected(serverEvent);
        buttons.add(serverScope);
        buttons.add(new ThemedButton(EVENT_PICK, x, eventY, rowWidth, buttonHeight,
                events.isEmpty() ? ClientLocalization.format("gui.rebornaddon.leadership.no_events",
                        "No event presets") : events.get(eventIndex).name,
                Theme.PURPLE_DARK, Theme.PURPLE_MID, Theme.BUTTON_TEXT));
        buttons.add(new ThemedButton(LOCATION_PICK, x, locationY, rowWidth, buttonHeight,
                data.locations.isEmpty() ? ClientLocalization.format("gui.rebornaddon.leadership.no_locations",
                        "No locations configured") : data.locations.get(locationIndex).name,
                Theme.PURPLE_DARK, Theme.PURPLE_MID, Theme.BUTTON_TEXT));
        ThemedButton start = new ThemedButton(START_EVENT, x, startY, rowWidth, compact ? 18 : 22,
                ClientLocalization.format("gui.rebornaddon.leadership.start_event", "Start Event"),
                Theme.RANKED_RED_DARK, Theme.RANKED_RED, Theme.BUTTON_TEXT);
        start.enabled = data.startEvents && !events.isEmpty() && !data.locations.isEmpty();
        buttons.add(start);
    }

    private void addAction(List<GuiButton> buttons, int id, int x, int y, int width,
                           String label, boolean allowed) {
        ThemedButton button = new ThemedButton(id, x, y, width, 18, label,
                Theme.PURPLE_DARK, Theme.TEAL, Theme.BUTTON_TEXT);
        button.enabled = allowed && selected != null;
        buttons.add(button);
    }

    @Override
    public void drawContent(GuiScreen screen, int left, int top, int width, int height,
                            int mouseX, int mouseY) {
        ClientVillageLeadershipData.Snapshot data = ClientVillageLeadershipData.get();
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int accent = getTabColorActive();
        GuiChrome.rule(left, left + width, top + 22, accent);
        int bodyTop = top + 29;
        Village village = Village.byId(data.villageId);
        if (data.switchVillage) {
            String name = village == null
                    ? ClientLocalization.format("gui.rebornaddon.leadership.village_scope", "Village")
                    : ClientLocalization.format("gui.rebornaddon.leadership.named_village", "%s Village",
                            village.displayName());
            font.drawString(name, left + (width - font.getStringWidth(name)) / 2, bodyTop + 5, Theme.TEXT_LIGHT);
            bodyTop += 24;
        }
        if (!data.access) {
            font.drawString(data.message.isEmpty()
                            ? ClientLocalization.format("gui.rebornaddon.leadership.access_required",
                                    "Leadership access is required.") : data.message,
                    left + 8, bodyTop + 8, Theme.DANGER);
            return;
        }

        if (section == 0) {
            drawRosterSummary(font, left, bodyTop, width, data, false);
        } else if (section == 1) {
            drawRosterSummary(font, left, bodyTop, width, data, true);
        } else if (section == 2) {
            drawHuntSummary(font, left, bodyTop, width, data);
        } else {
            drawEventSummary(font, left, bodyTop, width, data);
        }
    }

    private void drawRosterSummary(FontRenderer font, int left, int top, int width,
                                   ClientVillageLeadershipData.Snapshot data, boolean anbu) {
        String summary = anbu
                ? ClientLocalization.format("gui.rebornaddon.leadership.unit", "Unit: %s/8", data.anbuCount)
                : ClientLocalization.format("gui.rebornaddon.leadership.member_count", "%s village members",
                        data.members.size());
        font.drawString(summary, left + width - font.getStringWidth(summary), top + 2, Theme.TEXT_MUTED);
    }

    private void drawHuntSummary(FontRenderer font, int left, int top, int width,
                                 ClientVillageLeadershipData.Snapshot data) {
        String status;
        if (data.activeHunt != null && data.activeHunt.state == 1) {
            status = ClientLocalization.format("gui.rebornaddon.leadership.active_target", "Active target: %s",
                    data.activeHunt.target);
        } else if (data.huntCooldown > 0L) {
            status = ClientLocalization.format("gui.rebornaddon.leadership.cooldown", "Cooldown: %s",
                    duration(data.huntCooldown));
        } else {
            status = ClientLocalization.format("gui.rebornaddon.leadership.hunt_ready", "Hunt ready");
        }
        font.drawString(status, left + width - font.getStringWidth(status), top + 2,
                data.huntCooldown > 0L ? Theme.DANGER : Theme.TEXT_MUTED);
    }

    private void drawEventSummary(FontRenderer font, int left, int top, int width,
                                  ClientVillageLeadershipData.Snapshot data) {
        ClientVillageLeadershipData.Event active = serverEvent ? data.serverEvent : data.villageEvent;
        String status = active == null
                ? ClientLocalization.format("gui.rebornaddon.leadership.no_active_event", "No active event")
                : ClientLocalization.format("gui.rebornaddon.leadership.active_event", "%s at %s",
                        active.name, active.location);
        status = font.trimStringToWidth(status, Math.max(30, width - 8));
        font.drawString(status, left + 4, top + 2, active == null ? Theme.TEXT_MUTED : Theme.GOLD);
    }

    @Override
    public boolean handleButtonClick(int buttonId) {
        ClientVillageLeadershipData.Snapshot data = ClientVillageLeadershipData.get();
        if (buttonId >= TAB_BASE && buttonId < TAB_BASE + 4) {
            section = buttonId - TAB_BASE;
            page = 0;
            selected = null;
            return true;
        }
        if (buttonId == VILLAGE_PREVIOUS || buttonId == VILLAGE_NEXT) {
            int next = data.villageId + (buttonId == VILLAGE_PREVIOUS ? -1 : 1);
            Village[] villages = Village.all();
            if (next < 0) next = villages.length - 1;
            if (next >= villages.length) next = 0;
            selected = null;
            RebornAddonNetwork.requestVillageLeadership(next);
            return false;
        }
        if (buttonId >= ROW_BASE && buttonId < ROW_BASE + visibleRows.size()) {
            selected = visibleRows.get(buttonId - ROW_BASE).id;
            return true;
        }
        if (buttonId == PAGE_PREVIOUS || buttonId == PAGE_NEXT) {
            page += buttonId == PAGE_PREVIOUS ? -1 : 1;
            selected = null;
            return true;
        }
        if (buttonId == EVENT_VILLAGE || buttonId == EVENT_SERVER) {
            serverEvent = buttonId == EVENT_SERVER;
            eventIndex = 0;
            return true;
        }
        if (buttonId == EVENT_PICK) {
            List<ClientVillageLeadershipData.Preset> events = serverEvent ? data.serverEvents : data.villageEvents;
            if (!events.isEmpty()) eventIndex = (eventIndex + 1) % events.size();
            return true;
        }
        if (buttonId == LOCATION_PICK) {
            if (!data.locations.isEmpty()) locationIndex = (locationIndex + 1) % data.locations.size();
            return true;
        }
        if (buttonId == START_EVENT) {
            List<ClientVillageLeadershipData.Preset> events = serverEvent ? data.serverEvents : data.villageEvents;
            if (!events.isEmpty() && !data.locations.isEmpty()) {
                send(serverEvent ? VillageLeadershipService.START_SERVER_EVENT
                                : VillageLeadershipService.START_VILLAGE_EVENT,
                        null, events.get(eventIndex).id + "|" + data.locations.get(locationIndex).id);
            }
            return false;
        }

        int action = action(buttonId);
        if (action != 0 && selected != null) {
            ClientVillageLeadershipData.Member member = selectedMember(data);
            send(action, selected, member == null ? "" : member.name);
        }
        return false;
    }

    private int action(int buttonId) {
        if (buttonId == ADD_ADVISOR) return VillageLeadershipService.ADD_ADVISOR;
        if (buttonId == REMOVE_ADVISOR) return VillageLeadershipService.REMOVE_ADVISOR;
        if (buttonId == VOTE_REMOVE) return VillageLeadershipService.VOTE_REMOVE;
        if (buttonId == ADD_ANBU) return VillageLeadershipService.ADD_ANBU;
        if (buttonId == REMOVE_ANBU) return VillageLeadershipService.REMOVE_ANBU;
        if (buttonId == SET_CAPTAIN) return VillageLeadershipService.SET_CAPTAIN;
        if (buttonId == START_HUNT) return VillageLeadershipService.START_HUNT;
        return 0;
    }

    private void send(int action, UUID target, String value) {
        RebornAddonNetwork.sendVillageLeadershipAction(action,
                ClientVillageLeadershipData.get().villageId, target, value);
    }

    private ClientVillageLeadershipData.Member selectedMember(ClientVillageLeadershipData.Snapshot data) {
        List<ClientVillageLeadershipData.Member> source = section == 2 ? data.online : data.members;
        for (ClientVillageLeadershipData.Member member : source) {
            if (member.id.equals(selected)) return member;
        }
        return null;
    }

    private static String rowLabel(ClientVillageLeadershipData.Member member) {
        String role = member.kage
                ? ClientLocalization.format("gui.rebornaddon.overview.role.kage", "Kage")
                : member.captain ? ClientLocalization.format("gui.rebornaddon.leadership.captain", "Captain")
                : member.advisor ? ClientLocalization.format("gui.rebornaddon.overview.role.advisor", "Advisor")
                : member.anbu ? ClientLocalization.format("gui.rebornaddon.leadership.anbu", "ANBU")
                : ClientLocalization.format("gui.rebornaddon.leadership.member", "Member");
        return (member.online ? "* " : "  ") + member.name + "  |  " + role
                + (member.votes > 0 ? "  " + member.votes + "/3" : "");
    }

    private static int clamp(int value, int size) {
        return size <= 0 ? 0 : Math.max(0, Math.min(value, size - 1));
    }

    private static String duration(long millis) {
        long minutes = Math.max(1L, (millis + 59_999L) / 60_000L);
        return minutes >= 60L ? minutes / 60L + "h " + minutes % 60L + "m" : minutes + "m";
    }

    @Override
    public boolean consumeButtonRefresh() {
        return builtRevision != ClientVillageLeadershipData.getRevision();
    }

    @Override
    public void onTick() {
        if (++refreshTicks >= 200) {
            refreshTicks = 0;
            RebornAddonNetwork.requestVillageLeadership(ClientVillageLeadershipData.get().villageId);
        }
    }
}
