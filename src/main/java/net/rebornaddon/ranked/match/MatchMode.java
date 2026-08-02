package net.rebornaddon.ranked.match;

public enum MatchMode {
    ONE_V_ONE(1, "1v1"),
    TWO_V_TWO(2, "2v2"),
    THREE_V_THREE(3, "3v3");

    private final int teamSize;
    private final String label;

    MatchMode(int teamSize, String label) {
        this.teamSize = teamSize;
        this.label = label;
    }

    public int getTeamSize() {
        return teamSize;
    }

    public int getTotalPlayers() {
        return teamSize * 2;
    }

    public String getLabel() {
        return label;
    }

    /** Stable numeric id for network packets - don't reorder these values once used live. */
    public int getNetId() {
        switch (this) {
            case ONE_V_ONE: return 0;
            case TWO_V_TWO: return 1;
            default: return 2;
        }
    }

    public static MatchMode fromNetId(int id) {
        switch (id) {
            case 0: return ONE_V_ONE;
            case 1: return TWO_V_TWO;
            default: return THREE_V_THREE;
        }
    }
}
