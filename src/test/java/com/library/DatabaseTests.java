package com.library;

import com.library.db.BookDraft;
import com.library.db.LibraryDatabase;
import com.library.db.LoanFilter;
import com.library.db.MemberDraft;
import com.library.model.Book;
import com.library.model.Loan;
import com.library.model.LoanStatus;
import com.library.model.LoanView;
import com.library.model.Member;
import com.library.model.MemberView;
import com.library.model.Stats;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.List;

import static com.library.TestHarness.assertContains;
import static com.library.TestHarness.assertEquals;
import static com.library.TestHarness.assertFalse;
import static com.library.TestHarness.assertNotNull;
import static com.library.TestHarness.assertNull;
import static com.library.TestHarness.assertThrows;
import static com.library.TestHarness.assertTrue;

/** Tests for the data layer and its circulation rules. */
public final class DatabaseTests {

    private DatabaseTests() {
    }

    /** A fresh library with one 2-copy title and one student member. */
    private record Fixture(LibraryDatabase db, int book, int member) implements AutoCloseable {
        @Override
        public void close() {
            db.close();
        }

        /** Moves a loan's dates into the past so it reads as overdue. */
        void backdate(int loanId, int days) throws Exception {
            Loan loan = db.getLoan(loanId);
            LocalDate issue = db.today().minusDays(days);
            LocalDate due = issue.plusDays(db.loanDays());
            try (PreparedStatement ps = db.connectionForSeeding().prepareStatement(
                    "UPDATE loans SET issue_date = ?, due_date = ? WHERE id = ?")) {
                ps.setString(1, issue.toString());
                ps.setString(2, due.toString());
                ps.setInt(3, loanId);
                ps.executeUpdate();
            }
            assertNotNull(loan, "the loan being backdated should exist");
        }
    }

    private static Fixture fixture() {
        LibraryDatabase db = LibraryDatabase.inMemory();
        int book = db.addBook(BookDraft.of("Dune", "Frank Herbert", "9780441013593",
                "Sci-Fi", "Ace Books", 1965, "F-11", 2));
        int member = db.addMember(MemberDraft.of("Asha Rao", "Student",
                "asha@example.com", "9876543210"));
        return new Fixture(db, book, member);
    }

    public static int run() {
        TestHarness t = new TestHarness("Database tests");
        books(t);
        members(t);
        circulation(t);
        persistence(t);
        return t.report();
    }

    // -------------------------------------------------------------------- books
    private static void books(TestHarness t) {
        t.test("a new book has every copy on the shelf", () -> {
            try (Fixture f = fixture()) {
                Book book = f.db().getBook(f.book());
                assertEquals(2, book.totalCopies(), "total copies");
                assertEquals(2, book.availableCopies(), "available copies");
                assertEquals(0, book.copiesOnLoan(), "copies on loan");
            }
        });

        t.test("title and author are required", () -> {
            try (Fixture f = fixture()) {
                assertThrows(() -> f.db().addBook(BookDraft.of("", "Nobody", null,
                        "General", null, null, null, 1)), "Title", "blank title");
                assertThrows(() -> f.db().addBook(BookDraft.of("Untitled", "  ", null,
                        "General", null, null, null, 1)), "Author", "blank author");
            }
        });

        t.test("a duplicate ISBN is rejected, dashes and all", () -> {
            try (Fixture f = fixture()) {
                assertThrows(() -> f.db().addBook(BookDraft.of("Dune copy", "F. Herbert",
                        "978-0441013593", "Sci-Fi", null, null, null, 1)),
                        "ISBN", "duplicate ISBN");
            }
        });

        t.test("an ISBN must be 10 or 13 digits", () -> {
            try (Fixture f = fixture()) {
                assertThrows(() -> f.db().addBook(BookDraft.of("X", "Y", "12345",
                        "General", null, null, null, 1)), "ISBN", "short ISBN");
            }
        });

        t.test("the year must be a plausible number", () -> {
            try (Fixture f = fixture()) {
                assertThrows(() -> f.db().addBook(new BookDraft("X", "Y", "", "General",
                        "", "nineteen", "", "1")), "Year", "non-numeric year");
                assertThrows(() -> f.db().addBook(BookDraft.of("X", "Y", null, "General",
                        null, f.db().today().getYear() + 5, null, 1)),
                        "Year", "year too far ahead");
            }
        });

        t.test("a book needs at least one copy", () -> {
            try (Fixture f = fixture()) {
                assertThrows(() -> f.db().addBook(BookDraft.of("Phantom", "Nobody", null,
                        "General", null, null, null, 0)), "copy", "zero copies");
            }
        });

        t.test("raising the total keeps the issued copies issued", () -> {
            try (Fixture f = fixture()) {
                f.db().issueBook(f.book(), f.member(), null);
                f.db().updateBook(f.book(), BookDraft.of("Dune", "Frank Herbert",
                        "9780441013593", "Sci-Fi", null, 1965, null, 5));
                Book book = f.db().getBook(f.book());
                assertEquals(5, book.totalCopies(), "total after update");
                assertEquals(4, book.availableCopies(), "available after update");
            }
        });

        t.test("the total cannot drop below the copies on loan", () -> {
            try (Fixture f = fixture()) {
                int other = f.db().addMember(MemberDraft.of("Tomas Novak", "Public",
                        null, null));
                f.db().issueBook(f.book(), f.member(), null);
                f.db().issueBook(f.book(), other, null);
                assertThrows(() -> f.db().updateBook(f.book(), BookDraft.of("Dune",
                        "Frank Herbert", "9780441013593", "Sci-Fi", null, 1965, null, 1)),
                        "2 copies are currently issued", "shrinking below issued");
            }
        });

        t.test("a book on loan cannot be deleted", () -> {
            try (Fixture f = fixture()) {
                f.db().issueBook(f.book(), f.member(), null);
                assertThrows(() -> f.db().deleteBook(f.book()), "on loan", "delete guard");
            }
        });

        t.test("search matches title, author and ISBN", () -> {
            try (Fixture f = fixture()) {
                assertEquals(1, f.db().listBooks("herbert", "All", false).size(), "author");
                assertEquals(1, f.db().listBooks("9780441013593", "All", false).size(),
                        "ISBN");
                assertEquals(0, f.db().listBooks("nothing here", "All", false).size(),
                        "no match");
            }
        });

        t.test("the category filter narrows the list", () -> {
            try (Fixture f = fixture()) {
                f.db().addBook(BookDraft.of("Matilda", "Roald Dahl", null, "Children",
                        null, 1988, null, 2));
                assertEquals(2, f.db().listBooks("", "All", false).size(), "all");
                assertEquals(1, f.db().listBooks("", "Children", false).size(), "children");
                assertEquals(List.of("Children", "Sci-Fi"), f.db().categories(),
                        "categories");
            }
        });
    }

    // ------------------------------------------------------------------ members
    private static void members(TestHarness t) {
        t.test("member codes are assigned in sequence", () -> {
            try (Fixture f = fixture()) {
                assertEquals("M0001", f.db().getMember(f.member()).code(), "first code");
                int second = f.db().addMember(MemberDraft.of("Rahul Verma", "Student",
                        null, null));
                assertEquals("M0002", f.db().getMember(second).code(), "second code");
            }
        });

        t.test("a duplicate member code is rejected", () -> {
            try (Fixture f = fixture()) {
                assertThrows(() -> f.db().addMember(new MemberDraft("M0001", "Someone",
                        "", "", "", "Public", "", true, "")), "member ID", "duplicate code");
            }
        });

        t.test("email and phone are checked", () -> {
            try (Fixture f = fixture()) {
                assertThrows(() -> f.db().addMember(MemberDraft.of("Bad Email", "Public",
                        "not-an-email", null)), "email", "bad email");
                assertThrows(() -> f.db().addMember(MemberDraft.of("Bad Phone", "Public",
                        null, "call me")), "phone", "bad phone");
            }
        });

        t.test("the limit follows the membership type unless overridden", () -> {
            try (Fixture f = fixture()) {
                int teacher = f.db().addMember(MemberDraft.of("Priya Nair", "Teacher",
                        null, null));
                assertEquals(6, f.db().getMember(teacher).borrowingLimit(), "teacher");
                assertEquals(3, f.db().getMember(f.member()).borrowingLimit(), "student");

                f.db().updateMember(f.member(), new MemberDraft("M0001", "Asha Rao",
                        "asha@example.com", "", "", "Student", "", true, "1"));
                assertEquals(1, f.db().getMember(f.member()).borrowingLimit(), "override");
            }
        });

        t.test("a member holding books cannot be deleted", () -> {
            try (Fixture f = fixture()) {
                f.db().issueBook(f.book(), f.member(), null);
                assertThrows(() -> f.db().deleteMember(f.member()), "on loan",
                        "delete guard");
            }
        });

        t.test("the member list reports open loans and dues", () -> {
            try (Fixture f = fixture()) {
                int loan = f.db().issueBook(f.book(), f.member(), null);
                f.backdate(loan, 20);
                f.db().returnBook(loan, false);

                MemberView view = f.db().listMembers().stream()
                        .filter(m -> m.id() == f.member()).findFirst().orElseThrow();
                assertEquals(0, view.onLoan(), "nothing still out");
                assertTrue(view.dues() > 0, "a fine is owed");
            }
        });

        t.test("dues include fines still accruing on an open loan", () -> {
            try (Fixture f = fixture()) {
                int loan = f.db().issueBook(f.book(), f.member(), null);
                f.backdate(loan, f.db().loanDays() + 5);
                MemberView view = f.db().listMembers().stream()
                        .filter(m -> m.id() == f.member()).findFirst().orElseThrow();
                assertEquals(5 * f.db().finePerDay(), view.dues(), "accruing dues");
            }
        });

        t.test("inactive members can be filtered out", () -> {
            try (Fixture f = fixture()) {
                f.db().updateMember(f.member(), new MemberDraft("M0001", "Asha Rao",
                        "", "", "", "Student", "", false, ""));
                assertEquals(1, f.db().listMembers("", false).size(), "all members");
                assertEquals(0, f.db().listMembers("", true).size(), "active only");
            }
        });
    }

    // -------------------------------------------------------------- circulation
    private static void circulation(TestHarness t) {
        t.test("issuing takes a copy off the shelf and returning puts it back", () -> {
            try (Fixture f = fixture()) {
                int loan = f.db().issueBook(f.book(), f.member(), null);
                assertEquals(1, f.db().getBook(f.book()).availableCopies(), "after issue");
                assertEquals(0.0, f.db().returnBook(loan, false), "no fine");
                assertEquals(2, f.db().getBook(f.book()).availableCopies(), "after return");
            }
        });

        t.test("the due date uses the configured loan period", () -> {
            try (Fixture f = fixture()) {
                f.db().setSetting("loan_days", "7");
                Loan loan = f.db().getLoan(f.db().issueBook(f.book(), f.member(), null));
                assertEquals(f.db().today().plusDays(7), loan.dueDate(), "due date");
            }
        });

        t.test("an explicit period overrides the setting", () -> {
            try (Fixture f = fixture()) {
                Loan loan = f.db().getLoan(f.db().issueBook(f.book(), f.member(), 3));
                assertEquals(f.db().today().plusDays(3), loan.dueDate(), "due date");
            }
        });

        t.test("one member cannot hold two copies of the same title", () -> {
            try (Fixture f = fixture()) {
                f.db().issueBook(f.book(), f.member(), null);
                assertThrows(() -> f.db().issueBook(f.book(), f.member(), null),
                        "already has a copy", "duplicate title");
            }
        });

        t.test("nothing can be issued once the shelf is empty", () -> {
            try (Fixture f = fixture()) {
                int second = f.db().addMember(MemberDraft.of("Tomas Novak", "Public",
                        null, null));
                int third = f.db().addMember(MemberDraft.of("Mei Lin Chen", "Public",
                        null, null));
                f.db().issueBook(f.book(), f.member(), null);
                f.db().issueBook(f.book(), second, null);
                assertThrows(() -> f.db().issueBook(f.book(), third, null), "shelf",
                        "no copies left");
            }
        });

        t.test("the borrowing limit is enforced", () -> {
            try (Fixture f = fixture()) {
                f.db().updateMember(f.member(), new MemberDraft("M0001", "Asha Rao",
                        "", "", "", "Student", "", true, "1"));
                f.db().issueBook(f.book(), f.member(), null);
                int other = f.db().addBook(BookDraft.of("Clean Code", "Robert Martin",
                        null, "Technology", null, 2008, null, 1));
                assertThrows(() -> f.db().issueBook(other, f.member(), null), "permitted",
                        "at limit");
            }
        });

        t.test("an inactive member cannot borrow", () -> {
            try (Fixture f = fixture()) {
                f.db().updateMember(f.member(), new MemberDraft("M0001", "Asha Rao",
                        "", "", "", "Student", "", false, ""));
                assertThrows(() -> f.db().issueBook(f.book(), f.member(), null),
                        "inactive", "inactive member");
            }
        });

        t.test("an overdue book blocks further borrowing", () -> {
            try (Fixture f = fixture()) {
                int loan = f.db().issueBook(f.book(), f.member(), null);
                f.backdate(loan, 30);
                int other = f.db().addBook(BookDraft.of("Sapiens", "Y. N. Harari", null,
                        "History", null, 2011, null, 1));
                assertThrows(() -> f.db().issueBook(other, f.member(), null), "overdue",
                        "overdue guard");
            }
        });

        t.test("the fine is per day late and is recorded on return", () -> {
            try (Fixture f = fixture()) {
                f.db().setSetting("fine_per_day", "2.50");
                int loan = f.db().issueBook(f.book(), f.member(), null);
                f.backdate(loan, f.db().loanDays() + 4);

                assertEquals(10.0, f.db().fineFor(f.db().getLoan(loan)), "accrued");
                assertEquals(10.0, f.db().returnBook(loan, false), "charged");
                Loan stored = f.db().getLoan(loan);
                assertEquals(10.0, stored.fine(), "stored fine");
                assertFalse(stored.finePaid(), "fine starts unpaid");
            }
        });

        t.test("a fine can be waived at the desk", () -> {
            try (Fixture f = fixture()) {
                int loan = f.db().issueBook(f.book(), f.member(), null);
                f.backdate(loan, f.db().loanDays() + 3);
                assertEquals(0.0, f.db().returnBook(loan, true), "waived");
                assertEquals(0.0, f.db().getLoan(loan).fine(), "nothing stored");
            }
        });

        t.test("a fine can be settled once only", () -> {
            try (Fixture f = fixture()) {
                int loan = f.db().issueBook(f.book(), f.member(), null);
                f.backdate(loan, f.db().loanDays() + 1);
                f.db().returnBook(loan, false);
                f.db().payFine(loan);
                assertTrue(f.db().getLoan(loan).finePaid(), "marked paid");
                assertThrows(() -> f.db().payFine(loan), "already", "paying twice");
            }
        });

        t.test("an on-time return owes nothing", () -> {
            try (Fixture f = fixture()) {
                int loan = f.db().issueBook(f.book(), f.member(), null);
                assertEquals(0.0, f.db().returnBook(loan, false), "no fine");
                assertTrue(f.db().getLoan(loan).finePaid(), "nothing left to pay");
            }
        });

        t.test("a copy cannot be returned twice", () -> {
            try (Fixture f = fixture()) {
                int loan = f.db().issueBook(f.book(), f.member(), null);
                f.db().returnBook(loan, false);
                assertThrows(() -> f.db().returnBook(loan, false), "already been returned",
                        "double return");
            }
        });

        t.test("a loan can be renewed twice, then no more", () -> {
            try (Fixture f = fixture()) {
                int loan = f.db().issueBook(f.book(), f.member(), null);
                LocalDate first = f.db().renewLoan(loan, 7);
                assertEquals(f.db().today().plusDays(f.db().loanDays() + 7), first,
                        "new due date");
                f.db().renewLoan(loan, 7);
                assertThrows(() -> f.db().renewLoan(loan, 7), "renewed", "third renewal");
                assertEquals(2, f.db().getLoan(loan).renewals(), "renewal count");
            }
        });

        t.test("an overdue loan cannot be renewed", () -> {
            try (Fixture f = fixture()) {
                int loan = f.db().issueBook(f.book(), f.member(), null);
                f.backdate(loan, f.db().loanDays() + 2);
                assertThrows(() -> f.db().renewLoan(loan, null), "returned, not renewed",
                        "renew guard");
            }
        });

        t.test("the status filters separate on-loan, overdue and returned", () -> {
            try (Fixture f = fixture()) {
                int onTime = f.db().issueBook(f.book(), f.member(), null);
                int other = f.db().addMember(MemberDraft.of("Tomas Novak", "Public",
                        null, null));
                int late = f.db().issueBook(f.book(), other, null);
                f.backdate(late, 40);

                assertEquals(List.of(late), ids(f.db().listLoans(LoanFilter.OVERDUE, "")),
                        "overdue");
                assertEquals(List.of(onTime), ids(f.db().listLoans(LoanFilter.ON_LOAN, "")),
                        "on loan");
                assertEquals(2, f.db().listLoans(LoanFilter.ACTIVE, "").size(), "active");

                f.db().returnBook(late, false);
                assertEquals(List.of(late), ids(f.db().listLoans(LoanFilter.RETURNED, "")),
                        "returned");
                assertEquals(List.of(late),
                        ids(f.db().listLoans(LoanFilter.UNPAID_FINES, "")), "unpaid fines");
            }
        });

        t.test("a loan view knows its status and days late", () -> {
            try (Fixture f = fixture()) {
                int loan = f.db().issueBook(f.book(), f.member(), null);
                f.backdate(loan, f.db().loanDays() + 3);
                LoanView view = f.db().getLoanDetail(loan);
                assertEquals(LoanStatus.OVERDUE, view.status(), "status");
                assertEquals(3L, view.daysLate(), "days late");
                assertEquals("Dune", view.bookTitle(), "joined title");
                assertEquals("Asha Rao", view.memberName(), "joined member");
                assertEquals("9876543210", view.memberContact(), "contact");
            }
        });

        t.test("the dashboard figures follow circulation", () -> {
            try (Fixture f = fixture()) {
                f.db().issueBook(f.book(), f.member(), null);
                Stats stats = f.db().stats();
                assertEquals(1, stats.titles(), "titles");
                assertEquals(2, stats.copies(), "copies");
                assertEquals(1, stats.copiesOnLoan(), "on loan");
                assertEquals(1, stats.loansActive(), "active loans");
                assertEquals(1, stats.issuedToday(), "issued today");
                assertEquals(0, stats.loansOverdue(), "overdue");
            }
        });

        t.test("outstanding dues include fines still running", () -> {
            try (Fixture f = fixture()) {
                int loan = f.db().issueBook(f.book(), f.member(), null);
                f.backdate(loan, f.db().loanDays() + 5);
                assertEquals(5 * f.db().finePerDay(), f.db().stats().duesOutstanding(),
                        "dues");
            }
        });

        t.test("the most-borrowed list counts every issue", () -> {
            try (Fixture f = fixture()) {
                int loan = f.db().issueBook(f.book(), f.member(), null);
                f.db().returnBook(loan, false);
                f.db().issueBook(f.book(), f.member(), null);
                assertEquals(1, f.db().popularBooks(5).size(), "one title");
                assertEquals(2, f.db().popularBooks(5).get(0).timesIssued(), "twice");
            }
        });
    }

    private static List<Integer> ids(List<LoanView> loans) {
        return loans.stream().map(LoanView::id).toList();
    }

    // -------------------------------------------------------- persistence / IO
    private static void persistence(TestHarness t) {
        t.test("data survives reopening the file", () -> {
            Path folder = Files.createTempDirectory("lms-test");
            Path file = folder.resolve("library.db");
            try (LibraryDatabase first = new LibraryDatabase(file)) {
                int book = first.addBook(BookDraft.of("Matilda", "Roald Dahl", null,
                        "Children", null, 1988, null, 1));
                int member = first.addMember(MemberDraft.of("Liam O'Connor", "Student",
                        null, null));
                first.issueBook(book, member, null);
            }
            try (LibraryDatabase second = new LibraryDatabase(file)) {
                assertEquals(1, second.stats().loansActive(), "loan survived");
                assertEquals("Matilda", second.listBooks().get(0).title(), "book survived");
                assertEquals("Liam O'Connor", second.listMembers().get(0).member().name(),
                        "member survived");
            }
        });

        t.test("settings are stored and read back", () -> {
            try (Fixture f = fixture()) {
                f.db().setSetting("library_name", "Riverside Library");
                f.db().setSetting("loan_days", "21");
                f.db().setSetting("fine_per_day", "3.50");
                assertEquals("Riverside Library", f.db().libraryName(), "name");
                assertEquals(21, f.db().loanDays(), "loan days");
                assertEquals(3.5, f.db().finePerDay(), "fine per day");
            }
        });

        t.test("a CSV export contains the records", () -> {
            try (Fixture f = fixture()) {
                Path folder = Files.createTempDirectory("lms-csv");
                Path target = folder.resolve("books.csv");
                assertEquals(1, f.db().exportCsv("books", target), "rows written");
                String text = Files.readString(target);
                assertContains(text, "Dune", "exported title");
                assertContains(text, "title", "header row");
            }
        });

        t.test("a backup can be reopened as a database", () -> {
            try (Fixture f = fixture()) {
                f.db().issueBook(f.book(), f.member(), null);
                Path folder = Files.createTempDirectory("lms-backup");
                Path target = f.db().backup(folder.resolve("copy.db"));
                assertTrue(Files.size(target) > 0, "backup is not empty");
                try (LibraryDatabase restored = new LibraryDatabase(target)) {
                    assertEquals(1, restored.stats().loansActive(), "restored loan");
                }
            }
        });

        t.test("the sample data loads with consistent copy counts", () -> {
            try (LibraryDatabase db = LibraryDatabase.inMemory()) {
                SampleData.Counts counts = SampleData.load(db);
                assertEquals(16, counts.books(), "books");
                assertEquals(8, counts.members(), "members");
                assertTrue(counts.loans() > 10, "loans");

                for (Book book : db.listBooks()) {
                    long out = db.listLoans(LoanFilter.ACTIVE, "").stream()
                            .filter(v -> v.loan().bookId() == book.id()).count();
                    assertEquals(book.totalCopies() - (int) out, book.availableCopies(),
                            "availability for " + book.title());
                }
                assertTrue(!db.listLoans(LoanFilter.OVERDUE, "").isEmpty(),
                        "sample set includes overdue loans");
                assertTrue(db.stats().duesOutstanding() > 0, "sample set includes fines");
            }
        });

        t.test("an unknown member or book id is reported, not crashed on", () -> {
            try (Fixture f = fixture()) {
                assertNull(f.db().getBook(9999), "missing book");
                assertNull(f.db().getMember(9999), "missing member");
                assertNull(f.db().getLoan(9999), "missing loan");
                assertThrows(() -> f.db().issueBook(9999, f.member(), null), "book",
                        "issuing a missing book");
                assertThrows(() -> f.db().returnBook(9999, false), "no longer exists",
                        "returning a missing loan");
            }
        });

        t.test("dates are parsed from the spellings a form may contain", () -> {
            try (Fixture f = fixture()) {
                LocalDate expected = LocalDate.of(2026, 3, 9);
                assertEquals(expected, f.db().parseDate("2026-03-09"), "ISO");
                assertEquals(expected, f.db().parseDate("09-03-2026"), "day first");
                assertEquals(expected, f.db().parseDate("09/03/2026"), "slashes");
                assertEquals(f.db().today(), f.db().parseDate(""), "empty means today");
                assertThrows(() -> f.db().parseDate("not a date"), "not a date I recognise",
                        "nonsense date");
            }
        });

        t.test("a member record round-trips through an update", () -> {
            try (Fixture f = fixture()) {
                f.db().updateMember(f.member(), new MemberDraft("M0001", "Asha Rao",
                        "asha.rao@example.com", "9876543210", "12 Nehru Street",
                        "Teacher", "2025-01-15", true, "4"));
                Member member = f.db().getMember(f.member());
                assertEquals("asha.rao@example.com", member.email(), "email");
                assertEquals("12 Nehru Street", member.address(), "address");
                assertEquals("Teacher", member.type().label(), "type");
                assertEquals(LocalDate.of(2025, 1, 15), member.joinDate(), "join date");
                assertEquals(4, member.borrowingLimit(), "limit");
            }
        });
    }
}
