package com.library.ui;

import com.library.db.BookDraft;
import com.library.db.LibraryException;
import com.library.model.Book;
import com.library.ui.components.CardPanel;
import com.library.ui.components.DataTable;
import com.library.ui.components.FlatButton;
import com.library.ui.components.FormGrid;
import com.library.ui.components.SearchField;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Catalogue tab: browse, add, edit and remove books. */
public class BooksPanel extends TabPanel {

    private static final long serialVersionUID = 1L;

    private static final String[] CATEGORIES = {
            "General", "Fiction", "Non-fiction", "Science", "Sci-Fi", "History",
            "Biography", "Technology", "Mathematics", "Reference", "Children",
    };

    private final JLabel subtitle = Theme.muted("");
    private final SearchField search;
    private final JComboBox<String> categoryFilter = new JComboBox<>();
    private final JCheckBox onShelfOnly = new JCheckBox("On shelf only");
    private final DataTable<Book> table;
    private final FormGrid form = new FormGrid();
    private final JLabel formTitle = Theme.section("Add a book");
    private final JLabel formHint = Theme.muted("Fields marked * are required.");
    private final JLabel detail = new JLabel();

    private Integer editingId;
    private boolean loadingFilters;

    public BooksPanel(AppContext app) {
        super(app);
        search = new SearchField("Title, author, ISBN...", 22, this::refresh);

        table = new DataTable<>(List.of(
                DataTable.Column.text("Title", 210, Book::title),
                DataTable.Column.text("Author", 140, Book::author),
                DataTable.Column.text("Category", 100, Book::category),
                DataTable.Column.centre("Year", 55,
                        b -> b.year() == null ? "-" : String.valueOf(b.year())),
                DataTable.Column.text("ISBN", 115, b -> Theme.orDash(b.isbn())),
                DataTable.Column.centre("Shelf", 60, b -> Theme.orDash(b.shelf())),
                DataTable.Column.centre("Copies", 60,
                        b -> String.valueOf(b.totalCopies())),
                DataTable.Column.centre("On shelf", 70,
                        b -> String.valueOf(b.availableCopies()))),
                Book::id, 18);
        table.setRowStyle(book -> book.onShelf()
                ? DataTable.RowStyle.NORMAL : DataTable.RowStyle.WARN);
        table.onSelect(this::onSelect);
        table.onActivate(form::focusFirst);

        add(buildToolbar(), BorderLayout.NORTH);
        add(buildTable(), BorderLayout.CENTER);
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
        JLabel heading = Theme.section("Book catalogue");
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        headings.add(heading);
        headings.add(subtitle);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        controls.setOpaque(false);
        categoryFilter.setPreferredSize(new Dimension(150, 30));
        categoryFilter.addActionListener(event -> {
            if (!loadingFilters) {
                refresh();
            }
        });
        onShelfOnly.setOpaque(false);
        onShelfOnly.setFont(Theme.BASE);
        onShelfOnly.addActionListener(event -> refresh());

        controls.add(search);
        controls.add(categoryFilter);
        controls.add(onShelfOnly);
        controls.add(FlatButton.accent("+  New book", event -> newRecord()));

        bar.add(headings, BorderLayout.WEST);
        bar.add(controls, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildTable() {
        CardPanel card = new CardPanel(new BorderLayout(), 1, 1);
        card.add(table, BorderLayout.CENTER);
        return card;
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

        form.text("title", "Title", true, 18);
        form.text("author", "Author", true, 18);
        form.combo("category", "Category", CATEGORIES, true);
        form.text("isbn", "ISBN", false, 18);
        form.text("publisher", "Publisher", false, 18);
        form.text("year", "Year", false, 6);
        form.text("shelf", "Shelf code", false, 8);
        form.spinner("totalCopies", "Total copies", 1, 500, 1);
        form.setDefault("category", "General");
        form.buttons(
                FlatButton.accent("Save", event -> save()),
                FlatButton.soft("Clear", event -> newRecord()),
                FlatButton.danger("Delete", event -> delete()));

        detail.setFont(Theme.SMALL);
        detail.setForeground(Theme.MUTED);
        detail.setVerticalAlignment(JLabel.TOP);
        detail.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        body.add(form, BorderLayout.NORTH);
        body.add(detail, BorderLayout.CENTER);

        card.add(head, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    // -------------------------------------------------------------------- data
    @Override
    public void refresh() {
        loadingFilters = true;
        Object selectedCategory = categoryFilter.getSelectedItem();
        List<String> categories = new ArrayList<>();
        categories.add("All");
        categories.addAll(db.categories());
        categoryFilter.removeAllItems();
        for (String category : categories) {
            categoryFilter.addItem(category);
        }
        categoryFilter.setSelectedItem(
                categories.contains(selectedCategory) ? selectedCategory : "All");
        loadingFilters = false;

        String category = String.valueOf(categoryFilter.getSelectedItem());
        List<Book> books = db.listBooks(search.text(), category, onShelfOnly.isSelected());
        table.setRows(books);

        int onShelf = books.stream().mapToInt(Book::availableCopies).sum();
        subtitle.setText(Theme.plural(books.size(), "title", "titles") + " listed · "
                + onShelf + " copies on shelf");
    }

    private void onSelect() {
        Book book = table.getSelected();
        if (book == null) {
            detail.setText("");
            return;
        }
        editingId = book.id();
        formTitle.setText("Edit book");
        formHint.setText("Record #" + book.id() + " — changes overwrite it.");
        form.load(Map.of(
                "title", book.title(),
                "author", book.author(),
                "category", book.category() == null ? "" : book.category(),
                "isbn", book.isbn() == null ? "" : book.isbn(),
                "publisher", book.publisher() == null ? "" : book.publisher(),
                "year", book.year() == null ? "" : String.valueOf(book.year()),
                "shelf", book.shelf() == null ? "" : book.shelf(),
                "totalCopies", String.valueOf(book.totalCopies())));
        detail.setText(Theme.wrap("Added " + Theme.date(book.addedOn()) + "\n"
                + book.copiesOnLoan() + " of " + book.totalCopies()
                + " copies currently issued.", 290));
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
        formTitle.setText("Add a book");
        formHint.setText("Fields marked * are required.");
        detail.setText("");
        if (focus) {
            form.focusFirst();
        }
    }

    private BookDraft draft() {
        Map<String, String> values = form.values();
        return new BookDraft(values.get("title"), values.get("author"),
                values.get("isbn"), values.get("category"), values.get("publisher"),
                values.get("year"), values.get("shelf"), values.get("totalCopies"));
    }

    /** Saves the form, adding a new book or updating the selected one. */
    public void save() {
        BookDraft draft = draft();
        int bookId;
        try {
            if (editingId == null) {
                bookId = db.addBook(draft);
                app.setStatus("Added \"" + draft.title() + "\" to the catalogue.");
            } else {
                bookId = editingId;
                db.updateBook(bookId, draft);
                app.setStatus("Updated \"" + draft.title() + "\".");
            }
        } catch (LibraryException exc) {
            reportProblem("Check the form", exc);
            return;
        }

        editingId = bookId;
        app.refreshAll();
        table.selectId(bookId);
    }

    /** Deletes the selected book once the user confirms. */
    public void delete() {
        Integer bookId = table.getSelectedId() != null ? table.getSelectedId() : editingId;
        if (bookId == null) {
            app.dialogs().info(this, "Nothing selected", "Pick a book from the list first.");
            return;
        }
        Book book = db.getBook(bookId);
        if (book == null) {
            return;
        }
        if (!app.dialogs().confirm(this, "Delete book",
                "Delete \"" + book.title() + "\" and its loan history?\n"
                        + "This cannot be undone.")) {
            return;
        }
        try {
            db.deleteBook(bookId);
        } catch (LibraryException exc) {
            reportProblem("Cannot delete", exc);
            return;
        }
        app.setStatus("Deleted \"" + book.title() + "\".");
        newRecord(false);
        app.refreshAll();
    }

    @Override
    public void focusSearch() {
        search.requestFocus();
    }

    // ------------------------------------------------------------ for the tests
    public DataTable<Book> table() {
        return table;
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

    public void setCategoryFilter(String category) {
        categoryFilter.setSelectedItem(category);
    }
}
