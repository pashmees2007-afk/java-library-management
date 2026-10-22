package com.library;

import com.library.db.BookDraft;
import com.library.db.LibraryDatabase;
import com.library.db.MemberDraft;
import com.library.model.Book;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Demo records so the application can be explored without typing data in.
 *
 * <p>Loans are written with explicit dates rather than through
 * {@link LibraryDatabase#issueBook}, so the sample set can include overdue and
 * historic loans.
 */
public final class SampleData {

    private SampleData() {
    }

    /** How many of each kind of record a seeding run inserted. */
    public record Counts(int books, int members, int loans) {
    }

    private static final Object[][] BOOKS = {
            // title, author, isbn, category, publisher, year, shelf, copies
            {"The Pragmatic Programmer", "Andrew Hunt", "9780201616224", "Technology",
                    "Addison-Wesley", 1999, "T-01", 3},
            {"Clean Code", "Robert C. Martin", "9780132350884", "Technology",
                    "Prentice Hall", 2008, "T-02", 2},
            {"Introduction to Algorithms", "Thomas H. Cormen", "9780262033848",
                    "Mathematics", "MIT Press", 2009, "M-04", 2},
            {"Dune", "Frank Herbert", "9780441013593", "Sci-Fi", "Ace Books", 1965,
                    "F-11", 4},
            {"Project Hail Mary", "Andy Weir", "9780593135204", "Sci-Fi", "Ballantine",
                    2021, "F-12", 2},
            {"The Hobbit", "J. R. R. Tolkien", "9780547928227", "Fiction",
                    "Houghton Mifflin", 1937, "F-03", 3},
            {"Pride and Prejudice", "Jane Austen", "9780141439518", "Fiction", "Penguin",
                    1813, "F-01", 2},
            {"A Brief History of Time", "Stephen Hawking", "9780553380163", "Science",
                    "Bantam", 1988, "S-07", 2},
            {"Sapiens", "Yuval Noah Harari", "9780062316097", "History", "Harper", 2011,
                    "H-02", 3},
            {"The Wright Brothers", "David McCullough", "9781476728759", "Biography",
                    "Simon & Schuster", 2015, "B-05", 1},
            {"Thinking, Fast and Slow", "Daniel Kahneman", "9780374533557", "Non-fiction",
                    "Farrar, Straus", 2011, "N-09", 2},
            {"The Selfish Gene", "Richard Dawkins", "9780198788607", "Science", "OUP",
                    1976, "S-02", 2},
            {"Matilda", "Roald Dahl", "9780142410370", "Children", "Puffin", 1988,
                    "C-01", 4},
            {"Charlotte's Web", "E. B. White", "9780064400558", "Children", "Harper",
                    1952, "C-02", 3},
            {"Oxford English Dictionary", "Oxford Press", "9780199571123", "Reference",
                    "OUP", 2010, "R-01", 1},
            {"Python Crash Course", "Eric Matthes", "9781593279288", "Technology",
                    "No Starch Press", 2019, "T-08", 3},
    };

    private static final Object[][] MEMBERS = {
            // name, type, email, phone, address, days since joining
            {"Asha Rao", "Student", "asha.rao@example.com", "9876543210",
                    "12 Nehru Street, Pune", 320},
            {"Daniel Okafor", "Teacher", "d.okafor@example.com", "9823004411",
                    "4 Lake View Road, Pune", 540},
            {"Mei Lin Chen", "Public", "meilin.chen@example.com", "9900112233",
                    "88 Garden Lane, Mumbai", 210},
            {"Rahul Verma", "Student", "rahul.verma@example.com", "9812345678",
                    "5 Hill Road, Nashik", 95},
            {"Sofia Martinez", "Staff", "sofia.m@example.com", "9765432109",
                    "22 Park Avenue, Pune", 700},
            {"Tomas Novak", "Public", "t.novak@example.com", "9700456123",
                    "19 Rose Court, Pune", 48},
            {"Priya Nair", "Teacher", "priya.nair@example.com", "9845001122",
                    "3 Temple Street, Kochi", 410},
            {"Liam O'Connor", "Student", "liam.oc@example.com", "9798765432",
                    "7 Market Square, Pune", 30},
    };

    /**
     * book index, member index, issued days ago, loan length, returned days ago.
     * A returned value of -1 leaves the loan open.
     */
    private static final int[][] LOANS = {
            {0, 1, 6, 14, -1},      // on loan, due in 8 days
            {3, 0, 10, 14, -1},     // on loan
            {8, 2, 25, 14, -1},     // overdue by 11 days
            {1, 3, 20, 14, -1},     // overdue by 6 days
            {11, 4, 3, 14, -1},     // on loan
            {12, 6, 1, 21, -1},     // on loan
            {5, 0, 60, 14, 48},     // returned on time
            {5, 2, 44, 14, 26},     // returned late -> fine
            {3, 1, 90, 14, 75},     // returned on time
            {7, 4, 80, 14, 70},     // returned on time
            {15, 3, 70, 14, 50},    // returned late -> fine
            {3, 6, 35, 14, 20},     // returned on time
            {0, 2, 120, 14, 100},   // returned on time
            {12, 7, 15, 14, 9},     // returned on time
            {13, 6, 55, 14, 40},    // returned on time
            {10, 1, 30, 21, 12},    // returned on time
    };

    /** Inserts the demo catalogue, members and loan history. */
    public static Counts load(LibraryDatabase db) {
        List<Integer> bookIds = new ArrayList<>();
        for (Object[] row : BOOKS) {
            String isbn = (String) row[2];
            Integer existing = findId(db, "SELECT id FROM books WHERE isbn = ?", isbn);
            if (existing != null) {
                bookIds.add(existing);
                continue;
            }
            bookIds.add(db.addBook(BookDraft.of((String) row[0], (String) row[1], isbn,
                    (String) row[3], (String) row[4], (Integer) row[5], (String) row[6],
                    (Integer) row[7])));
        }

        List<Integer> memberIds = new ArrayList<>();
        for (Object[] row : MEMBERS) {
            String email = (String) row[2];
            Integer existing = findId(db, "SELECT id FROM members WHERE email = ?", email);
            if (existing != null) {
                memberIds.add(existing);
                continue;
            }
            LocalDate joined = db.today().minusDays((Integer) row[5]);
            memberIds.add(db.addMember(new MemberDraft("", (String) row[0], email,
                    (String) row[3], (String) row[4], (String) row[1], joined.toString(),
                    true, "")));
        }

        int loans = 0;
        for (int[] row : LOANS) {
            if (addLoan(db, bookIds.get(row[0]), memberIds.get(row[1]), row[2], row[3],
                    row[4])) {
                loans++;
            }
        }
        return new Counts(bookIds.size(), memberIds.size(), loans);
    }

    private static Integer findId(LibraryDatabase db, String sql, String value) {
        try (PreparedStatement ps = connection(db).prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        } catch (SQLException exc) {
            throw new com.library.db.LibraryException(
                    "Could not read existing sample records: " + exc.getMessage(), exc);
        }
    }

    /** Writes one historic or open loan, keeping the copy counts consistent. */
    private static boolean addLoan(LibraryDatabase db, int bookId, int memberId,
                                   int issuedAgo, int period, int returnedAgo) {
        LocalDate issue = db.today().minusDays(issuedAgo);
        LocalDate due = issue.plusDays(period);
        LocalDate returned = returnedAgo < 0 ? null : db.today().minusDays(returnedAgo);

        double fine = 0;
        if (returned != null && returned.isAfter(due)) {
            long late = java.time.temporal.ChronoUnit.DAYS.between(due, returned);
            fine = Math.round(late * db.finePerDay() * 100.0) / 100.0;
        }

        if (returned == null) {
            Book book = db.getBook(bookId);
            if (book == null || book.availableCopies() < 1) {
                return false;
            }
        }

        Connection connection = connection(db);
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO loans(book_id, member_id, issue_date, due_date,
                                      return_date, fine, fine_paid)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """)) {
                ps.setInt(1, bookId);
                ps.setInt(2, memberId);
                ps.setString(3, issue.toString());
                ps.setString(4, due.toString());
                ps.setString(5, returned == null ? null : returned.toString());
                ps.setDouble(6, fine);
                // An open loan owes nothing yet, so it is not "paid" either.
                ps.setInt(7, returned != null && fine == 0 ? 1 : 0);
                ps.executeUpdate();
            }
            if (returned == null) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE books SET available_copies = available_copies - 1 "
                                + "WHERE id = ?")) {
                    ps.setInt(1, bookId);
                    ps.executeUpdate();
                }
            }
            connection.commit();
            return true;
        } catch (SQLException exc) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
                // The original failure below is the one worth reporting.
            }
            throw new com.library.db.LibraryException(
                    "Could not write sample loan: " + exc.getMessage(), exc);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
                // Connection is being discarded anyway.
            }
        }
    }

    private static Connection connection(LibraryDatabase db) {
        return db.connectionForSeeding();
    }
}
