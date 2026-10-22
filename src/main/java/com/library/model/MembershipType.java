package com.library.model;

/** Membership categories and the number of books each may hold by default. */
public enum MembershipType {
    STUDENT("Student", 3),
    TEACHER("Teacher", 6),
    PUBLIC("Public", 2),
    STAFF("Staff", 5);

    private final String label;
    private final int defaultLimit;

    MembershipType(String label, int defaultLimit) {
        this.label = label;
        this.defaultLimit = defaultLimit;
    }

    public String label() {
        return label;
    }

    /** Books a member of this type may hold unless their record overrides it. */
    public int defaultLimit() {
        return defaultLimit;
    }

    /** Parses the label shown in the interface; falls back to {@link #PUBLIC}. */
    public static MembershipType fromLabel(String text) {
        if (text != null) {
            for (MembershipType type : values()) {
                if (type.label.equalsIgnoreCase(text.trim())) {
                    return type;
                }
            }
        }
        return PUBLIC;
    }

    /** Labels in declaration order, for combo boxes. */
    public static String[] labels() {
        MembershipType[] all = values();
        String[] out = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            out[i] = all[i].label;
        }
        return out;
    }

    @Override
    public String toString() {
        return label;
    }
}
