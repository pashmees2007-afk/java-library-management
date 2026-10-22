package com.library.db;

/** The status filter offered at the circulation desk. */
public enum LoanFilter {
    ACTIVE("Active"),
    ON_LOAN("On loan"),
    OVERDUE("Overdue"),
    RETURNED("Returned"),
    UNPAID_FINES("Unpaid fines"),
    ALL("All");

    private final String label;

    LoanFilter(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static LoanFilter fromLabel(String text) {
        for (LoanFilter filter : values()) {
            if (filter.label.equalsIgnoreCase(String.valueOf(text).trim())) {
                return filter;
            }
        }
        return ALL;
    }

    public static String[] labels() {
        LoanFilter[] all = values();
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
