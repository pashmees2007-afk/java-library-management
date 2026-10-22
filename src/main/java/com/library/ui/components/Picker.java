package com.library.ui.components;

import com.library.ui.Theme;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Type-to-filter chooser: a text box that narrows a short list. Used at the
 * circulation desk to pick the member and the book.
 *
 * @param <T> the kind of record being chosen
 */
public class Picker<T> extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JTextField query = new JTextField();
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> list = new JList<>(listModel);
    private final JLabel note = new JLabel(" ");

    private final Function<String, List<T>> fetch;
    private final Function<T, String> render;
    private final String singular;
    private final String plural;
    private final List<T> items = new ArrayList<>();
    private Runnable onChange;

    public Picker(String label, Function<String, List<T>> fetch, Function<T, String> render,
                  String singular, String plural, int visibleRows) {
        super(new BorderLayout(0, 4));
        this.fetch = fetch;
        this.render = render;
        this.singular = singular;
        this.plural = plural;
        setOpaque(false);

        JLabel caption = new JLabel(label);
        caption.setFont(Theme.BASE);
        caption.setForeground(Theme.INK);

        query.setFont(Theme.BASE);
        query.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(5, 7, 5, 7)));
        query.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                reload();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                reload();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                reload();
            }
        });

        list.setFont(Theme.BASE);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(Theme.CARD);
        list.setForeground(Theme.INK);
        list.setVisibleRowCount(visibleRows);
        list.setFixedCellHeight(22);
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && onChange != null) {
                onChange.run();
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.setPreferredSize(new Dimension(300, visibleRows * 22 + 6));

        note.setFont(Theme.SMALL);
        note.setForeground(Theme.MUTED);

        JPanel head = new JPanel(new BorderLayout(0, 4));
        head.setOpaque(false);
        head.add(caption, BorderLayout.NORTH);
        head.add(query, BorderLayout.SOUTH);

        add(head, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(note, BorderLayout.SOUTH);
    }

    public void onChange(Runnable listener) {
        this.onChange = listener;
    }

    /** Re-runs the search, keeping the current choice selected when it still matches. */
    public void reload() {
        T keep = selected();
        List<T> found = fetch.apply(query.getText().trim());

        items.clear();
        items.addAll(found);
        listModel.clear();
        for (T item : found) {
            listModel.addElement(render.apply(item));
        }

        int index = keep == null ? -1 : items.indexOf(keep);
        if (index >= 0) {
            list.setSelectedIndex(index);
        } else if (found.size() == 1) {
            list.setSelectedIndex(0);
        } else if (onChange != null) {
            onChange.run();
        }

        boolean searching = !query.getText().trim().isEmpty();
        note.setText(searching
                ? found.size() + (found.size() == 1 ? " match" : " matches")
                : found.size() + " " + (found.size() == 1 ? singular : plural));
    }

    public T selected() {
        int index = list.getSelectedIndex();
        return index >= 0 && index < items.size() ? items.get(index) : null;
    }

    public void select(int index) {
        if (index >= 0 && index < items.size()) {
            list.setSelectedIndex(index);
        }
    }

    public void setQuery(String text) {
        query.setText(text);
    }

    public void clear() {
        list.clearSelection();
        query.setText("");
        reload();
    }

    /** How many records the current filter matched. */
    public int count() {
        return items.size();
    }
}
