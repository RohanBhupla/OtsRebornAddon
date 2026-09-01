package net.rebornaddon.jutsu.client;

public final class ClientAdminAccess {
    private static boolean operator;
    private static int revision;

    private ClientAdminAccess() {
    }

    public static boolean isOperator() {
        return operator;
    }

    public static int revision() {
        return revision;
    }

    public static void update(boolean value) {
        if (operator != value) {
            operator = value;
            revision++;
        }
    }

    public static void reset() {
        operator = false;
        revision++;
    }
}
