package com.library.db;

/**
 * Book details exactly as typed into the form. Fields stay as text so the data
 * layer can do the validating and report problems in one place.
 */
public record BookDraft(
        String title,
        String author,
        String isbn,
        String category,
        String publisher,
        String year,
        String shelf,
        String totalCopies) {

    /** Convenience for tests and sample data, where the values are already clean. */
    public static BookDraft of(String title, String author, String isbn, String category,
                               String publisher, Integer year, String shelf, int copies) {
        return new BookDraft(title, author, isbn, category, publisher,
                year == null ? "" : String.valueOf(year), shelf, String.valueOf(copies));
    }
}
