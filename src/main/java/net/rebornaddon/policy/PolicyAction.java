package net.rebornaddon.policy;

import java.util.Locale;

public enum PolicyAction {
    JEI,
    CREATIVE,
    STORE,
    CRAFT,
    USE,
    EQUIP,
    DROP,
    PLACE,
    SPAWN,
    TRADE,
    MAIL,
    COMMAND;

    public static PolicyAction parse(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
