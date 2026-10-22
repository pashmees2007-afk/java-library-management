package com.library.model;

import java.time.LocalDate;

/**
 * A loan joined with the book and member it refers to, plus the fine accrued as
 * of the moment it was read. This is what the tables display.
 */
public record LoanView(
        Loan loan,
        String bookTitle,
        String bookAuthor,
        String memberName,
        String memberCode,
        String memberContact,
        LoanStatus status,
        long daysLate,
        double accruedFine) {

    public int id() {
        return loan.id();
    }

    public boolean isOpen() {
        return loan.isOpen();
    }

    public LocalDate dueDate() {
        return loan.dueDate();
    }

    public String memberDisplay() {
        return memberName + " (" + memberCode + ")";
    }
}
