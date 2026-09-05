package net.rebornaddon.gui.tabs;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdminTabNavigationTest {
    @Test
    public void pageAndEditorControlsCannotBeMistakenForSectionTabs() {
        assertFalse(AdminTab.isSectionButtonId(910));
        assertFalse(AdminTab.isSectionButtonId(911));
        assertFalse(AdminTab.isSectionButtonId(912));
        assertFalse(AdminTab.isSectionButtonId(913));
        assertTrue(AdminTab.isSectionButtonId(6000));
        assertTrue(AdminTab.isSectionButtonId(6013));
        assertFalse(AdminTab.isSectionButtonId(6014));
    }

    @Test
    public void everyGeneratedAdminButtonRangeIsDisjoint() throws Exception {
        Range[] ranges = new Range[] {
                range("sections", value("SECTION_BASE"), 14),
                range("jutsu properties", value("JUTSU_PROPERTY_BASE"), 16),
                range("mode properties", value("MODE_PROPERTY_BASE"), 16),
                range("list rows", value("ROW_BASE"), 100),
                range("ranked blocked rows", value("RANKED_BLOCKED_BASE"), 100),
                range("performance rows", value("PERFORMANCE_ROW_BASE"), 100),
                range("chakra settings", value("CHAKRA_SETTING_BASE"), 10)
        };
        for (int i = 0; i < ranges.length; i++) {
            for (int j = i + 1; j < ranges.length; j++) {
                assertFalse(ranges[i].name + " overlaps " + ranges[j].name,
                        ranges[i].overlaps(ranges[j]));
            }
        }
        String[] scalarNames = {
                "PREVIOUS_PAGE", "NEXT_PAGE", "PROPERTY", "BOOLEAN_VALUE", "SAVE",
                "RESET_PROPERTY", "RESET_ENTRY", "RESET_ALL", "RELOAD", "QUOTA_LOAD",
                "QUOTA_KIND", "QUOTA_SET", "QUOTA_ADD", "QUOTA_RESET", "QUEST_ACTION",
                "QUEST_APPLY", "JUTSU_SCOPE", "JUTSU_CATEGORY", "OPEN_EFFECT_RULES",
                "OPEN_MODE_COMBINATIONS", "RANKED_START", "RANKED_PAUSE", "RANKED_END",
                "RANKED_LEADERBOARD", "RANKED_SAVE_NAME", "RANKED_SAVE_DURATION",
                "RANKED_PLACEMENT_PREVIOUS", "RANKED_PLACEMENT_NEXT",
                "RANKED_PLACEMENT_ENABLED", "RANKED_ADD_ITEM", "RANKED_CLEAR_ITEMS",
                "RANKED_ADD_COMMAND", "RANKED_CLEAR_COMMANDS", "RANKED_SAVE_GROUP",
                "RANKED_BLOCK_PLAYER", "RANKED_UNBLOCK_PLAYER", "RANKED_RELOAD",
                "EXAM_RANK_PREVIOUS", "EXAM_RANK_NEXT", "EXAM_RANK_APPLY",
                "COOLDOWN_CLEAR_PLAYER", "COOLDOWN_CLEAR_ALL", "PERFORMANCE_REFRESH",
                "PERFORMANCE_PROFILE", "PERFORMANCE_OVERLAY", "PERFORMANCE_PREVIOUS",
                "PERFORMANCE_NEXT", "PERFORMANCE_TELEPORT", "CHAKRA_SAVE", "CHAKRA_RESET",
                "CHAKRA_RELOAD"
        };
        for (String scalarName : scalarNames) {
            int scalar = value(scalarName);
            for (Range range : ranges) {
                assertFalse(scalarName + " is inside " + range.name, range.contains(scalar));
            }
        }
    }

    private static int value(String name) throws Exception {
        java.lang.reflect.Field field = AdminTab.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(null);
    }

    private static Range range(String name, int start, int length) {
        return new Range(name, start, start + length);
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
