package com.library.model;

import java.time.LocalDate;

/**
 * One catalogued title. {@code availableCopies} is what is on the shelf now;
 * the difference from {@code totalCopies} is what is out on loan.
 */
public record Book(
        int id,
        String title,
        String author,
        String isbn,
        String category,
        String publisher,
        Integer year,
        String shelf,
        int totalCopies,
        int availableCopies,
        LocalDate addedOn) {

    public int copiesOnLoan() {
        return totalCopies - availableCopies;
    }

    public boolean onShelf() {
        return availableCopies > 0;
    }

    /** "Title — Author", used in pickers and messages. */
    public String display() {
        return title + " — " + author;
    }
}
