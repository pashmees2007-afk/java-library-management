package com.library;

import com.library.db.LibraryDatabase;
import com.library.db.LoanFilter;
import com.library.model.Book;
import com.library.model.Loan;
import com.library.model.LoanView;
import com.library.model.Member;
import com.library.model.MemberView;
import com.library.ui.BooksPanel;
import com.library.ui.CirculationPanel;
import com.library.ui.DashboardPanel;
import com.library.ui.Dialogs;
import com.library.ui.LibraryFrame;
import com.library.ui.MembersPanel;
import com.library.ui.SettingsPanel;
import com.library.ui.Theme;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.library.TestHarness.assertContains;
import static com.library.TestHarness.assertEquals;
import static com.library.TestHarness.assertFalse;
import static com.library.TestHarness.assertNotNull;
import static com.library.TestHarness.assertTrue;

/**
 * End-to-end tests that drive the real Swing components. The window is never
 * shown and the dialogs are stubbed, so the suite runs unattended while still
 * exercising the actual panel actions.
 */
public final class UiTests {

    private UiTests() {
    }

    /** Answers the modal questions and records what the user would have seen. */
    static final class StubDialogs implements Dialogs {
        final List<String> warnings = new ArrayList<>();
        final List<String> infos = new ArrayList<>();
        boolean answer = true;
        Path saveTarget;

        @Override
        public void warn(Component parent, String title, String message) {
            warnings.add(title + ": " + message);
        }

        @Override
        public void info(Component parent, String title, String message) {
            infos.add(title + ": " + message);
        }

        @Override
        public boolean confirm(Component parent, String title, String message) {
            return answer;
        }

        @Override
        public Path chooseSaveFile(Component parent, String title, String suggestedName,
                                   String description, String extension) {
            return saveTarget;
        }

        String lastWarning() {
            return warnings.isEmpty() ? "" : warnings.get(warnings.size() - 1);
        }
    }

    /** One application window on a throwaway database seeded with the sample data. */
    private record App(LibraryFrame frame, LibraryDatabase db, StubDialogs dialogs,
                       Path file) implements AutoCloseable {

        BooksPanel books() {
            return (BooksPanel) frame.tab("Catalogue");
        }

        MembersPanel members() {
            return (MembersPanel) frame.tab("Members");
        }

        CirculationPanel desk() {
            return (CirculationPanel) frame.tab("Circulation");
        }

        DashboardPanel dashboard() {
            return (DashboardPanel) frame.tab("Dashboard");
        }

        SettingsPanel settings() {
            return (SettingsPanel) frame.tab("Settings");
        }

        @Override
        public void close() {
            onEdt(frame::quit);
            try {
                Files.deleteIfExists(file);
            } catch (Exception ignored) {
                // A leftover temp file is not worth failing a test over.
            }
        }
    }

    /** Runs a block on the event dispatch thread and waits for it. */
    private static void onEdt(Runnable block) {
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                block.run();
            } else {
                SwingUtilities.invokeAndWait(block);
            }
        } catch (Exception exc) {
            throw new IllegalStateException(exc);
        }
    }

    private static App app() throws Exception {
        Path file = Files.createTempDirectory("lms-ui").resolve("ui.db");
        LibraryDatabase db = new LibraryDatabase(file);
        SampleData.load(db);
        StubDialogs dialogs = new StubDialogs();
        AtomicReference<LibraryFrame> holder = new AtomicReference<>();
        onEdt(() -> holder.set(new LibraryFrame(db, dialogs)));
        return new App(holder.get(), db, dialogs, file);
    }

    public static int run() {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("UI tests skipped: no display available.");
            return 0;
        }
        Theme.install();
        TestHarness t = new TestHarness("UI tests");
        shell(t);
        catalogue(t);
        members(t);
        circulation(t);
        settings(t);
        return t.report();
    }

    // -------------------------------------------------------------- app shell
    private static void shell(TestHarness t) {
        t.test("every tab builds and refreshes", () -> {
            try (App app = app()) {
                assertEquals(5, app.frame().tabs().size(), "tab count");
                for (String name : new String[]{"Dashboard", "Catalogue", "Members",
                        "Circulation", "Settings"}) {
                    onEdt(() -> app.frame().showTab(name));
                    assertNotNull(app.frame().tab(name), name + " exists");
                    assertEquals(app.frame().tab(name), app.frame().currentTab(),
                            name + " is selected");
                }
                onEdt(() -> app.frame().refreshAll());
            }
        });

        t.test("the dashboard shows the sample figures", () -> {
            try (App app = app()) {
                onEdt(() -> app.dashboard().refresh());
                assertEquals("16", app.dashboard().cardValue("titles"), "titles");
                assertEquals("8", app.dashboard().cardValue("members"), "members");
                assertEquals("2", app.dashboard().cardValue("overdue"), "overdue");
                assertTrue(app.dashboard().overdueTable().rowCount() > 0,
                        "the overdue queue is populated");
                assertEquals("City Central Library", app.dashboard().headingText(),
                        "library name");
            }
        });
    }

    // -------------------------------------------------------------- catalogue
    private static void catalogue(TestHarness t) {
        t.test("searching and filtering narrow the catalogue", () -> {
            try (App app = app()) {
                BooksPanel books = app.books();
                assertEquals(16, books.table().rowCount(), "all titles");

                onEdt(() -> {
                    books.search().setText("tolkien");
                    books.refresh();
                });
                assertEquals(1, books.table().rowCount(), "search by author");

                onEdt(() -> {
                    books.search().clear();
                    books.setCategoryFilter("Children");
                    books.refresh();
                });
                assertEquals(2, books.table().rowCount(), "category filter");

                onEdt(() -> {
                    books.setCategoryFilter("All");
                    books.refresh();
                });
                assertEquals(16, books.table().rowCount(), "filter cleared");
            }
        });

        t.test("a book can be added, edited and deleted from the form", () -> {
            try (App app = app()) {
                BooksPanel books = app.books();
                onEdt(() -> {
                    books.newRecord();
                    books.form().set("title", "Test Driven Development");
                    books.form().set("author", "Kent Beck");
                    books.form().set("isbn", "9780321146533");
                    books.form().set("category", "Technology");
                    books.form().set("year", "2002");
                    books.form().set("totalCopies", "2");
                    books.save();
                });
                assertEquals(17, books.table().rowCount(), "added");

                int id = books.editingId();
                assertEquals(2, app.db().getBook(id).availableCopies(), "copies on shelf");

                onEdt(() -> {
                    books.form().set("totalCopies", "4");
                    books.save();
                });
                assertEquals(4, app.db().getBook(id).totalCopies(), "updated");

                onEdt(() -> {
                    books.table().selectId(id);
                    books.delete();
                });
                assertEquals(null, app.db().getBook(id), "deleted");
                assertEquals(16, books.table().rowCount(), "back to the sample size");
            }
        });

        t.test("an invalid book is reported and not saved", () -> {
            try (App app = app()) {
                BooksPanel books = app.books();
                onEdt(() -> {
                    books.newRecord();
                    books.form().set("title", "No Author Here");
                    books.form().set("author", "");
                    books.save();
                });
                assertContains(app.dialogs().lastWarning(), "Author", "warning text");
                assertEquals(16, books.table().rowCount(), "nothing added");
            }
        });

        t.test("deleting a book that is on loan is refused", () -> {
            try (App app = app()) {
                BooksPanel books = app.books();
                LoanView open = app.db().listLoans(LoanFilter.ACTIVE, "").get(0);
                onEdt(() -> {
                    books.table().selectId(open.loan().bookId());
                    books.delete();
                });
                assertContains(app.dialogs().lastWarning(), "on loan", "warning text");
                assertNotNull(app.db().getBook(open.loan().bookId()), "book still there");
            }
        });

        t.test("selecting a row loads it into the form", () -> {
            try (App app = app()) {
                BooksPanel books = app.books();
                Book first = app.db().listBooks().get(0);
                onEdt(() -> books.table().selectId(first.id()));
                assertEquals(first.title(), books.form().value("title"), "title");
                assertEquals(first.author(), books.form().value("author"), "author");
                assertEquals(first.id(), (int) books.editingId(), "editing id");
            }
        });
    }

    // ----------------------------------------------------------------- members
    private static void members(TestHarness t) {
        t.test("selecting a member fills the form and the history", () -> {
            try (App app = app()) {
                MembersPanel members = app.members();
                assertEquals(8, members.table().rowCount(), "members listed");

                MemberView withLoans = app.db().listMembers().stream()
                        .filter(m -> m.onLoan() > 0).findFirst().orElseThrow();
                onEdt(() -> members.table().selectId(withLoans.id()));

                assertEquals(withLoans.member().name(), members.form().value("name"),
                        "name in the form");
                assertContains(members.historyTitle(), withLoans.member().name(),
                        "history heading");
                assertTrue(members.history().rowCount() > 0, "history rows");
            }
        });

        t.test("a new member gets the next code and the type's limit", () -> {
            try (App app = app()) {
                MembersPanel members = app.members();
                onEdt(() -> {
                    members.newRecord();
                    members.form().set("name", "Grace Hopper");
                    members.form().set("type", "Staff");
                    members.form().set("email", "grace@example.com");
                    members.form().set("phone", "9811122233");
                    members.save();
                });
                assertEquals(9, members.table().rowCount(), "member added");

                Member created = app.db().getMember(members.editingId());
                assertEquals("M0009", created.code(), "next code");
                assertEquals(5, created.borrowingLimit(), "staff limit");
            }
        });

        t.test("a bad email is reported and not saved", () -> {
            try (App app = app()) {
                MembersPanel members = app.members();
                onEdt(() -> {
                    members.newRecord();
                    members.form().set("name", "Broken Contact");
                    members.form().set("email", "nope");
                    members.save();
                });
                assertContains(app.dialogs().lastWarning(), "email", "warning text");
                assertEquals(8, members.table().rowCount(), "nothing added");
            }
        });

        t.test("a member holding books cannot be deleted from the panel", () -> {
            try (App app = app()) {
                MembersPanel members = app.members();
                MemberView holder = app.db().listMembers().stream()
                        .filter(m -> m.onLoan() > 0).findFirst().orElseThrow();
                onEdt(() -> {
                    members.table().selectId(holder.id());
                    members.delete();
                });
                assertContains(app.dialogs().lastWarning(), "on loan", "warning text");
                assertNotNull(app.db().getMember(holder.id()), "member still there");
            }
        });
    }

    // ------------------------------------------------------------ circulation
    private static void circulation(TestHarness t) {
        t.test("a book can be issued and returned at the desk", () -> {
            try (App app = app()) {
                CirculationPanel desk = app.desk();
                int before = app.db().stats().loansActive();

                onEdt(() -> {
                    assertTrue(desk.selectMember("Sofia"), "member found");
                    assertTrue(desk.selectBook("Charlotte"), "book found");
                });
                assertTrue(desk.canIssue(), "the issue button is enabled");

                int bookId = app.db().listBooks("Charlotte", "All", true).get(0).id();
                int shelfBefore = app.db().getBook(bookId).availableCopies();
                onEdt(desk::issue);

                assertEquals(before + 1, app.db().stats().loansActive(), "loan opened");
                assertEquals(shelfBefore - 1, app.db().getBook(bookId).availableCopies(),
                        "copy taken off the shelf");
                assertContains(app.frame().statusText(), "Issued", "status line");

                int loanId = app.db().listLoans(LoanFilter.ACTIVE, "").stream()
                        .mapToInt(LoanView::id).max().orElseThrow();
                onEdt(() -> {
                    desk.table().selectId(loanId);
                    desk.acceptReturn(false);
                });
                assertNotNull(app.db().getLoan(loanId).returnDate(), "returned");
                assertEquals(before, app.db().stats().loansActive(), "loan closed");
            }
        });

        t.test("issuing to a member with an overdue book is refused", () -> {
            try (App app = app()) {
                CirculationPanel desk = app.desk();
                LoanView overdue = app.db().listLoans(LoanFilter.OVERDUE, "").get(0);
                onEdt(() -> {
                    assertTrue(desk.selectMember(overdue.memberCode()), "member found");
                    assertTrue(desk.selectBook("Matilda"), "book found");
                });
                assertContains(desk.previewText(), "overdue", "the preview warns first");

                int before = app.db().stats().loansActive();
                onEdt(desk::issue);
                assertContains(app.dialogs().lastWarning(), "overdue", "warning text");
                assertEquals(before, app.db().stats().loansActive(), "nothing issued");
            }
        });

        t.test("an overdue return records a fine that can then be settled", () -> {
            try (App app = app()) {
                CirculationPanel desk = app.desk();
                onEdt(() -> {
                    desk.setStatusFilter(LoanFilter.OVERDUE);
                    desk.refreshTable();
                });
                assertTrue(desk.table().rowCount() > 0, "overdue loans listed");

                LoanView first = desk.table().rows().get(0);
                double expected = first.accruedFine();
                assertTrue(expected > 0, "a fine has accrued");

                onEdt(() -> desk.table().selectId(first.id()));
                assertTrue(desk.canReturn(), "return is offered");
                assertTrue(desk.canWaive(), "waiving is offered");
                onEdt(() -> desk.acceptReturn(false));

                Loan loan = app.db().getLoan(first.id());
                assertEquals(expected, loan.fine(), "fine recorded");
                assertFalse(loan.finePaid(), "fine starts unpaid");

                onEdt(() -> {
                    desk.setStatusFilter(LoanFilter.UNPAID_FINES);
                    desk.refreshTable();
                    desk.table().selectId(first.id());
                });
                assertTrue(desk.canPayFine(), "settling is offered");
                onEdt(desk::markFinePaid);
                assertTrue(app.db().getLoan(first.id()).finePaid(), "fine settled");
            }
        });

        t.test("waiving a fine leaves nothing owed", () -> {
            try (App app = app()) {
                CirculationPanel desk = app.desk();
                onEdt(() -> {
                    desk.setStatusFilter(LoanFilter.OVERDUE);
                    desk.refreshTable();
                });
                LoanView first = desk.table().rows().get(0);
                onEdt(() -> {
                    desk.table().selectId(first.id());
                    desk.acceptReturn(true);
                });
                assertEquals(0.0, app.db().getLoan(first.id()).fine(), "nothing charged");
                assertContains(app.frame().statusText(), "waived", "status line");
            }
        });

        t.test("renewing extends a loan that is not yet overdue", () -> {
            try (App app = app()) {
                CirculationPanel desk = app.desk();
                onEdt(() -> {
                    desk.setStatusFilter(LoanFilter.ON_LOAN);
                    desk.refreshTable();
                });
                LoanView first = desk.table().rows().get(0);
                onEdt(() -> desk.table().selectId(first.id()));
                assertTrue(desk.canRenew(), "renew is offered");

                onEdt(desk::renew);
                Loan after = app.db().getLoan(first.id());
                assertTrue(after.dueDate().isAfter(first.dueDate()), "due date moved out");
                assertEquals(1, after.renewals(), "renewal counted");
            }
        });

        t.test("the status filter and search narrow the loan list", () -> {
            try (App app = app()) {
                CirculationPanel desk = app.desk();
                onEdt(() -> {
                    desk.setStatusFilter(LoanFilter.ALL);
                    desk.refreshTable();
                });
                int everything = desk.table().rowCount();

                onEdt(() -> {
                    desk.setStatusFilter(LoanFilter.OVERDUE);
                    desk.refreshTable();
                });
                assertTrue(desk.table().rowCount() < everything, "overdue is a subset");

                onEdt(() -> {
                    desk.setStatusFilter(LoanFilter.ALL);
                    desk.search().setText("Dune");
                    desk.refreshTable();
                });
                assertTrue(desk.table().rowCount() > 0, "search found loans");
                assertTrue(desk.table().rowCount() < everything, "search narrowed");
            }
        });

        t.test("the buttons are disabled when no loan is selected", () -> {
            try (App app = app()) {
                CirculationPanel desk = app.desk();
                onEdt(() -> {
                    desk.refreshTable();
                    desk.table().clearSelection();
                });
                assertFalse(desk.canReturn(), "return disabled");
                assertFalse(desk.canRenew(), "renew disabled");
                assertFalse(desk.canWaive(), "waive disabled");
                assertFalse(desk.canPayFine(), "settle disabled");
            }
        });
    }

    // ------------------------------------------------------------------ settings
    private static void settings(TestHarness t) {
        t.test("saving the rules changes how loans behave", () -> {
            try (App app = app()) {
                SettingsPanel settings = app.settings();
                onEdt(() -> {
                    settings.form().set("libraryName", "Riverside Library");
                    settings.form().set("loanDays", "21");
                    settings.form().set("finePerDay", "3.50");
                    settings.save();
                });
                assertEquals(21, app.db().loanDays(), "loan period");
                assertEquals(3.5, app.db().finePerDay(), "fine rate");
                assertContains(app.frame().getTitle(), "Riverside Library", "window title");
                assertEquals("Riverside Library", app.dashboard().headingText(),
                        "dashboard heading");
            }
        });

        t.test("nonsense rule values are rejected", () -> {
            try (App app = app()) {
                SettingsPanel settings = app.settings();
                onEdt(() -> {
                    settings.form().set("loanDays", "zero");
                    settings.save();
                });
                assertContains(app.dialogs().lastWarning(), "whole number", "warning text");
                assertEquals(14, app.db().loanDays(), "setting unchanged");
            }
        });

        t.test("exporting writes a CSV file", () -> {
            try (App app = app()) {
                Path target = Files.createTempDirectory("lms-export").resolve("books.csv");
                app.dialogs().saveTarget = target;
                onEdt(() -> app.settings().export("books"));

                assertTrue(Files.exists(target), "file written");
                String text = Files.readString(target);
                assertContains(text, "Dune", "a known title");
                assertContains(app.frame().statusText(), "Exported", "status line");
            }
        });

        t.test("a cancelled file dialog writes nothing", () -> {
            try (App app = app()) {
                app.dialogs().saveTarget = null;
                String before = app.frame().statusText();
                onEdt(() -> app.settings().export("books"));
                assertEquals(before, app.frame().statusText(), "status unchanged");
            }
        });

        t.test("the sample loader tops up an existing database", () -> {
            try (App app = app()) {
                int before = app.db().stats().titles();
                app.dialogs().answer = true;
                onEdt(() -> app.frame().loadSampleData());
                assertEquals(before, app.db().stats().titles(),
                        "the same sample titles are not duplicated");
                assertContains(app.frame().statusText(), "sample data", "status line");
            }
        });
    }
}
