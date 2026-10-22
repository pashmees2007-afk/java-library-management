package com.library.ui;

import com.library.db.LibraryException;
import com.library.model.MembershipType;
import com.library.model.Stats;
import com.library.ui.components.CardPanel;
import com.library.ui.components.FlatButton;
import com.library.ui.components.FormGrid;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/** Settings tab: circulation rules, CSV export and database backup. */
public class SettingsPanel extends TabPanel {

    private static final long serialVersionUID = 1L;

    private final FormGrid form = new FormGrid();
    private final JLabel dataNote = new JLabel();

    public SettingsPanel(AppContext app) {
        super(app);

        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setOpaque(false);
        top.add(Theme.title("Settings & maintenance"), BorderLayout.NORTH);

        JPanel columns = new JPanel(new GridLayout(1, 2, 12, 0));
        columns.setOpaque(false);
        columns.add(buildRules());
        columns.add(buildData());
        top.add(columns, BorderLayout.CENTER);

        JPanel outer = new JPanel(new BorderLayout(0, 12));
        outer.setOpaque(false);
        outer.add(top, BorderLayout.NORTH);
        outer.add(buildRulesSummary(), BorderLayout.CENTER);

        add(outer, BorderLayout.NORTH);
        refresh();
    }

    // ------------------------------------------------------------------ layout
    private JPanel buildRules() {
        CardPanel card = new CardPanel(new BorderLayout(0, 10), 14, 14);

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel heading = Theme.section("Circulation rules");
        JLabel hint = Theme.muted("Applied to new loans and to fine calculations.");
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.add(heading);
        head.add(hint);

        form.text("libraryName", "Library name", false, 20);
        form.text("loanDays", "Loan period (days)", false, 5);
        form.text("finePerDay", "Fine per day", false, 5);
        form.buttons(
                FlatButton.accent("Save rules", event -> save()),
                FlatButton.soft("Reload", event -> refresh()));

        StringJoiner limits = new StringJoiner(", ");
        for (MembershipType type : MembershipType.values()) {
            limits.add(type.label() + " " + type.defaultLimit());
        }
        JLabel note = new JLabel(Theme.wrap("Per-type limits: " + limits + ".\n"
                + "A member's own limit, when set, overrides these.", 360));
        note.setFont(Theme.SMALL);
        note.setForeground(Theme.MUTED);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(form, BorderLayout.NORTH);
        body.add(note, BorderLayout.CENTER);

        card.add(head, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildData() {
        CardPanel card = new CardPanel(new BorderLayout(0, 10), 14, 14);
        card.add(Theme.section("Data"), BorderLayout.NORTH);

        dataNote.setFont(Theme.SMALL);
        dataNote.setForeground(Theme.MUTED);

        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));

        for (String[] entry : new String[][]{
                {"Export catalogue to CSV", "books"},
                {"Export members to CSV", "members"},
                {"Export loan history to CSV", "loans"}}) {
            FlatButton button = FlatButton.soft(entry[0], event -> export(entry[1]));
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.setMaximumSize(new Dimension(260, 34));
            buttons.add(button);
            buttons.add(javax.swing.Box.createVerticalStrut(6));
        }

        JSeparator separator = new JSeparator();
        separator.setAlignmentX(Component.LEFT_ALIGNMENT);
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        separator.setForeground(Theme.BORDER);
        buttons.add(javax.swing.Box.createVerticalStrut(6));
        buttons.add(separator);
        buttons.add(javax.swing.Box.createVerticalStrut(10));

        FlatButton backup = FlatButton.accent("Back up the database...",
                event -> backup());
        backup.setAlignmentX(Component.LEFT_ALIGNMENT);
        backup.setMaximumSize(new Dimension(260, 36));
        buttons.add(backup);

        JLabel hint = new JLabel(Theme.wrap("A backup is a single .db file you can copy "
                + "anywhere and open with this app.", 360));
        hint.setFont(Theme.SMALL);
        hint.setForeground(Theme.MUTED);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.add(javax.swing.Box.createVerticalStrut(10));
        buttons.add(hint);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(dataNote, BorderLayout.NORTH);
        body.add(buttons, BorderLayout.CENTER);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildRulesSummary() {
        CardPanel card = new CardPanel(new BorderLayout(0, 8), 14, 14);
        card.add(Theme.section("How the rules are enforced"), BorderLayout.NORTH);

        JLabel text = new JLabel(Theme.wrap(
                "• A copy can only be issued when one is on the shelf, and the same "
                + "member cannot hold two copies of one title.\n"
                + "• Issuing is blocked when the member is inactive, at their book "
                + "limit, or holding an overdue book.\n"
                + "• Fines accrue per day past the due date and are recorded on the "
                + "loan when the return is accepted; they can be waived at the desk.\n"
                + "• A loan may be renewed twice while it is not yet overdue.\n"
                + "• Books and members with open loans cannot be deleted.", 1150));
        text.setFont(Theme.SMALL);
        text.setForeground(Theme.MUTED);
        card.add(text, BorderLayout.CENTER);
        card.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        return card;
    }

    // -------------------------------------------------------------------- data
    @Override
    public void refresh() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("libraryName", db.getSetting("library_name"));
        values.put("loanDays", db.getSetting("loan_days"));
        values.put("finePerDay", db.getSetting("fine_per_day"));
        form.load(values);

        Stats stats = db.stats();
        dataNote.setText(Theme.wrap("Database file: " + shorten(db.location(), 58) + "\n"
                + stats.titles() + " titles · " + stats.membersTotal() + " members · "
                + stats.loansActive() + " open loans · "
                + Theme.money(stats.duesOutstanding()) + " in fines outstanding", 360));
    }

    /** Keeps both ends of a long path readable instead of letting it overflow. */
    private static String shorten(String path, int max) {
        if (path.length() <= max) {
            return path;
        }
        int keep = (max - 3) / 2;
        return path.substring(0, keep) + "..." + path.substring(path.length() - keep);
    }

    // ----------------------------------------------------------------- actions
    /** Validates and stores the circulation rules. */
    public void save() {
        Map<String, String> values = form.values();
        int days;
        double fine;
        try {
            days = Integer.parseInt(values.get("loanDays").trim());
            fine = Double.parseDouble(values.get("finePerDay").trim());
        } catch (NumberFormatException exc) {
            app.dialogs().warn(this, "Check the values",
                    "Loan period must be a whole number and the fine must be a number.");
            return;
        }
        if (days < 1 || fine < 0) {
            app.dialogs().warn(this, "Check the values",
                    "Loan period must be at least 1 day, and the fine cannot be negative.");
            return;
        }
        String name = values.get("libraryName").trim();
        if (name.isEmpty()) {
            name = "Library";
        }

        try {
            db.setSetting("library_name", name);
            db.setSetting("loan_days", String.valueOf(days));
            db.setSetting("fine_per_day", String.format("%.2f", fine));
        } catch (LibraryException exc) {
            reportProblem("Could not save", exc);
            return;
        }

        app.setStatus("Settings saved.");
        app.refreshAll();
    }

    /** Writes one table to a CSV file the user chooses. */
    public void export(String table) {
        Path target = app.dialogs().chooseSaveFile(this, "Export " + table,
                table + ".csv", "CSV file", "csv");
        if (target == null) {
            return;
        }
        int rows;
        try {
            rows = db.exportCsv(table, target);
        } catch (LibraryException exc) {
            reportProblem("Export failed", exc);
            return;
        }
        app.setStatus("Exported " + rows + " " + table + " row(s) to " + target + ".");
        app.dialogs().info(this, "Export complete",
                rows + " row(s) written to:\n" + target);
    }

    /** Copies the database to a file the user chooses. */
    public void backup() {
        Path target = app.dialogs().chooseSaveFile(this, "Back up database",
                "library-backup.db", "SQLite database", "db");
        if (target == null) {
            return;
        }
        try {
            db.backup(target);
        } catch (LibraryException exc) {
            reportProblem("Backup failed", exc);
            return;
        }
        app.setStatus("Database backed up to " + target + ".");
        app.dialogs().info(this, "Backup complete", "Saved to:\n" + target);
    }

    // ------------------------------------------------------------ for the tests
    public FormGrid form() {
        return form;
    }
}
