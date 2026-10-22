package com.library.ui.components;

import com.library.ui.Theme;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * A search box that reports changes a moment after typing stops, so a keystroke
 * does not trigger a database query each time.
 */
public class SearchField extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JTextField field = new JTextField();
    private final Timer debounce;
    private final String placeholder;

    public SearchField(String placeholder, int columns, Runnable onChange) {
        super(new BorderLayout(6, 0));
        this.placeholder = placeholder;
        setOpaque(false);

        field.setColumns(columns);
        field.setFont(Theme.BASE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));

        debounce = new Timer(180, event -> onChange.run());
        debounce.setRepeats(false);
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                debounce.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                debounce.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                debounce.restart();
            }
        });
        field.registerKeyboardAction(event -> clear(),
                javax.swing.KeyStroke.getKeyStroke("ESCAPE"),
                JPanel.WHEN_FOCUSED);

        add(field, BorderLayout.CENTER);
        FlatButton clear = FlatButton.soft("×", event -> clear());
        clear.setPreferredSize(new Dimension(34, 30));
        clear.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        add(clear, BorderLayout.EAST);
    }

    @Override
    public void removeNotify() {
        // A queued search must not run after the window (and its database) has
        // gone: stop the timer as soon as the field leaves the hierarchy.
        debounce.stop();
        super.removeNotify();
    }

    /** The text typed, trimmed; empty when the box is showing its placeholder. */
    public String text() {
        return field.getText().trim();
    }

    /** Sets the text and reports the change immediately. */
    public void setText(String value) {
        field.setText(value);
        debounce.stop();
    }

    public void clear() {
        field.setText("");
    }

    @Override
    public void requestFocus() {
        field.requestFocusInWindow();
    }

    public JTextField field() {
        return field;
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        if (field.getText().isEmpty() && !field.hasFocus()) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(Theme.MUTED);
            g2.setFont(Theme.BASE);
            java.awt.Rectangle bounds = field.getBounds();
            int y = bounds.y + (bounds.height + g2.getFontMetrics().getAscent()) / 2 - 2;
            g2.drawString(placeholder, bounds.x + 10, y);
            g2.dispose();
        }
    }
}
