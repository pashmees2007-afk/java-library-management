package com.library.ui.components;

import com.library.ui.Theme;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.Border;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds a label/input grid and hands back the values as a map of strings, so a
 * panel can pass them straight to the data layer for validation.
 */
public class FormGrid extends JPanel {

    private static final long serialVersionUID = 1L;

    private final Map<String, JComponent> fields = new LinkedHashMap<>();
    private final Map<String, String> defaults = new LinkedHashMap<>();
    private int row;

    public FormGrid() {
        super(new GridBagLayout());
        setOpaque(false);
    }

    private static Border inputBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(5, 7, 5, 7));
    }

    private void addLabel(String text, boolean required) {
        JLabel label = new JLabel(required ? text + " *" : text);
        label.setFont(Theme.BASE);
        label.setForeground(Theme.INK);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(5, 0, 5, 12);
        add(label, c);
    }

    private void addInput(JComponent input, boolean stretch) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = row;
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = stretch ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        c.insets = new Insets(5, 0, 5, 0);
        add(input, c);
        row++;
    }

    /** A single-line text field. Narrow fields keep their size; wide ones stretch. */
    public JTextField text(String key, String label, boolean required, int columns) {
        addLabel(label, required);
        JTextField field = new JTextField(columns);
        field.setFont(Theme.BASE);
        field.setBorder(inputBorder());
        fields.put(key, field);
        addInput(field, columns >= 18);
        return field;
    }

    public JTextField text(String key, String label) {
        return text(key, label, false, 20);
    }

    public JComboBox<String> combo(String key, String label, String[] values,
                                   boolean editable) {
        addLabel(label, false);
        JComboBox<String> combo = new JComboBox<>(values);
        combo.setFont(Theme.BASE);
        combo.setEditable(editable);
        combo.setBackground(Theme.CARD);
        fields.put(key, combo);
        addInput(combo, true);
        return combo;
    }

    public JSpinner spinner(String key, String label, int min, int max, int value) {
        addLabel(label, true);
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
        spinner.setFont(Theme.BASE);
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setColumns(4);
        fields.put(key, spinner);
        addInput(spinner, false);
        return spinner;
    }

    public JTextArea textArea(String key, String label, int rows) {
        addLabel(label, false);
        JTextArea area = new JTextArea(rows, 18);
        area.setFont(Theme.BASE);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.setPreferredSize(new Dimension(220, rows * 20 + 10));
        fields.put(key, area);
        addInput(scroll, true);
        return area;
    }

    public JCheckBox check(String key, String label, boolean selected) {
        JCheckBox box = new JCheckBox(label, selected);
        box.setFont(Theme.BASE);
        box.setForeground(Theme.INK);
        box.setOpaque(false);
        fields.put(key, box);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = row;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(5, 0, 5, 0);
        add(box, c);
        row++;
        return box;
    }

    /** A row of buttons spanning both columns. */
    public JPanel buttons(JComponent... buttons) {
        JPanel bar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        bar.setOpaque(false);
        for (JComponent button : buttons) {
            bar.add(button);
        }
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(14, -8, 0, 0);
        add(bar, c);
        row++;
        return bar;
    }

    /** Remembers the values a cleared form should fall back to. */
    public void setDefault(String key, String value) {
        defaults.put(key, value);
    }

    // ------------------------------------------------------------------- values
    public Map<String, String> values() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, JComponent> entry : fields.entrySet()) {
            out.put(entry.getKey(), valueOf(entry.getValue()));
        }
        return out;
    }

    public String value(String key) {
        JComponent field = fields.get(key);
        return field == null ? "" : valueOf(field);
    }

    public boolean checked(String key) {
        JComponent field = fields.get(key);
        return field instanceof JCheckBox box && box.isSelected();
    }

    private static String valueOf(JComponent field) {
        if (field instanceof JTextField text) {
            return text.getText().trim();
        }
        if (field instanceof JTextArea area) {
            return area.getText().trim();
        }
        if (field instanceof JComboBox<?> combo) {
            Object selected = combo.getSelectedItem();
            return selected == null ? "" : selected.toString().trim();
        }
        if (field instanceof JSpinner spinner) {
            return String.valueOf(spinner.getValue());
        }
        if (field instanceof JCheckBox box) {
            return box.isSelected() ? "1" : "0";
        }
        return "";
    }

    public void set(String key, String value) {
        JComponent field = fields.get(key);
        if (field == null) {
            return;
        }
        String safe = value == null ? "" : value;
        if (field instanceof JTextField text) {
            text.setText(safe);
        } else if (field instanceof JTextArea area) {
            area.setText(safe);
        } else if (field instanceof JComboBox<?> combo) {
            @SuppressWarnings("unchecked")
            JComboBox<String> typed = (JComboBox<String>) combo;
            if (typed.isEditable()) {
                typed.setSelectedItem(safe);
            } else {
                typed.setSelectedItem(safe.isEmpty() ? null : safe);
            }
        } else if (field instanceof JSpinner spinner) {
            try {
                spinner.setValue(Integer.valueOf(safe.isEmpty() ? "1" : safe));
            } catch (IllegalArgumentException exc) {
                spinner.setValue(1);
            }
        } else if (field instanceof JCheckBox box) {
            box.setSelected("1".equals(safe) || "true".equalsIgnoreCase(safe)
                    || "Yes".equalsIgnoreCase(safe));
        }
    }

    public void set(String key, boolean value) {
        set(key, value ? "1" : "0");
    }

    public void load(Map<String, String> values) {
        values.forEach(this::set);
    }

    /** Empties every field, then restores the defaults registered for this form. */
    public void clear() {
        for (Map.Entry<String, JComponent> entry : fields.entrySet()) {
            JComponent field = entry.getValue();
            if (field instanceof JCheckBox box) {
                box.setSelected(true);
            } else if (field instanceof JSpinner spinner) {
                spinner.setValue(1);
            } else {
                set(entry.getKey(), "");
            }
        }
        defaults.forEach(this::set);
    }

    public void focusFirst() {
        for (JComponent field : fields.values()) {
            if (field.isFocusable()) {
                field.requestFocusInWindow();
                return;
            }
        }
    }

    public Component field(String key) {
        return fields.get(key);
    }
}
