package com.library.ui.components;

import com.library.ui.Theme;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.ScrollPaneConstants;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * A sortable table with zebra striping and per-row colouring, over a list of
 * model objects. Selection is tracked by record id, so a refresh keeps the row
 * the user was looking at.
 *
 * @param <T> the row type, e.g. {@code Book}
 */
public class DataTable<T> extends JPanel {

    private static final long serialVersionUID = 1L;

    /** How a row should read at a glance. */
    public enum RowStyle {
        NORMAL(Theme.INK),
        OVERDUE(Theme.DANGER),
        RETURNED(Theme.MUTED),
        WARN(Theme.WARN),
        INACTIVE(Theme.MUTED);

        private final Color colour;

        RowStyle(Color colour) {
            this.colour = colour;
        }

        public Color colour() {
            return colour;
        }
    }

    /** One column: its heading, preferred width, alignment and how to render a row. */
    public record Column<T>(String heading, int width, int align, Function<T, String> value) {

        public static <T> Column<T> text(String heading, int width, Function<T, String> value) {
            return new Column<>(heading, width, SwingConstants.LEFT, value);
        }

        public static <T> Column<T> centre(String heading, int width,
                                           Function<T, String> value) {
            return new Column<>(heading, width, SwingConstants.CENTER, value);
        }

        public static <T> Column<T> right(String heading, int width,
                                          Function<T, String> value) {
            return new Column<>(heading, width, SwingConstants.RIGHT, value);
        }
    }

    private final List<Column<T>> columns;
    private final ToIntFunction<T> idOf;
    private final List<T> rows = new ArrayList<>();
    // Built in the constructor, not here: the model reads `columns`, which a
    // field initialiser would run before the constructor body can assign it.
    private final Model model;
    private final JTable table;
    private final TableRowSorter<Model> sorter;

    private Function<T, RowStyle> styleOf = row -> RowStyle.NORMAL;
    private Runnable onSelect;
    private Runnable onActivate;
    private boolean updating;

    public DataTable(List<Column<T>> columns, ToIntFunction<T> idOf, int visibleRows) {
        super(new BorderLayout());
        this.columns = List.copyOf(columns);
        this.idOf = idOf;
        this.model = new Model();
        this.table = new JTable(model);
        this.sorter = new TableRowSorter<>(model);
        setOpaque(false);

        table.setRowHeight(28);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.setRowSorter(sorter);
        table.setBackground(Theme.CARD);
        table.setForeground(Theme.INK);

        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setPreferredSize(new Dimension(0, 32));
        header.setBackground(Theme.HEADER);
        header.setForeground(Theme.INK);
        header.setFont(Theme.SMALL_BOLD);

        for (int i = 0; i < this.columns.size(); i++) {
            Column<T> column = this.columns.get(i);
            TableColumn tableColumn = table.getColumnModel().getColumn(i);
            tableColumn.setPreferredWidth(column.width());
            tableColumn.setMinWidth(Math.min(45, column.width()));
            tableColumn.setCellRenderer(new CellRenderer(column.align()));
            sorter.setComparator(i, SMART);
        }

        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !updating && onSelect != null) {
                onSelect.run();
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && onActivate != null) {
                    onActivate.run();
                }
            }
        });
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke("ENTER"), "activateRow");
        table.getActionMap().put("activateRow", new javax.swing.AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                if (onActivate != null) {
                    onActivate.run();
                }
            }
        });

        JScrollPane scroll = new JScrollPane(table,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.CARD);
        add(scroll, BorderLayout.CENTER);
    }

    /** Sorts text naturally but numbers numerically, so "12.00" beats "9.00". */
    private static final Comparator<Object> SMART = (left, right) -> {
        String a = String.valueOf(left);
        String b = String.valueOf(right);
        Double da = numeric(a);
        Double db = numeric(b);
        if (da != null && db != null) {
            return Double.compare(da, db);
        }
        if (da != null) {
            return -1;
        }
        if (db != null) {
            return 1;
        }
        return a.compareToIgnoreCase(b);
    };

    private static Double numeric(String text) {
        String cleaned = text.replace(",", "").replace("✓", "").replace("✗", "")
                .trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(cleaned);
        } catch (NumberFormatException exc) {
            return null;
        }
    }

    // ------------------------------------------------------------------ content
    public void setRowStyle(Function<T, RowStyle> styleOf) {
        this.styleOf = styleOf;
    }

    public void onSelect(Runnable listener) {
        this.onSelect = listener;
    }

    public void onActivate(Runnable listener) {
        this.onActivate = listener;
    }

    /** Replaces every row, keeping the selected record selected when it is still there. */
    public void setRows(List<T> newRows) {
        Integer keep = getSelectedId();
        List<? extends RowSorter.SortKey> sortKeys = sorter.getSortKeys();

        updating = true;
        rows.clear();
        rows.addAll(newRows);
        model.fireTableDataChanged();
        if (!sortKeys.isEmpty()) {
            sorter.setSortKeys(sortKeys);
        }
        updating = false;

        if (keep != null) {
            selectId(keep);
        }
        if (onSelect != null) {
            onSelect.run();
        }
    }

    public int rowCount() {
        return rows.size();
    }

    public List<T> rows() {
        return List.copyOf(rows);
    }

    // ---------------------------------------------------------------- selection
    public T getSelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        return modelRow >= 0 && modelRow < rows.size() ? rows.get(modelRow) : null;
    }

    public Integer getSelectedId() {
        T selected = getSelected();
        return selected == null ? null : idOf.applyAsInt(selected);
    }

    public void selectId(int id) {
        for (int modelRow = 0; modelRow < rows.size(); modelRow++) {
            if (idOf.applyAsInt(rows.get(modelRow)) == id) {
                int viewRow = table.convertRowIndexToView(modelRow);
                if (viewRow >= 0) {
                    table.setRowSelectionInterval(viewRow, viewRow);
                    table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
                }
                return;
            }
        }
    }

    public void selectFirst() {
        if (table.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
        }
    }

    public void clearSelection() {
        table.clearSelection();
    }

    /** Sorts by a column, as if its heading had been clicked. */
    public void sortBy(int column, boolean descending) {
        sorter.setSortKeys(List.of(new RowSorter.SortKey(column,
                descending ? SortOrder.DESCENDING : SortOrder.ASCENDING)));
    }

    public JTable table() {
        return table;
    }

    // -------------------------------------------------------------------- model
    private class Model extends AbstractTableModel {
        private static final long serialVersionUID = 1L;

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.size();
        }

        @Override
        public String getColumnName(int column) {
            return columns.get(column).heading();
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            String value = columns.get(columnIndex).value().apply(rows.get(rowIndex));
            return value == null ? "" : value;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }

    private class CellRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        CellRenderer(int align) {
            setHorizontalAlignment(align);
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        }

        @Override
        public Component getTableCellRendererComponent(JTable source, Object value,
                                                       boolean selected, boolean focused,
                                                       int row, int column) {
            super.getTableCellRendererComponent(source, value, selected, false, row, column);
            int modelRow = source.convertRowIndexToModel(row);
            RowStyle style = modelRow >= 0 && modelRow < rows.size()
                    ? styleOf.apply(rows.get(modelRow)) : RowStyle.NORMAL;

            if (selected) {
                setBackground(Theme.SELECTION);
            } else {
                setBackground(row % 2 == 0 ? Theme.CARD : Theme.STRIPE);
            }
            setForeground(style.colour());
            setFont(Theme.BASE);
            return this;
        }
    }
}
