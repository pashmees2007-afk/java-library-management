package com.library.model;

/** Where a loan stands right now. */
public enum LoanStatus {
    ON_LOAN("On loan"),
    OVERDUE("Overdue"),
    RETURNED("Returned");

    private final String label;

    LoanStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
