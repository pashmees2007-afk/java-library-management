package com.library.model;

/** The headline numbers on the dashboard. */
public record Stats(
        int titles,
        int copies,
        int available,
        int copiesOnLoan,
        int membersTotal,
        int membersActive,
        int loansActive,
        int loansOverdue,
        int issuedToday,
        double duesOutstanding) {
}
