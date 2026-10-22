package com.library.ui;

import com.library.db.LibraryException;
import com.library.db.LoanFilter;
import com.library.model.Book;
import com.library.model.Loan;
import com.library.model.LoanStatus;
import com.library.model.LoanView;
import com.library.model.Member;
import com.library.model.MemberView;
import com.library.ui.components.CardPanel;
import com.library.ui.components.DataTable;
import com.library.ui.components.FlatButton;
import com.library.ui.components.Picker;
import com.library.ui.components.SearchField;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.util.List;

/** Circulation desk: issue, return, renew and settle fines. */
public class CirculationPanel extends TabPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel subtitle = Theme.muted("");
    private final SearchField search;
    private final JComboBox<String> statusFilter = new JComboBox<>(LoanFilter.labels());
    private final Picker<MemberView> memberPicker;
    private final Picker<Book> bookPicker;
    private final JSpinner loanDays;
    private final JLabel preview = new JLabel();
    private final FlatButton issueButton;
    private final DataTable<LoanView> table;

    private final JLabel selectionLabel = Theme.muted("Select a loan to act on it.");
    private final FlatButton returnButton;
    private final FlatButton renewButton;
    private final FlatButton waiveButton;
    private final FlatButton payButton;

    public CirculationPanel(AppContext app) {
        super(app);
        search = new SearchField("Book or member...", 18, this::refreshTable);
        loanDays = new JSpinner(new SpinnerNumberModel(db.loanDays(), 1, 180, 1));

        memberPicker = new Picker<>("Member",
                query -> db.listMembers(query, true),
                view -> view.member().code() + "  " + view.member().name()
                        + "  (" + view.onLoan() + "/" + view.member().borrowingLimit() + ")"
                        + (view.atLimit() ? "  !" : ""),
                "active member", "active members", 5);
        bookPicker = new Picker<>("Book on shelf",
                query -> db.listBooks(query, "All", true),
                book -> book.display() + "  [" + book.availableCopies() + "]",
                "title on shelf", "titles on shelf", 5);

        issueButton = FlatButton.accent("Issue book", event -> issue());
        returnButton = FlatButton.accent("Accept return", event -> acceptReturn(false));
        renewButton = FlatButton.soft("Renew", event -> renew());
        waiveButton = FlatButton.soft("Return & waive fine", event -> acceptReturn(true));
        payButton = FlatButton.soft("Mark fine paid", event -> markFinePaid());

        table = new DataTable<>(List.of(
                DataTable.Column.centre("Loan", 50, v -> String.valueOf(v.id())),
                DataTable.Column.text("Book", 185, LoanView::bookTitle),
                DataTable.Column.text("Member", 155, LoanView::memberDisplay),
                DataTable.Column.centre("Issued", 95,
                        v -> Theme.date(v.loan().issueDate())),
                DataTable.Column.centre("Due", 95, v -> Theme.date(v.loan().dueDate())),
                DataTable.Column.centre("Returned", 95,
                        v -> Theme.date(v.loan().returnDate())),
                DataTable.Column.right("Fine", 75, CirculationPanel::fineText),
                DataTable.Column.centre("Status", 80, v -> v.status().label())),
                LoanView::id, 16);
        table.setRowStyle(view -> switch (view.status()) {
            case OVERDUE -> DataTable.RowStyle.OVERDUE;
            case RETURNED -> DataTable.RowStyle.RETURNED;
            case ON_LOAN -> DataTable.RowStyle.NORMAL;
        });
        table.onSelect(this::onLoanSelected);
        table.onActivate(() -> acceptReturn(false));

        add(buildToolbar(), BorderLayout.NORTH);
        add(buildIssuePanel(), BorderLayout.WEST);
        add(buildLoansPanel(), BorderLayout.CENTER);

        statusFilter.setSelectedItem(LoanFilter.ACTIVE.label());
        refresh();
    }

    /** A tick for a settled fine, a cross for one still owed, nothing while accruing. */
    private static String fineText(LoanView view) {
        double fine = view.accruedFine();
        if (fine <= 0) {
            return "-";
        }
        if (view.isOpen()) {
            return Theme.money(fine);
        }
        return Theme.money(fine) + (view.loan().finePaid() ? "  ✓" : "  ✗");
    }

    // ------------------------------------------------------------------ layout
    private JPanel buildToolbar() {
        CardPanel bar = new CardPanel(new BorderLayout(12, 0), 14, 10);

        JPanel headings = new JPanel();
        headings.setOpaque(false);
        headings.setLayout(new BoxLayout(headings, BoxLayout.Y_AXIS));
        JLabel heading = Theme.section("Circulation desk");
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        headings.add(heading);
        headings.add(subtitle);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        controls.setOpaque(false);
        statusFilter.setPreferredSize(new Dimension(140, 30));
        statusFilter.addActionListener(event -> refreshTable());
        controls.add(search);
        controls.add(statusFilter);

        bar.add(headings, BorderLayout.WEST);
        bar.add(controls, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildIssuePanel() {
        CardPanel card = new CardPanel(new BorderLayout(0, 10), 14, 14);
        card.setPreferredSize(new Dimension(370, 100));
        card.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel heading = Theme.section("Issue a book");
        JLabel hint = Theme.muted("Pick a member, pick an available title, then issue.");
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.add(heading);
        head.add(hint);

        JPanel period = new JPanel(new BorderLayout());
        period.setOpaque(false);
        period.add(Theme.body("Loan period (days)"), BorderLayout.WEST);
        ((JSpinner.DefaultEditor) loanDays.getEditor()).getTextField().setColumns(4);
        loanDays.addChangeListener(event -> refreshPreview());
        JPanel spinnerHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        spinnerHolder.setOpaque(false);
        spinnerHolder.add(loanDays);
        period.add(spinnerHolder, BorderLayout.EAST);

        preview.setFont(Theme.SMALL);
        preview.setForeground(Theme.MUTED);
        preview.setVerticalAlignment(JLabel.TOP);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.add(issueButton);
        actions.add(FlatButton.soft("Reset", event -> resetPickers()));

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        memberPicker.setAlignmentX(Component.LEFT_ALIGNMENT);
        bookPicker.setAlignmentX(Component.LEFT_ALIGNMENT);
        period.setAlignmentX(Component.LEFT_ALIGNMENT);
        preview.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);

        body.add(memberPicker);
        body.add(javax.swing.Box.createVerticalStrut(12));
        body.add(bookPicker);
        body.add(javax.swing.Box.createVerticalStrut(12));
        body.add(period);
        body.add(javax.swing.Box.createVerticalStrut(10));
        body.add(preview);
        body.add(javax.swing.Box.createVerticalStrut(12));
        body.add(actions);

        memberPicker.onChange(this::refreshPreview);
        bookPicker.onChange(this::refreshPreview);

        card.add(head, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildLoansPanel() {
        JPanel holder = new JPanel(new BorderLayout(0, 12));
        holder.setOpaque(false);
        holder.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));

        CardPanel tableCard = new CardPanel(new BorderLayout(), 1, 1);
        tableCard.add(table, BorderLayout.CENTER);

        CardPanel actionCard = new CardPanel(new BorderLayout(12, 0), 12, 10);
        actionCard.add(selectionLabel, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(returnButton);
        buttons.add(renewButton);
        buttons.add(waiveButton);
        buttons.add(payButton);
        actionCard.add(buttons, BorderLayout.EAST);

        holder.add(tableCard, BorderLayout.CENTER);
        holder.add(actionCard, BorderLayout.SOUTH);
        return holder;
    }

    // -------------------------------------------------------------------- data
    @Override
    public void refresh() {
        memberPicker.reload();
        bookPicker.reload();
        refreshTable();
        refreshPreview();
    }

    /** Reloads just the loan list, for a filter or search change. */
    public void refreshTable() {
        LoanFilter filter = LoanFilter.fromLabel(String.valueOf(statusFilter.getSelectedItem()));
        List<LoanView> loans = db.listLoans(filter, search.text());
        table.setRows(loans);

        long overdue = loans.stream().filter(v -> v.status() == LoanStatus.OVERDUE).count();
        subtitle.setText(Theme.plural(loans.size(), "loan", "loans") + " shown"
                + (overdue > 0 ? " · " + overdue + " overdue" : ""));
        onLoanSelected();
    }

    private int days() {
        Object value = loanDays.getValue();
        return value instanceof Number number ? Math.max(1, number.intValue()) : db.loanDays();
    }

    private void refreshPreview() {
        MemberView member = memberPicker.selected();
        Book book = bookPicker.selected();
        LocalDate due = db.today().plusDays(days());

        StringBuilder text = new StringBuilder("Due back on " + Theme.date(due) + ".");
        if (member != null) {
            List<LoanView> open = db.loansForMember(member.id(), true);
            text.append("\n").append(member.name()).append(" holds ").append(open.size())
                .append(" of ").append(member.member().borrowingLimit()).append(" books.");
            long overdue = open.stream()
                    .filter(v -> v.status() == LoanStatus.OVERDUE).count();
            if (overdue > 0) {
                text.append("\n! ").append(overdue)
                    .append(" overdue — returns needed first.");
            }
        }
        if (book != null) {
            text.append("\n\"").append(book.title()).append("\" — ")
                .append(book.availableCopies()).append(" on shelf.");
        }
        preview.setText(Theme.wrap(text.toString(), 320));
        issueButton.setEnabled(member != null && book != null);
    }

    private void onLoanSelected() {
        LoanView view = table.getSelected();
        if (view == null) {
            selectionLabel.setText("Select a loan to act on it.");
            selectionLabel.setForeground(Theme.MUTED);
            returnButton.setEnabled(false);
            renewButton.setEnabled(false);
            waiveButton.setEnabled(false);
            payButton.setEnabled(false);
            return;
        }

        Loan loan = view.loan();
        double fine = view.accruedFine();
        StringBuilder text = new StringBuilder("Loan #" + loan.id() + ": \""
                + view.bookTitle() + "\" → " + view.memberName());
        if (view.isOpen()) {
            if (view.status() == LoanStatus.OVERDUE) {
                text.append(" · ").append(view.daysLate()).append(" day(s) late, fine ")
                    .append(Theme.money(fine));
            } else {
                text.append(" · due ").append(Theme.date(loan.dueDate()));
            }
        } else {
            text.append(" · returned ").append(Theme.date(loan.returnDate()));
            if (loan.fine() > 0) {
                text.append(" · fine ").append(Theme.money(loan.fine()))
                    .append(loan.finePaid() ? " (paid)" : " (unpaid)");
            }
        }
        selectionLabel.setText(text.toString());
        selectionLabel.setForeground(
                view.status() == LoanStatus.OVERDUE ? Theme.DANGER : Theme.MUTED);

        returnButton.setEnabled(view.isOpen());
        waiveButton.setEnabled(view.isOpen() && fine > 0);
        renewButton.setEnabled(view.isOpen() && view.status() == LoanStatus.ON_LOAN);
        payButton.setEnabled(loan.fine() > 0 && !loan.finePaid());
    }

    // ----------------------------------------------------------------- actions
    /** Issues the chosen title to the chosen member. */
    public void issue() {
        MemberView member = memberPicker.selected();
        Book book = bookPicker.selected();
        if (member == null || book == null) {
            app.dialogs().info(this, "Pick both", "Choose a member and a book first.");
            return;
        }
        int loanId;
        try {
            loanId = db.issueBook(book.id(), member.id(), days());
        } catch (LibraryException exc) {
            reportProblem("Cannot issue", exc);
            return;
        }

        Loan loan = db.getLoan(loanId);
        app.setStatus("Issued \"" + book.title() + "\" to " + member.name()
                + " — due " + Theme.date(loan.dueDate()) + ".");
        statusFilter.setSelectedItem(LoanFilter.ACTIVE.label());
        bookPicker.clear();
        app.refreshAll();
        table.selectId(loanId);
    }

    /** Accepts the selected return, charging or waiving any fine. */
    public void acceptReturn(boolean waive) {
        LoanView view = table.getSelected();
        if (view == null || !view.isOpen()) {
            return;
        }
        double fine = view.accruedFine();
        if (fine > 0 && !waive) {
            boolean charge = app.dialogs().confirm(this, "Overdue return",
                    "This return is late. Charge a fine of " + Theme.money(fine) + "?\n\n"
                            + "Choose No to leave the loan open.");
            if (!charge) {
                return;
            }
        }

        double charged;
        try {
            charged = db.returnBook(view.id(), waive);
        } catch (LibraryException exc) {
            reportProblem("Cannot return", exc);
            return;
        }

        String message = "Return accepted.";
        if (charged > 0) {
            message += " Fine of " + Theme.money(charged) + " recorded as unpaid.";
        } else if (waive && fine > 0) {
            message += " Fine of " + Theme.money(fine) + " waived.";
        }
        app.setStatus(message);
        app.refreshAll();
    }

    /** Extends the selected loan. */
    public void renew() {
        LoanView view = table.getSelected();
        if (view == null) {
            return;
        }
        LocalDate newDue;
        try {
            newDue = db.renewLoan(view.id(), days());
        } catch (LibraryException exc) {
            reportProblem("Cannot renew", exc);
            return;
        }
        app.setStatus("Loan renewed — now due " + Theme.date(newDue) + ".");
        app.refreshAll();
    }

    /** Marks the selected loan's fine as settled. */
    public void markFinePaid() {
        LoanView view = table.getSelected();
        if (view == null) {
            return;
        }
        try {
            db.payFine(view.id());
        } catch (LibraryException exc) {
            reportProblem("Cannot settle", exc);
            return;
        }
        app.setStatus("Fine marked as paid.");
        app.refreshAll();
    }

    public void resetPickers() {
        memberPicker.clear();
        bookPicker.clear();
        loanDays.setValue(db.loanDays());
    }

    @Override
    public void newRecord() {
        resetPickers();
    }

    @Override
    public void focusSearch() {
        search.requestFocus();
    }

    // ------------------------------------------------------------ for the tests
    public DataTable<LoanView> table() {
        return table;
    }

    public Picker<MemberView> memberPicker() {
        return memberPicker;
    }

    public Picker<Book> bookPicker() {
        return bookPicker;
    }

    public SearchField search() {
        return search;
    }

    public void setStatusFilter(LoanFilter filter) {
        statusFilter.setSelectedItem(filter.label());
    }

    public String previewText() {
        return preview.getText();
    }

    public boolean canIssue() {
        return issueButton.isEnabled();
    }

    public boolean canReturn() {
        return returnButton.isEnabled();
    }

    public boolean canRenew() {
        return renewButton.isEnabled();
    }

    public boolean canWaive() {
        return waiveButton.isEnabled();
    }

    public boolean canPayFine() {
        return payButton.isEnabled();
    }

    /** Looks up a member by code in the picker and selects them. */
    public boolean selectMember(String search) {
        memberPicker.setQuery(search);
        memberPicker.reload();
        if (memberPicker.count() > 0) {
            memberPicker.select(0);
            return true;
        }
        return false;
    }

    /** Looks up a title in the picker and selects it. */
    public boolean selectBook(String search) {
        bookPicker.setQuery(search);
        bookPicker.reload();
        if (bookPicker.count() > 0) {
            bookPicker.select(0);
            return true;
        }
        return false;
    }

    public void setLoanDays(int days) {
        loanDays.setValue(days);
    }

    /** The member currently chosen, or {@code null}. */
    public Member chosenMember() {
        MemberView view = memberPicker.selected();
        return view == null ? null : view.member();
    }
}
