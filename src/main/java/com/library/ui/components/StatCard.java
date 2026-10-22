package com.library.ui.components;

import com.library.ui.Theme;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Component;

/** A headline number with a caption and a line of detail, used on the dashboard. */
public class StatCard extends CardPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel value = new JLabel("0");
    private final JLabel hint = new JLabel(" ");

    public StatCard(String caption, Color tone) {
        super(new java.awt.BorderLayout(), 16, 14);

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));

        value.setFont(Theme.STAT);
        value.setForeground(tone);

        JLabel captionLabel = new JLabel(caption.toUpperCase(java.util.Locale.ENGLISH));
        captionLabel.setFont(Theme.SMALL);
        captionLabel.setForeground(Theme.MUTED);

        hint.setFont(Theme.SMALL);
        hint.setForeground(Theme.MUTED);
        hint.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        for (JLabel label : new JLabel[]{value, captionLabel, hint}) {
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            stack.add(label);
        }
        add(stack, java.awt.BorderLayout.CENTER);
    }

    public void set(String headline, String detail) {
        value.setText(headline);
        hint.setText(detail == null || detail.isBlank() ? " " : detail);
    }

    /** The headline text, so tests can read what is on screen. */
    public String headline() {
        return value.getText();
    }
}
