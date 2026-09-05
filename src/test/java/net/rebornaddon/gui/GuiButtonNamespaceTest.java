package net.rebornaddon.gui;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;

public class GuiButtonNamespaceTest {
    @Test
    public void hubTabButtonNamespacesDoNotOverlap() throws Exception {
        audit("net.rebornaddon.gui.tabs.ExamsTab",
                ranges(range("VIEW_BASE", 3), range("ROW_BASE", 9)),
                "PREVIOUS", "NEXT", "REFRESH", "CREATE", "EDIT", "ENROLL",
                "WRITTEN", "READY", "APPROVE", "REJECT");
        audit("net.rebornaddon.gui.tabs.MailTab",
                ranges(range("ROW_BASE", 8)),
                "INBOX", "COMPOSE", "PREVIOUS", "NEXT", "CLAIM", "DELETE",
                "ATTACHMENT", "SEND", "REFRESH", "CLEAR_ATTACHMENTS");
        audit("net.rebornaddon.gui.tabs.MarketplaceTab",
                ranges(range("VIEW_BASE", 4), range("ROW_BASE", 12)),
                "PREVIOUS", "NEXT", "PRIMARY", "CREATE");
        audit("net.rebornaddon.gui.tabs.PartyTab",
                ranges(range("INVITE_ONLINE_BASE", 4), range("TELEPORT_BASE", 10),
                        range("KICK_BASE", 10), range("PROMOTE_BASE", 10)),
                "INVITE", "ACCEPT", "LEAVE");
        audit("net.rebornaddon.gui.tabs.QuestsTab",
                ranges(range("CUSTOM_TAB_BASE", 3), range("RANK_BASE", 5),
                        range("QUEST_BASE", 7)),
                "MODE_MISSIONS", "MODE_VILLAGE", "CUSTOM_PAGE_PREV", "CUSTOM_PAGE_NEXT",
                "QUEST_PAGE_PREV", "QUEST_PAGE_NEXT", "DETAIL_UP", "DETAIL_DOWN",
                "CLAIM_REWARDS", "QUEST_ACTION", "QUEST_RESET", "REFRESH");
        audit("net.rebornaddon.gui.tabs.ShinobiTab",
                ranges(range("SECTION_BASE", 6), range("ROW_BASE", 12)),
                "PREVIOUS", "NEXT", "ACTION", "RESET_TRAINING");
        audit("net.rebornaddon.gui.tabs.StoreTab",
                ranges(range("CATEGORY_BASE", 7), range("SUBCATEGORY_BASE", 12),
                        range("ITEM_BASE", 9), range("ARMOR_COLLECTION_BASE", 3),
                        range("ARMOR_TIER_BASE", 5)),
                "CATEGORY_PREVIOUS", "CATEGORY_NEXT", "SUBCATEGORY_PREVIOUS",
                "SUBCATEGORY_NEXT", "PAGE_PREVIOUS", "PAGE_NEXT", "BUY");
        audit("net.rebornaddon.gui.tabs.TradeTab",
                ranges(range("PLAYER_BASE", 20)),
                "REQUEST", "ACCEPT", "CANCEL", "READY", "PREVIOUS", "NEXT");
        audit("net.rebornaddon.gui.tabs.VillageLeadershipTab",
                ranges(range("TAB_BASE", 4), range("ROW_BASE", 7)),
                "VILLAGE_PREVIOUS", "VILLAGE_NEXT", "PAGE_PREVIOUS", "PAGE_NEXT",
                "ADD_ADVISOR", "REMOVE_ADVISOR", "VOTE_REMOVE", "ADD_ANBU",
                "REMOVE_ANBU", "SET_CAPTAIN", "START_HUNT", "EVENT_VILLAGE",
                "EVENT_PICK", "LOCATION_PICK", "START_EVENT", "EVENT_SERVER");
        audit("net.rebornaddon.gui.tabs.WorldEditorTab",
                ranges(range("SECTION_BASE", 7), range("ROW_BASE", 20)),
                "PREVIOUS", "NEXT", "CREATE", "EDIT", "DUPLICATE", "DELETE",
                "REFRESH", "VALIDATE", "PUBLISH", "ROLLBACK");
    }

    @Test
    public void adminSubpanelButtonNamespacesDoNotOverlap() throws Exception {
        audit("net.rebornaddon.gui.tabs.LuckPermsAdminPanel",
                ranges(range("PLAYER_ROW_BASE", 20), range("GROUP_ROW_BASE", 20)),
                "PLAYER_PREVIOUS", "PLAYER_NEXT", "GROUP_PREVIOUS", "GROUP_NEXT",
                "ADD_PARENT", "REMOVE_PARENT", "REFRESH");
        audit("net.rebornaddon.gui.tabs.MountAdminPanel",
                ranges(range("PLAYER_ROW_BASE", 20), range("FORM_ROW_BASE", 20)),
                "PLAYER_PREVIOUS", "PLAYER_NEXT", "FORM_PREVIOUS", "FORM_NEXT",
                "TYPE_MOUNT", "TYPE_COMPANION", "FAVORITE", "SELECT", "GRANT",
                "REVOKE", "REFRESH", "SAVE_SCALE");
        audit("net.rebornaddon.gui.tabs.ModerationAdminPanel",
                ranges(range("VIEW_BASE", 3), range("ROW_BASE", 20)),
                "SEARCH", "REFRESH", "PREVIOUS", "NEXT", "PERMANENT", "BAN",
                "WARN", "UNBAN", "WARNING_PREVIOUS", "WARNING_NEXT");
    }

    @Test
    public void standaloneScreenButtonNamespacesDoNotOverlap() throws Exception {
        audit("net.rebornaddon.content.client.GuiNativeCatalogPicker",
                ranges(range("ROW_BASE", 20)), "PREVIOUS", "NEXT", "CANCEL");
        audit("net.rebornaddon.content.client.GuiNativeDialogue",
                ranges(range("CHOICE_BASE", 8)), "PREVIOUS", "NEXT");
        audit("net.rebornaddon.content.client.GuiNativeRecordEditor",
                ranges(range("ROW_BASE", 30), range("GROUP_BASE", 30)),
                "PREVIOUS", "NEXT", "TOGGLE", "ADD", "REMOVE", "POSITION",
                "SAVE", "CANCEL", "PICK", "SKIN_LIBRARY");
        audit("net.rebornaddon.exam.client.GuiExamEditor",
                ranges(range("PAGE_BASE", 5), range("ANSWER_ROW_BASE", 6)),
                "SAVE", "CANCEL", "RANK_PREVIOUS", "RANK_NEXT", "STAGE_WRITTEN",
                "STAGE_PRACTICAL", "STAGE_TOURNAMENT", "QUESTION_PREVIOUS",
                "QUESTION_NEXT", "QUESTION_ADD", "QUESTION_DELETE", "ANSWER_PREVIOUS",
                "ANSWER_NEXT", "ANSWER_ADD", "ANSWER_DELETE", "ANSWER_CORRECT",
                "SELECTION_SINGLE", "SELECTION_ANY", "SELECTION_ALL", "MAX_SELECTIONS",
                "RULE_NATURE", "RULE_KG", "RULE_MODES", "RULE_ARMOR", "RULE_WEAPONS",
                "WEAPON_ADD", "WEAPON_REMOVE", "WEAPON_PREVIOUS", "WEAPON_NEXT",
                "ARENA_PREVIOUS", "ARENA_NEXT", "FIGHTER_A_PREVIOUS", "FIGHTER_A_NEXT",
                "FIGHTER_B_PREVIOUS", "FIGHTER_B_NEXT", "BRACKET_ADD", "BRACKET_REMOVE",
                "BRACKET_PREVIOUS", "BRACKET_NEXT", "PRACTICAL_PASS", "PRACTICAL_FAIL",
                "TOURNAMENT_START");
        audit("net.rebornaddon.exam.client.GuiWrittenExam",
                ranges(range("ANSWER_BASE", 8)), "SUBMIT", "CLOSE");
        audit("net.rebornaddon.jutsu.client.GuiJutsuAdmin",
                ranges(range("ENTRY_BASE", 20)),
                "SOURCE_FILTER", "STATE_FILTER", "PREVIOUS_PAGE", "NEXT_PAGE", "PROPERTY",
                "BOOLEAN_VALUE", "SAVE", "RESET_PROPERTY", "RESET_ENTRY", "RESET_ALL",
                "DONE", "TAB_JUTSUS", "TAB_STORE", "TAB_LIMITS");
        audit("net.rebornaddon.jutsu.client.GuiJutsuEffectEditor",
                ranges(range("ROW_BASE", 20)),
                "PREVIOUS", "NEXT", "BEHAVIOR", "EXTRA", "APPLY", "SAVE", "DONE");
        audit("net.rebornaddon.jutsu.client.GuiStoreAdmin",
                ranges(range("ENTRY_BASE", 20), range("SECTION_ENTRY_BASE", 20)),
                "CATEGORY_FILTER", "STATE_FILTER", "PREVIOUS_PAGE", "NEXT_PAGE", "PROPERTY",
                "BOOLEAN_VALUE", "SAVE", "RESET_PROPERTY", "RESET_ENTRY", "RESET_ALL",
                "DONE", "RELOAD", "TAB_JUTSUS", "TAB_STORE", "TAB_LIMITS", "TAB_SECTIONS",
                "SECTION_VALUE", "SECTION_SAVE", "SECTION_RESET", "QUOTA_LOAD", "QUOTA_KIND",
                "QUOTA_SET", "QUOTA_ADJUST", "QUOTA_RESET", "QUOTA_RESET_ALL");
        audit("net.rebornaddon.mode.client.GuiModeCombinationEditor",
                ranges(range("ROW_BASE", 20)), "PREVIOUS", "NEXT", "SAVE", "CANCEL");
        audit("net.rebornaddon.village.client.GuiVillageSelect",
                ranges(range("BUTTON_BASE", 8)));
    }

    private static void audit(String className, RangeSpec[] specifications,
                              String... scalarNames) throws Exception {
        Class<?> type = Class.forName(className);
        List<Range> ranges = new ArrayList<Range>();
        for (RangeSpec specification : specifications) {
            int start = value(type, specification.fieldName);
            ranges.add(new Range(specification.fieldName, start, start + specification.capacity));
        }
        for (int i = 0; i < ranges.size(); i++) {
            for (int j = i + 1; j < ranges.size(); j++) {
                assertFalse(className + ": " + ranges.get(i).name + " overlaps " + ranges.get(j).name,
                        ranges.get(i).overlaps(ranges.get(j)));
            }
        }
        Map<Integer, String> scalars = new HashMap<Integer, String>();
        for (String scalarName : scalarNames) {
            int scalar = value(type, scalarName);
            String previous = scalars.put(Integer.valueOf(scalar), scalarName);
            assertFalse(className + ": " + scalarName + " duplicates " + previous, previous != null);
            for (Range range : ranges) {
                assertFalse(className + ": " + scalarName + " is inside " + range.name,
                        range.contains(scalar));
            }
        }
    }

    private static int value(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        assertFalse(type.getName() + ": " + name + " is not static",
                !Modifier.isStatic(field.getModifiers()));
        return field.getInt(null);
    }

    private static RangeSpec[] ranges(RangeSpec... values) {
        return values;
    }

    private static RangeSpec range(String fieldName, int capacity) {
        return new RangeSpec(fieldName, capacity);
    }

    private static final class RangeSpec {
        private final String fieldName;
        private final int capacity;

        private RangeSpec(String fieldName, int capacity) {
            this.fieldName = fieldName;
            this.capacity = capacity;
        }
    }

    private static final class Range {
        private final String name;
        private final int start;
        private final int end;

        private Range(String name, int start, int end) {
            this.name = name;
            this.start = start;
            this.end = end;
        }

        private boolean contains(int value) {
            return value >= start && value < end;
        }

        private boolean overlaps(Range other) {
            return start < other.end && other.start < end;
        }
    }
}
