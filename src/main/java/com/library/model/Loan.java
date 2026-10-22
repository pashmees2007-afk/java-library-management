package com.library.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** One issued copy. A {@code returnDate} of {@code null} means it is still out. */
public record Loan(
        int id,
        int bookId,
        int memberId,
        LocalDate issueDate,
        LocalDate dueDate,
        LocalDate returnDate,
        int renewals,
        double fine,
        boolean finePaid) {

    public boolean isOpen() {
        return returnDate == null;
    }

    public LoanStatus status(LocalDate today) {
        if (returnDate != null) {
            return LoanStatus.RETURNED;
        }
        return dueDate.isBefore(today) ? LoanStatus.OVERDUE : LoanStatus.ON_LOAN;
    }

    /**
     * Days past the due date: measured to today while the book is out, or to the
     * day it came back once returned. Never negative.
     */
    public long daysLate(LocalDate today) {
        LocalDate end = returnDate != null ? returnDate : today;
        return Math.max(0, ChronoUnit.DAYS.between(dueDate, end));
    }
}
