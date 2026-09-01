package net.rebornaddon.compat;

import net.minecraft.item.Item;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CooldownTrackerCompatibilityTest {
    @Test
    public void fallsBackToRuntimeSetMethodWhenRemoveMethodIsMissing() {
        Item item = new Item();
        SetOnlyTracker tracker = new SetOnlyTracker();
        tracker.cooldowns.put(item, Integer.valueOf(40));

        assertTrue(CooldownTrackerCompatibility.hasCooldown(tracker, item));
        assertTrue(CooldownTrackerCompatibility.clearTracker(tracker, item));
        assertFalse(CooldownTrackerCompatibility.hasCooldown(tracker, item));
    }

    @Test
    public void clearsBackingMapWhenRuntimeMethodsAreMissing() {
        Item item = new Item();
        FieldOnlyTracker tracker = new FieldOnlyTracker();
        tracker.cooldowns.put(item, new Object());

        assertTrue(CooldownTrackerCompatibility.hasCooldown(tracker, item));
        assertTrue(CooldownTrackerCompatibility.clearTracker(tracker, item));
        assertFalse(CooldownTrackerCompatibility.hasCooldown(tracker, item));
    }

    public static final class SetOnlyTracker {
        private final Map<Item, Integer> cooldowns = new HashMap<Item, Integer>();

        public boolean active(Item item) {
            return cooldowns.containsKey(item);
        }

        public void set(Item item, int ticks) {
            if (ticks <= 0) cooldowns.remove(item);
            else cooldowns.put(item, Integer.valueOf(ticks));
        }
    }

    private static final class FieldOnlyTracker {
        private final Map<Item, Object> cooldowns = new HashMap<Item, Object>();
    }
}
