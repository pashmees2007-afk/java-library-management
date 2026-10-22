package com.library.ui;

import com.library.db.LibraryException;
import com.library.db.MemberDraft;
import com.library.model.LoanStatus;
import com.library.model.LoanView;
import com.library.model.Member;
import com.library.model.MemberView;
import com.library.model.MembershipType;
import com.library.ui.components.CardPanel;
import com.library.ui.components.DataTable;
import com.library.ui.components.FlatButton;
import com.library.ui.components.FormGrid;
import com.library.ui.components.SearchField;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Members tab: member records, borrowing limits and per-member history. */
public class MembersPanel extends TabPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel subtitle = Theme.muted("");
    private final SearchField search;
    private final JCheckBox activeOnly = new JCheckBox("Active only");
    private final DataTable<MemberView> table;
    private final DataTable<LoanView> history;
    private final JLabel historyTitle = Theme.section("Borrowing history");
    private final FormGrid form = new FormGrid();
    private final JLabel formTitle = Theme.section("Add a member");
    private final JLabel formHint = Theme.muted("Member ID is pre-filled; edit it if you like.");
    private final JLabel summary = new JLabel();

    private Integer editingId;

    public MembersPanel(AppContext app) {
        super(app);
        search = new SearchField("Name, ID, phone...", 20, this::refresh);

        table = new DataTable<>(List.of(
                DataTable.Column.centre("Member ID", 85, v -> v.member().code()),
                DataTable.Column.text("Name", 150, v -> v.member().name()),
                DataTable.Column.text("Type", 80, v -> v.member().type().label()),
                DataTable.Column.text("Phone", 110, v -> Theme.orDash(v.member().phone())),
                DataTable.Column.text("Email", 170, v -> Theme.orDash(v.member().email())),
                DataTable.Column.centre("On loan", 70, v -> String.valueOf(v.onLoan())),
                DataTable.Column.right("Dues", 70, v -> Theme.money(v.dues())),
                DataTable.Column.centre("Status", 75,
                        v -> v.member().active() ? "Active" : "Inactive")),
                MemberView::id, 11);
        table.setRowStyle(view -> view.member().active()
                ? DataTable.RowStyle.NORMAL : DataTable.RowStyle.INACTIVE);
        table.onSelect(this::onSelect);

        history = new DataTable<>(List.of(
                DataTable.Column.text("Book", 230, LoanView::bookTitle),
                DataTable.Column.centre("Issued", 100,
                        v -> Theme.date(v.loan().issueDate())),
                DataTable.Column.centre("Due", 100, v -> Theme.date(v.loan().dueDate())),
                DataTable.Column.centre("Returned", 100,
                        v -> Theme.date(v.loan().returnDate())),
                DataTable.Column.right("Fine", 70,
                        v -> v.accruedFine() > 0 ? Theme.money(v.accruedFine()) : "-"),
                DataTable.Column.centre("Status", 90, v -> v.status().label())),
                LoanView::id, 6);
        history.setRowStyle(view -> switch (view.status()) {
            case OVERDUE -> DataTable.RowStyle.OVERDUE;
            case RETURNED -> DataTable.RowStyle.RETURNED;
            case ON_LOAN -> DataTable.RowStyle.NORMAL;
        });

        add(buildToolbar(), BorderLayout.NORTH);
        add(buildCentre(), BorderLayout.CENTER);
        add(buildForm(), BorderLayout.EAST);

        newRecord(false);
        refresh();
    }

    // ------------------------------------------------------------------ layout
    private JPanel buildToolbar() {
        CardPanel bar = new CardPanel(new BorderLayout(12, 0), 14, 10);

        JPanel headings = new JPanel();
        headings.setOpaque(false);
        headings.setLayout(new BoxLayout(headings, BoxLayout.Y_AXIS));
        JLabel heading = Theme.section("Members");
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        headings.add(heading);
        headings.add(subtitle);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        controls.setOpaque(false);
        activeOnly.setOpaque(false);
        activeOnly.setFont(Theme.BASE);
        activeOnly.addActionListener(event -> refresh());
        controls.add(search);
        controls.add(activeOnly);
        controls.add(FlatButton.accent("+  New member", event -> newRecord()));

        bar.add(headings, BorderLayout.WEST);
        bar.add(controls, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildCentre() {
        JPanel centre = new JPanel(new BorderLayout(0, 12));
        centre.setOpaque(false);

        CardPanel listCard = new CardPanel(new BorderLayout(), 1, 1);
        listCard.add(table, BorderLayout.CENTER);

        CardPanel historyCard = new CardPanel(new BorderLayout(0, 8), 10, 10);
        historyCard.setPreferredSize(new Dimension(100, 220));
        historyCard.add(historyTitle, BorderLayout.NORTH);
        historyCard.add(history, BorderLayout.CENTER);

        centre.add(listCard, BorderLayout.CENTER);
        centre.add(historyCard, BorderLayout.SOUTH);
        return centre;
    }

    private JPanel buildForm() {
        CardPanel card = new CardPanel(new BorderLayout(0, 10), 14, 14);
        card.setPreferredSize(new Dimension(340, 100));

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        formTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        formHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.add(formTitle);
        head.add(formHint);

        form.text("name", "Full name", true, 18);
        form.text("code", "Member ID", false, 10);
        form.combo("type", "Membership", MembershipType.labels(), false);
        form.text("email", "Email", false, 18);
        form.text("phone", "Phone", false, 12);
        form.textArea("address", "Address", 3);
        form.text("maxBooks", "Book limit", false, 5);
        form.text("joinDate", "Joined", false, 10);
        form.check("active", "Membership active", true);
        form.buttons(
                FlatButton.accent("Save", event -> save()),
                FlatButton.soft("Clear", event -> newRecord()),
                FlatButton.danger("Delete", event -> delete()));

        summary.setFont(Theme.SMALL);
        summary.setForeground(Theme.MUTED);
        summary.setVerticalAlignment(JLabel.TOP);
        summary.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        body.add(form, BorderLayout.NORTH);
        body.add(summary, BorderLayout.CENTER);

        card.add(head, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    // -------------------------------------------------------------------- data
    @Override
    public void refresh() {
        List<MemberView> members = db.listMembers(search.text(), activeOnly.isSelected());
        table.setRows(members);
        long active = members.stream().filter(v -> v.member().active()).count();
        subtitle.setText(members.size() + " listed · " + active + " active");
    }

    private void onSelect() {
        MemberView view = table.getSelected();
        if (view == null) {
            history.setRows(List.of());
            historyTitle.setText("Borrowing history");
            summary.setText("");
            return;
        }
        Member member = view.member();
        editingId = member.id();
        formTitle.setText("Edit member");
        formHint.setText("Record #" + member.id() + " — changes overwrite it.");

        Map<String, String> values = new LinkedHashMap<>();
        values.put("name", member.name());
        values.put("code", member.code());
        values.put("type", member.type().label());
        values.put("email", member.email() == null ? "" : member.email());
        values.put("phone", member.phone() == null ? "" : member.phone());
        values.put("address", member.address() == null ? "" : member.address());
        values.put("maxBooks", member.maxBooks() == null ? ""
                : String.valueOf(member.maxBooks()));
        values.put("joinDate", member.joinDate() == null ? ""
                : member.joinDate().toString());
        values.put("active", member.active() ? "1" : "0");
        form.load(values);

        List<LoanView> loans = db.loansForMember(member.id(), false);
        history.setRows(loans);
        historyTitle.setText("Borrowing history — " + member.name()
                + " (" + Theme.plural(loans.size(), "loan", "loans") + ")");

        long open = loans.stream().filter(LoanView::isOpen).count();
        summary.setText(Theme.wrap("Joined " + Theme.date(member.joinDate()) + "\n"
                + "Holding " + open + " of " + member.borrowingLimit()
                + " permitted books.\n"
                + "Outstanding dues: " + Theme.money(view.dues()), 290));
    }

    // ----------------------------------------------------------------- actions
    @Override
    public void newRecord() {
        newRecord(true);
    }

    private void newRecord(boolean focus) {
        editingId = null;
        table.clearSelection();
        form.clear();
        form.set("code", db.nextMemberCode());
        form.set("type", MembershipType.PUBLIC.label());
        form.set("joinDate", db.today().toString());
        form.set("active", true);
        formTitle.setText("Add a member");
        formHint.setText("Member ID is pre-filled; edit it if you like.");
        summary.setText("");
        history.setRows(List.of());
        if (focus) {
            form.focusFirst();
        }
    }

    private MemberDraft draft() {
        Map<String, String> values = form.values();
        return new MemberDraft(values.get("code"), values.get("name"),
                values.get("email"), values.get("phone"), values.get("address"),
                values.get("type"), values.get("joinDate"), form.checked("active"),
                values.get("maxBooks"));
    }

    /** Saves the form, adding a new member or updating the selected one. */
    public void save() {
        MemberDraft draft = draft();
        int memberId;
        try {
            if (editingId == null) {
                memberId = db.addMember(draft);
                app.setStatus("Added member " + draft.name() + ".");
            } else {
                memberId = editingId;
                db.updateMember(memberId, draft);
                app.setStatus("Updated " + draft.name() + "'s record.");
            }
        } catch (LibraryException exc) {
            reportProblem("Check the form", exc);
            return;
        }

        editingId = memberId;
        app.refreshAll();
        table.selectId(memberId);
    }

    /** Deletes the selected member once the user confirms. */
    public void delete() {
        Integer memberId = table.getSelectedId() != null ? table.getSelectedId() : editingId;
        if (memberId == null) {
            app.dialogs().info(this, "Nothing selected",
                    "Pick a member from the list first.");
            return;
        }
        Member member = db.getMember(memberId);
        if (member == null) {
            return;
        }
        if (!app.dialogs().confirm(this, "Delete member",
                "Delete " + member.name() + " (" + member.code() + ") and their loan "
                        + "history?\nThis cannot be undone.")) {
            return;
        }
        try {
            db.deleteMember(memberId);
        } catch (LibraryException exc) {
            reportProblem("Cannot delete", exc);
            return;
        }
        app.setStatus("Deleted " + member.name() + ".");
        newRecord(false);
        app.refreshAll();
    }

    @Override
    public void focusSearch() {
        search.requestFocus();
    }

    // ------------------------------------------------------------ for the tests
    public DataTable<MemberView> table() {
        return table;
    }

    public DataTable<LoanView> history() {
        return history;
    }

    public String historyTitle() {
        return historyTitle.getText();
    }

    public FormGrid form() {
        return form;
    }

    public SearchField search() {
        return search;
    }

    public Integer editingId() {
        return editingId;
    }

    /** Used by the tests to confirm the status colouring picks up overdue loans. */
    public long overdueInHistory() {
        return history.rows().stream().filter(v -> v.status() == LoanStatus.OVERDUE).count();
    }
}
