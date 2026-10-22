package com.library.db;

import com.library.model.Book;
import com.library.model.DailyCount;
import com.library.model.Loan;
import com.library.model.LoanStatus;
import com.library.model.LoanView;
import com.library.model.Member;
import com.library.model.MemberView;
import com.library.model.MembershipType;
import com.library.model.PopularBook;
import com.library.model.Stats;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every read and write of the library, and every rule that has to stay true:
 * copy counts, borrowing limits and fines. The interface calls these methods and
 * never reasons about the rules itself.
 *
 * <p>Dates are handled in local time throughout. SQLite's {@code date('now')} is
 * UTC, which is a day out for part of every day, so the queries below always say
 * {@code date('now','localtime')}.
 */
public class LibraryDatabase implements AutoCloseable {

    /** The database file used when none is given on the command line. */
    public static final Path DEFAULT_PATH = Path.of("library.db");

    /** Settings written on first run; editable on the Settings tab. */
    private static final Map<String, String> DEFAULT_SETTINGS = new LinkedHashMap<>();

    static {
        DEFAULT_SETTINGS.put("loan_days", "14");
        DEFAULT_SETTINGS.put("fine_per_day", "2.00");
        DEFAULT_SETTINGS.put("library_name", "City Central Library");
    }

    private static final String SCHEMA = """
            CREATE TABLE IF NOT EXISTS books (
                id               INTEGER PRIMARY KEY AUTOINCREMENT,
                title            TEXT    NOT NULL,
                author           TEXT    NOT NULL,
                isbn             TEXT    UNIQUE,
                category         TEXT    NOT NULL DEFAULT 'General',
                publisher        TEXT,
                year             INTEGER,
                shelf            TEXT,
                total_copies     INTEGER NOT NULL DEFAULT 1 CHECK (total_copies >= 0),
                available_copies INTEGER NOT NULL DEFAULT 1 CHECK (available_copies >= 0),
                added_on         TEXT    NOT NULL DEFAULT (date('now','localtime')),
                CHECK (available_copies <= total_copies)
            );

            CREATE TABLE IF NOT EXISTS members (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                member_code     TEXT    NOT NULL UNIQUE,
                name            TEXT    NOT NULL,
                email           TEXT,
                phone           TEXT,
                address         TEXT,
                membership_type TEXT    NOT NULL DEFAULT 'Public',
                join_date       TEXT    NOT NULL DEFAULT (date('now','localtime')),
                active          INTEGER NOT NULL DEFAULT 1,
                max_books       INTEGER
            );

            CREATE TABLE IF NOT EXISTS loans (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                book_id     INTEGER NOT NULL REFERENCES books(id)   ON DELETE CASCADE,
                member_id   INTEGER NOT NULL REFERENCES members(id) ON DELETE CASCADE,
                issue_date  TEXT    NOT NULL,
                due_date    TEXT    NOT NULL,
                return_date TEXT,
                renewals    INTEGER NOT NULL DEFAULT 0,
                fine        REAL    NOT NULL DEFAULT 0,
                fine_paid   INTEGER NOT NULL DEFAULT 0,
                notes       TEXT
            );

            CREATE TABLE IF NOT EXISTS settings (
                key   TEXT PRIMARY KEY,
                value TEXT NOT NULL
            );

            CREATE INDEX IF NOT EXISTS idx_loans_member ON loans(member_id);
            CREATE INDEX IF NOT EXISTS idx_loans_book   ON loans(book_id);
            CREATE INDEX IF NOT EXISTS idx_loans_due    ON loans(due_date);
            CREATE INDEX IF NOT EXISTS idx_books_title  ON books(title);
            CREATE INDEX IF NOT EXISTS idx_members_name ON members(name);
            """;

    private static final String LOAN_SELECT = """
            SELECT l.*, b.title, b.author, m.name AS member_name, m.member_code,
                   m.email, m.phone
            FROM loans l
            JOIN books b   ON b.id = l.book_id
            JOIN members m ON m.id = l.member_id
            """;

    /** The file backing this database, or {@code null} for an in-memory one. */
    private final Path file;
    /** What to show the user: the file path, or ":memory:". */
    private final String location;
    private final Connection connection;

    public LibraryDatabase(Path file) {
        this(file, file.toString(), "jdbc:sqlite:" + file);
    }

    private LibraryDatabase(Path file, String location, String jdbcUrl) {
        this.file = file;
        this.location = location;
        try {
            if (file != null) {
                Path parent = file.toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
            }
            this.connection = DriverManager.getConnection(jdbcUrl);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                for (String ddl : SCHEMA.split(";\\s*\\n")) {
                    if (!ddl.isBlank()) {
                        statement.execute(ddl);
                    }
                }
            }
            seedSettings();
        } catch (SQLException | IOException exc) {
            throw new LibraryException("Could not open the database at " + location + ": "
                    + exc.getMessage(), exc);
        }
    }

    /** An in-memory database, used by the tests. */
    public static LibraryDatabase inMemory() {
        return new LibraryDatabase(null, ":memory:", "jdbc:sqlite::memory:");
    }

    /** The backing file, or {@code null} when this database lives in memory. */
    public Path file() {
        return file;
    }

    /** A description of where the data lives, for display. */
    public String location() {
        return location;
    }

    /**
     * Direct JDBC access, used only by the sample-data seeder so it can write
     * loans with historic dates. Application code goes through the methods below.
     */
    public Connection connectionForSeeding() {
        return connection;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException exc) {
            // Nothing useful to do while shutting down.
        }
    }

    /** Today in local time; every rule below is measured against this. */
    public LocalDate today() {
        return LocalDate.now();
    }

    // ------------------------------------------------------------------ plumbing
    private LibraryException wrap(SQLException exc) {
        String message = exc.getMessage() == null ? "" : exc.getMessage();
        if (message.contains("books.isbn")) {
            return new LibraryException("Another book already uses that ISBN.", exc);
        }
        if (message.contains("members.member_code")) {
            return new LibraryException("Another member already uses that member ID.", exc);
        }
        if (message.contains("available_copies")) {
            return new LibraryException(
                    "Copy count would become invalid (available cannot exceed total).", exc);
        }
        return new LibraryException("The database rejected the change: " + message, exc);
    }

    /** Runs {@code work} as one transaction, rolling back if anything fails. */
    private <T> T inTransaction(SqlWork<T> work) {
        try {
            connection.setAutoCommit(false);
            try {
                T result = work.run();
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exc) {
                connection.rollback();
                throw exc;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exc) {
            throw wrap(exc);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run() throws SQLException;
    }

    private static LocalDate readDate(ResultSet rs, String column) throws SQLException {
        String text = rs.getString(column);
        return text == null || text.isBlank() ? null : LocalDate.parse(text.substring(0, 10));
    }

    private static Integer readInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    // ------------------------------------------------------------------ settings
    private void seedSettings() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO settings(key, value) VALUES (?, ?)")) {
            for (Map.Entry<String, String> entry : DEFAULT_SETTINGS.entrySet()) {
                ps.setString(1, entry.getKey());
                ps.setString(2, entry.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public String getSetting(String key) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT value FROM settings WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (SQLException exc) {
            throw wrap(exc);
        }
        return DEFAULT_SETTINGS.getOrDefault(key, "");
    }

    public void setSetting(String key, String value) {
        inTransaction(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO settings(key, value) VALUES (?, ?) "
                            + "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
                ps.setString(1, key);
                ps.setString(2, value);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public int loanDays() {
        try {
            return Math.max(1, (int) Double.parseDouble(getSetting("loan_days")));
        } catch (NumberFormatException exc) {
            return 14;
        }
    }

    public double finePerDay() {
        try {
            return Math.max(0, Double.parseDouble(getSetting("fine_per_day")));
        } catch (NumberFormatException exc) {
            return 2.0;
        }
    }

    public String libraryName() {
        String name = getSetting("library_name");
        return name.isBlank() ? "Library" : name;
    }

    // --------------------------------------------------------------------- books
    /** Validated book details, ready to be written. */
    private record CleanBook(String title, String author, String isbn, String category,
                             String publisher, Integer year, String shelf, int totalCopies) {
    }

    private CleanBook validate(BookDraft draft) {
        String title = trimOrEmpty(draft.title());
        String author = trimOrEmpty(draft.author());
        if (title.isEmpty()) {
            throw new LibraryException("Title is required.");
        }
        if (author.isEmpty()) {
            throw new LibraryException("Author is required.");
        }

        String copiesText = trimOrEmpty(draft.totalCopies());
        int copies;
        try {
            copies = copiesText.isEmpty() ? 1 : Integer.parseInt(copiesText);
        } catch (NumberFormatException exc) {
            throw new LibraryException("Total copies must be a whole number.");
        }
        if (copies < 1) {
            throw new LibraryException("A book needs at least one copy.");
        }

        Integer year = null;
        String yearText = trimOrEmpty(draft.year());
        if (!yearText.isEmpty()) {
            try {
                year = Integer.valueOf(yearText);
            } catch (NumberFormatException exc) {
                throw new LibraryException("Year must be a number, e.g. 2019.");
            }
            int limit = today().getYear() + 1;
            if (year < 1000 || year > limit) {
                throw new LibraryException("Year must be between 1000 and " + limit + ".");
            }
        }

        String isbn = trimOrEmpty(draft.isbn()).replace("-", "").replace(" ", "");
        if (!isbn.isEmpty()) {
            boolean digits = isbn.chars().allMatch(Character::isDigit);
            if (!digits || (isbn.length() != 10 && isbn.length() != 13)) {
                throw new LibraryException("ISBN must be 10 or 13 digits (dashes are fine).");
            }
        }

        String category = trimOrEmpty(draft.category());
        return new CleanBook(title, author, isbn.isEmpty() ? null : isbn,
                category.isEmpty() ? "General" : category,
                nullIfBlank(draft.publisher()), year, nullIfBlank(draft.shelf()), copies);
    }

    public int addBook(BookDraft draft) {
        CleanBook book = validate(draft);
        return inTransaction(() -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO books(title, author, isbn, category, publisher, year,
                                      shelf, total_copies, available_copies, added_on)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, book.title());
                ps.setString(2, book.author());
                ps.setString(3, book.isbn());
                ps.setString(4, book.category());
                ps.setString(5, book.publisher());
                setInteger(ps, 6, book.year());
                ps.setString(7, book.shelf());
                ps.setInt(8, book.totalCopies());
                ps.setInt(9, book.totalCopies());
                ps.setString(10, today().toString());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return keys.getInt(1);
                }
            }
        });
    }

    public void updateBook(int bookId, BookDraft draft) {
        Book current = getBook(bookId);
        if (current == null) {
            throw new LibraryException("That book no longer exists.");
        }
        CleanBook book = validate(draft);
        int onLoan = current.copiesOnLoan();
        if (book.totalCopies() < onLoan) {
            throw new LibraryException(onLoan + " copies are currently issued, so the total "
                    + "cannot drop below " + onLoan + ".");
        }
        int available = book.totalCopies() - onLoan;

        inTransaction(() -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE books SET title = ?, author = ?, isbn = ?, category = ?,
                           publisher = ?, year = ?, shelf = ?, total_copies = ?,
                           available_copies = ?
                    WHERE id = ?
                    """)) {
                ps.setString(1, book.title());
                ps.setString(2, book.author());
                ps.setString(3, book.isbn());
                ps.setString(4, book.category());
                ps.setString(5, book.publisher());
                setInteger(ps, 6, book.year());
                ps.setString(7, book.shelf());
                ps.setInt(8, book.totalCopies());
                ps.setInt(9, available);
                ps.setInt(10, bookId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void deleteBook(int bookId) {
        if (countOpenLoans("book_id", bookId) > 0) {
            throw new LibraryException(
                    "This book has copies on loan. Accept the returns before deleting it.");
        }
        inTransaction(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM books WHERE id = ?")) {
                ps.setInt(1, bookId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public Book getBook(int bookId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM books WHERE id = ?")) {
            ps.setInt(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readBook(rs) : null;
            }
        } catch (SQLException exc) {
            throw wrap(exc);
        }
    }

    public List<Book> listBooks() {
        return listBooks("", "All", false);
    }

    public List<Book> listBooks(String search, String category, boolean onlyAvailable) {
        StringBuilder sql = new StringBuilder("SELECT * FROM books WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            sql.append(" AND (title LIKE ? OR author LIKE ? OR IFNULL(isbn, '') LIKE ?")
               .append(" OR IFNULL(publisher, '') LIKE ?)");
            String like = "%" + search.trim() + "%";
            for (int i = 0; i < 4; i++) {
                params.add(like);
            }
        }
        if (category != null && !category.isBlank() && !"All".equals(category)) {
            sql.append(" AND category = ?");
            params.add(category);
        }
        if (onlyAvailable) {
            sql.append(" AND available_copies > 0");
        }
        sql.append(" ORDER BY title COLLATE NOCASE");

        List<Book> books = new ArrayList<>();
        try (PreparedStatement ps = prepare(sql.toString(), params);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                books.add(readBook(rs));
            }
        } catch (SQLException exc) {
            throw wrap(exc);
        }
        return books;
    }

    public List<String> categories() {
        List<String> out = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT DISTINCT category FROM books ORDER BY category COLLATE NOCASE")) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        } catch (SQLException exc) {
            throw wrap(exc);
        }
        return out;
    }

    private static Book readBook(ResultSet rs) throws SQLException {
        return new Book(
                rs.getInt("id"),
                rs.getString("title"),
                rs.getString("author"),
                rs.getString("isbn"),
                rs.getString("category"),
                rs.getString("publisher"),
                readInteger(rs, "year"),
                rs.getString("shelf"),
                rs.getInt("total_copies"),
                rs.getInt("available_copies"),
                readDate(rs, "added_on"));
    }

    // ------------------------------------------------------------------- members
    private record CleanMember(String code, String name, String email, String phone,
                               String address, MembershipType type, LocalDate joinDate,
                               boolean active, Integer maxBooks) {
    }

    private CleanMember validate(MemberDraft draft) {
        String name = trimOrEmpty(draft.name());
        if (name.isEmpty()) {
            throw new LibraryException("Member name is required.");
        }

        String email = trimOrEmpty(draft.email());
        if (!email.isEmpty()) {
            int at = email.indexOf('@');
            boolean looksValid = at > 0 && email.indexOf('.', at) > at + 1
                    && !email.endsWith(".");
            if (!looksValid) {
                throw new LibraryException("That email address does not look valid.");
            }
        }

        String phone = trimOrEmpty(draft.phone());
        if (!phone.isEmpty()) {
            for (char ch : phone.toCharArray()) {
                if (!Character.isDigit(ch) && "+- ()".indexOf(ch) < 0) {
                    throw new LibraryException(
                            "Phone may only contain digits and + - ( ) characters.");
                }
            }
        }

        Integer maxBooks = null;
        String limitText = trimOrEmpty(draft.maxBooks());
        if (!limitText.isEmpty()) {
            try {
                maxBooks = Integer.valueOf(limitText);
            } catch (NumberFormatException exc) {
                throw new LibraryException("Book limit must be a whole number.");
            }
            if (maxBooks < 0) {
                throw new LibraryException("Book limit cannot be negative.");
            }
        }

        return new CleanMember(trimOrEmpty(draft.code()), name,
                email.isEmpty() ? null : email, phone.isEmpty() ? null : phone,
                nullIfBlank(draft.address()), MembershipType.fromLabel(draft.type()),
                parseDate(draft.joinDate()), draft.active(), maxBooks);
    }

    /** The next free member code, in the form {@code M0007}. */
    public String nextMemberCode() {
        int highest = 0;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT member_code FROM members")) {
            while (rs.next()) {
                String digits = rs.getString(1).replaceAll("\\D", "");
                if (!digits.isEmpty()) {
                    try {
                        highest = Math.max(highest, Integer.parseInt(digits));
                    } catch (NumberFormatException ignored) {
                        // A code with more digits than an int can hold: skip it.
                    }
                }
            }
        } catch (SQLException exc) {
            throw wrap(exc);
        }
        return String.format("M%04d", highest + 1);
    }

    public int addMember(MemberDraft draft) {
        CleanMember member = validate(draft);
        String code = member.code().isEmpty() ? nextMemberCode() : member.code();
        return inTransaction(() -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO members(member_code, name, email, phone, address,
                                        membership_type, join_date, active, max_books)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, code);
                ps.setString(2, member.name());
                ps.setString(3, member.email());
                ps.setString(4, member.phone());
                ps.setString(5, member.address());
                ps.setString(6, member.type().label());
                ps.setString(7, member.joinDate().toString());
                ps.setInt(8, member.active() ? 1 : 0);
                setInteger(ps, 9, member.maxBooks());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return keys.getInt(1);
                }
            }
        });
    }

    public void updateMember(int memberId, MemberDraft draft) {
        if (getMember(memberId) == null) {
            throw new LibraryException("That member no longer exists.");
        }
        CleanMember member = validate(draft);
        if (member.code().isEmpty()) {
            throw new LibraryException("Member ID is required.");
        }
        inTransaction(() -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE members SET member_code = ?, name = ?, email = ?, phone = ?,
                           address = ?, membership_type = ?, join_date = ?, active = ?,
                           max_books = ?
                    WHERE id = ?
                    """)) {
                ps.setString(1, member.code());
                ps.setString(2, member.name());
                ps.setString(3, member.email());
                ps.setString(4, member.phone());
                ps.setString(5, member.address());
                ps.setString(6, member.type().label());
                ps.setString(7, member.joinDate().toString());
                ps.setInt(8, member.active() ? 1 : 0);
                setInteger(ps, 9, member.maxBooks());
                ps.setInt(10, memberId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void deleteMember(int memberId) {
        int open = countOpenLoans("member_id", memberId);
        if (open > 0) {
            throw new LibraryException("This member still has " + open + " book(s) on loan. "
                    + "Accept the returns before deleting the record.");
        }
        inTransaction(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM members WHERE id = ?")) {
                ps.setInt(1, memberId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public Member getMember(int memberId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM members WHERE id = ?")) {
            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readMember(rs) : null;
            }
        } catch (SQLException exc) {
            throw wrap(exc);
        }
    }

    public List<MemberView> listMembers() {
        return listMembers("", false);
    }

    /**
     * Members with their open-loan count and what they owe. "Dues" counts fines
     * recorded on returned loans that are still unpaid plus what is accruing right
     * now on anything overdue, so the column agrees with the dashboard total.
     */
    public List<MemberView> listMembers(String search, boolean onlyActive) {
        StringBuilder sql = new StringBuilder("""
                SELECT m.*,
                       (SELECT COUNT(*) FROM loans l
                         WHERE l.member_id = m.id AND l.return_date IS NULL) AS on_loan,
                       (SELECT IFNULL(SUM(
                            CASE WHEN l.return_date IS NOT NULL
                                 THEN (CASE WHEN l.fine_paid = 0 THEN l.fine ELSE 0 END)
                                 ELSE MAX(0, CAST(julianday('now','localtime')
                                                - julianday(l.due_date) AS INTEGER)) * ?
                            END), 0)
                          FROM loans l WHERE l.member_id = m.id) AS dues
                FROM members m WHERE 1 = 1
                """);
        List<Object> params = new ArrayList<>();
        params.add(finePerDay());
        if (search != null && !search.isBlank()) {
            sql.append(" AND (m.name LIKE ? OR m.member_code LIKE ?")
               .append(" OR IFNULL(m.email, '') LIKE ? OR IFNULL(m.phone, '') LIKE ?)");
            String like = "%" + search.trim() + "%";
            for (int i = 0; i < 4; i++) {
                params.add(like);
            }
        }
        if (onlyActive) {
            sql.append(" AND m.active = 1");
        }
        sql.append(" ORDER BY m.name COLLATE NOCASE");

        List<MemberView> out = new ArrayList<>();
        try (PreparedStatement ps = prepare(sql.toString(), params);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new MemberView(readMember(rs), rs.getInt("on_loan"),
                        round(rs.getDouble("dues"))));
            }
        } catch (SQLException exc) {
            throw wrap(exc);
        }
        return out;
    }

    private static Member readMember(ResultSet rs) throws SQLException {
        return new Member(
                rs.getInt("id"),
                rs.getString("member_code"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("address"),
                MembershipType.fromLabel(rs.getString("membership_type")),
                readDate(rs, "join_date"),
                rs.getInt("active") == 1,
                readInteger(rs, "max_books"));
    }

    // --------------------------------------------------------------- circulation
    /**
     * Issues one copy and returns the new loan id. Refuses when nothing is on the
     * shelf, the member is inactive, at their limit, already holds this title, or
     * has something overdue.
     */
    public int issueBook(int bookId, int memberId, Integer days) {
        Book book = getBook(bookId);
        Member member = getMember(memberId);
        if (book == null) {
            throw new LibraryException("Select a book to issue.");
        }
        if (member == null) {
            throw new LibraryException("Select a member to issue to.");
        }
        if (!member.active()) {
            throw new LibraryException(member.name() + "'s membership is inactive.");
        }
        if (book.availableCopies() < 1) {
            throw new LibraryException("No copies of '" + book.title() + "' are on the shelf.");
        }

        List<LoanView> open = loansForMember(memberId, true);
        int limit = member.borrowingLimit();
        if (open.size() >= limit) {
            throw new LibraryException(member.name() + " already holds " + open.size()
                    + " of " + limit + " permitted books.");
        }
        for (LoanView view : open) {
            if (view.loan().bookId() == bookId) {
                throw new LibraryException(
                        member.name() + " already has a copy of this title.");
            }
        }
        long overdue = open.stream().filter(v -> v.status() == LoanStatus.OVERDUE).count();
        if (overdue > 0) {
            throw new LibraryException(member.name() + " has " + overdue
                    + " overdue book(s). Settle those before issuing another.");
        }

        LocalDate issue = today();
        LocalDate due = issue.plusDays(days == null || days < 1 ? loanDays() : days);
        return inTransaction(() -> {
            int loanId;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO loans(book_id, member_id, issue_date, due_date) "
                            + "VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, bookId);
                ps.setInt(2, memberId);
                ps.setString(3, issue.toString());
                ps.setString(4, due.toString());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    loanId = keys.getInt(1);
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE books SET available_copies = available_copies - 1 WHERE id = ?")) {
                ps.setInt(1, bookId);
                ps.executeUpdate();
            }
            return loanId;
        });
    }

    /** Accepts a return and returns the fine charged (0 when on time or waived). */
    public double returnBook(int loanId, boolean waiveFine) {
        Loan loan = getLoan(loanId);
        if (loan == null) {
            throw new LibraryException("That loan record no longer exists.");
        }
        if (!loan.isOpen()) {
            throw new LibraryException("This copy has already been returned.");
        }
        double fine = waiveFine ? 0 : fineFor(loan);

        return inTransaction(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE loans SET return_date = ?, fine = ?, fine_paid = ? WHERE id = ?")) {
                ps.setString(1, today().toString());
                ps.setDouble(2, fine);
                ps.setInt(3, fine == 0 ? 1 : 0);
                ps.setInt(4, loanId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE books SET available_copies = "
                            + "MIN(total_copies, available_copies + 1) WHERE id = ?")) {
                ps.setInt(1, loan.bookId());
                ps.executeUpdate();
            }
            return fine;
        });
    }

    public LocalDate renewLoan(int loanId, Integer days) {
        return renewLoan(loanId, days, 2);
    }

    public LocalDate renewLoan(int loanId, Integer days, int maxRenewals) {
        Loan loan = getLoan(loanId);
        if (loan == null) {
            throw new LibraryException("That loan record no longer exists.");
        }
        if (!loan.isOpen()) {
            throw new LibraryException("A returned loan cannot be renewed.");
        }
        if (loan.renewals() >= maxRenewals) {
            throw new LibraryException(
                    "This loan has already been renewed " + maxRenewals + " times.");
        }
        if (loan.dueDate().isBefore(today())) {
            throw new LibraryException("Overdue loans must be returned, not renewed.");
        }

        LocalDate newDue = loan.dueDate()
                .plusDays(days == null || days < 1 ? loanDays() : days);
        inTransaction(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE loans SET due_date = ?, renewals = renewals + 1 WHERE id = ?")) {
                ps.setString(1, newDue.toString());
                ps.setInt(2, loanId);
                ps.executeUpdate();
            }
            return null;
        });
        return newDue;
    }

    public void payFine(int loanId) {
        Loan loan = getLoan(loanId);
        if (loan == null) {
            throw new LibraryException("That loan record no longer exists.");
        }
        if (loan.finePaid()) {
            throw new LibraryException("That fine is already settled.");
        }
        inTransaction(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE loans SET fine_paid = 1 WHERE id = ?")) {
                ps.setInt(1, loanId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** What this loan owes: the stored fine once returned, or what has accrued so far. */
    public double fineFor(Loan loan) {
        if (!loan.isOpen()) {
            return loan.fine();
        }
        long late = ChronoUnit.DAYS.between(loan.dueDate(), today());
        return late <= 0 ? 0 : round(late * finePerDay());
    }

    public Loan getLoan(int loanId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM loans WHERE id = ?")) {
            ps.setInt(1, loanId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readLoan(rs) : null;
            }
        } catch (SQLException exc) {
            throw wrap(exc);
        }
    }

    public LoanView getLoanDetail(int loanId) {
        try (PreparedStatement ps = connection.prepareStatement(
                LOAN_SELECT + " WHERE l.id = ?")) {
            ps.setInt(1, loanId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readLoanView(rs) : null;
            }
        } catch (SQLException exc) {
            throw wrap(exc);
        }
    }

    public List<LoanView> listLoans(LoanFilter filter, String search) {
        StringBuilder sql = new StringBuilder(LOAN_SELECT).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        switch (filter) {
            case ON_LOAN -> sql.append(
                    " AND l.return_date IS NULL AND date(l.due_date) >= date('now','localtime')");
            case OVERDUE -> sql.append(
                    " AND l.return_date IS NULL AND date(l.due_date) < date('now','localtime')");
            case ACTIVE -> sql.append(" AND l.return_date IS NULL");
            case RETURNED -> sql.append(" AND l.return_date IS NOT NULL");
            case UNPAID_FINES -> sql.append(" AND l.fine > 0 AND l.fine_paid = 0");
            case ALL -> { /* no extra condition */ }
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (b.title LIKE ? OR m.name LIKE ? OR m.member_code LIKE ?)");
            String like = "%" + search.trim() + "%";
            for (int i = 0; i < 3; i++) {
                params.add(like);
            }
        }
        sql.append(" ORDER BY l.return_date IS NOT NULL, date(l.due_date), l.id DESC");
        return queryLoans(sql.toString(), params);
    }

    public List<LoanView> loansForMember(int memberId, boolean openOnly) {
        StringBuilder sql = new StringBuilder(LOAN_SELECT).append(" WHERE l.member_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(memberId);
        if (openOnly) {
            sql.append(" AND l.return_date IS NULL");
        }
        sql.append(" ORDER BY date(l.due_date)");
        return queryLoans(sql.toString(), params);
    }

    private List<LoanView> queryLoans(String sql, List<Object> params) {
        List<LoanView> out = new ArrayList<>();
        try (PreparedStatement ps = prepare(sql, params);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(readLoanView(rs));
            }
        } catch (SQLException exc) {
            throw wrap(exc);
        }
        return out;
    }

    private static Loan readLoan(ResultSet rs) throws SQLException {
        return new Loan(
                rs.getInt("id"),
                rs.getInt("book_id"),
                rs.getInt("member_id"),
                readDate(rs, "issue_date"),
                readDate(rs, "due_date"),
                readDate(rs, "return_date"),
                rs.getInt("renewals"),
                rs.getDouble("fine"),
                rs.getInt("fine_paid") == 1);
    }

    private LoanView readLoanView(ResultSet rs) throws SQLException {
        Loan loan = readLoan(rs);
        String phone = rs.getString("phone");
        String email = rs.getString("email");
        String contact = phone != null && !phone.isBlank() ? phone
                : (email != null && !email.isBlank() ? email : "-");
        return new LoanView(loan, rs.getString("title"), rs.getString("author"),
                rs.getString("member_name"), rs.getString("member_code"), contact,
                loan.status(today()), loan.daysLate(today()), fineFor(loan));
    }

    private int countOpenLoans(String column, int id) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM loans WHERE " + column + " = ? AND return_date IS NULL")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException exc) {
            throw wrap(exc);
        }
    }

    // ----------------------------------------------------------------- dashboard
    public Stats stats() {
        try (Statement st = connection.createStatement()) {
            int titles;
            int copies;
            int available;
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) AS titles, IFNULL(SUM(total_copies), 0) AS copies, "
                            + "IFNULL(SUM(available_copies), 0) AS available FROM books")) {
                rs.next();
                titles = rs.getInt("titles");
                copies = rs.getInt("copies");
                available = rs.getInt("available");
            }

            int membersTotal;
            int membersActive;
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) AS total, IFNULL(SUM(active), 0) AS active FROM members")) {
                rs.next();
                membersTotal = rs.getInt("total");
                membersActive = rs.getInt("active");
            }

            int active;
            int overdue;
            int issuedToday;
            double recorded;
            try (ResultSet rs = st.executeQuery("""
                    SELECT IFNULL(SUM(return_date IS NULL), 0) AS active,
                           IFNULL(SUM(return_date IS NULL
                                  AND date(due_date) < date('now','localtime')), 0) AS overdue,
                           IFNULL(SUM(CASE WHEN fine_paid = 0 THEN fine ELSE 0 END), 0) AS dues,
                           IFNULL(SUM(date(issue_date) = date('now','localtime')), 0)
                               AS issued_today
                    FROM loans
                    """)) {
                rs.next();
                active = rs.getInt("active");
                overdue = rs.getInt("overdue");
                recorded = rs.getDouble("dues");
                issuedToday = rs.getInt("issued_today");
            }

            // Fines still running on open overdue loans are not stored on the row yet.
            double accruing = 0;
            for (LoanView view : listLoans(LoanFilter.OVERDUE, "")) {
                accruing += view.accruedFine();
            }

            return new Stats(titles, copies, available, copies - available, membersTotal,
                    membersActive, active, overdue, issuedToday, round(recorded + accruing));
        } catch (SQLException exc) {
            throw wrap(exc);
        }
    }

    public List<PopularBook> popularBooks(int limit) {
        List<PopularBook> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT b.title, b.author, COUNT(l.id) AS times_issued
                FROM books b JOIN loans l ON l.book_id = b.id
                GROUP BY b.id
                ORDER BY times_issued DESC, b.title COLLATE NOCASE
                LIMIT ?
                """)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new PopularBook(rs.getString("title"), rs.getString("author"),
                            rs.getInt("times_issued")));
                }
            }
        } catch (SQLException exc) {
            throw wrap(exc);
        }
        return out;
    }

    /** Issues per day over the last {@code days} days, oldest first. */
    public List<DailyCount> activity(int days) {
        List<DailyCount> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT date(issue_date) AS day, COUNT(*) AS issued
                FROM loans WHERE date(issue_date) >= ?
                GROUP BY day ORDER BY day
                """)) {
            ps.setString(1, today().minusDays(days).toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new DailyCount(LocalDate.parse(rs.getString("day")),
                            rs.getInt("issued")));
                }
            }
        } catch (SQLException exc) {
            throw wrap(exc);
        }
        return out;
    }

    // -------------------------------------------------------------------- export
    /** Writes {@code books}, {@code members} or {@code loans} to a CSV file. */
    public int exportCsv(String table, Path target) {
        String sql = switch (table) {
            case "books" -> "SELECT * FROM books ORDER BY title COLLATE NOCASE";
            case "members" -> "SELECT * FROM members ORDER BY name COLLATE NOCASE";
            case "loans" -> LOAN_SELECT + " ORDER BY l.id";
            default -> throw new LibraryException("Nothing to export for '" + table + "'.");
        };

        int rows = 0;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql);
             Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            ResultSetMetaData meta = rs.getMetaData();
            int columns = meta.getColumnCount();
            StringBuilder header = new StringBuilder();
            for (int i = 1; i <= columns; i++) {
                header.append(i > 1 ? "," : "").append(csv(meta.getColumnLabel(i)));
            }
            writer.write(header.append('\n').toString());

            while (rs.next()) {
                StringBuilder line = new StringBuilder();
                for (int i = 1; i <= columns; i++) {
                    line.append(i > 1 ? "," : "").append(csv(rs.getString(i)));
                }
                writer.write(line.append('\n').toString());
                rows++;
            }
        } catch (SQLException | IOException exc) {
            throw new LibraryException("Could not write " + target + ": " + exc.getMessage(), exc);
        }
        return rows;
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    /** Makes a consistent copy of the database, safe to run while the app is open. */
    public Path backup(Path target) {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("backup to " + quote(target.toAbsolutePath().toString()));
        } catch (SQLException exc) {
            throw new LibraryException("Backup failed: " + exc.getMessage(), exc);
        }
        return target;
    }

    private static String quote(String text) {
        return "'" + text.replace("'", "''") + "'";
    }

    // ------------------------------------------------------------------- helpers
    private PreparedStatement prepare(String sql, List<Object> params) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(sql);
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
        return ps;
    }

    private static void setInteger(PreparedStatement ps, int index, Integer value)
            throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static String trimOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String nullIfBlank(String value) {
        String trimmed = trimOrEmpty(value);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** Accepts the handful of date spellings a form field might contain. */
    public LocalDate parseDate(String text) {
        String trimmed = trimOrEmpty(text);
        if (trimmed.isEmpty()) {
            return today();
        }
        for (String pattern : new String[]{"yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yyyy", "d MMM yyyy"}) {
            try {
                return LocalDate.parse(trimmed, DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException ignored) {
                // Try the next spelling.
            }
        }
        throw new LibraryException("'" + text + "' is not a date I recognise. Use YYYY-MM-DD.");
    }
}
