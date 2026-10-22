package com.library.ui.components;

import com.library.ui.Theme;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

/** A white panel with a hairline border and rounded corners. */
public class CardPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final int RADIUS = 10;

    public CardPanel() {
        this(new java.awt.BorderLayout(), 14, 14);
    }

    public CardPanel(LayoutManager layout) {
        this(layout, 14, 14);
    }

    public CardPanel(LayoutManager layout, int padX, int padY) {
        super(layout);
        setOpaque(false);
        setBackground(Theme.CARD);
        setBorder(BorderFactory.createEmptyBorder(padY, padX, padY, padX));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(getBackground());
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
        g2.setColor(Theme.BORDER);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
        g2.dispose();
        super.paintComponent(g);
    }
}
