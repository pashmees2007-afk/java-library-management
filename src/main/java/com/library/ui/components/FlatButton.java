package com.library.ui.components;

import com.library.ui.Theme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;

/**
 * A flat, painted button, so the look stays the same on every platform and the
 * accent colour actually shows (native look-and-feels ignore button backgrounds).
 */
public class FlatButton extends JButton {

    private static final long serialVersionUID = 1L;

    /** The visual weight of a button. */
    public enum Kind {
        ACCENT, SOFT, DANGER, LINK
    }

    private final Kind kind;

    public FlatButton(String text, Kind kind) {
        super(text);
        this.kind = kind;
        setFocusPainted(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setOpaque(false);
        setFont(kind == Kind.LINK ? Theme.SMALL : Theme.BOLD);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createEmptyBorder(8, kind == Kind.LINK ? 2 : 16, 8,
                kind == Kind.LINK ? 2 : 16));
        setForeground(switch (kind) {
            case ACCENT -> Color.WHITE;
            case DANGER -> Theme.DANGER;
            case LINK -> Theme.PRIMARY;
            case SOFT -> Theme.INK;
        });
    }

    public static FlatButton accent(String text, ActionListener action) {
        return build(text, Kind.ACCENT, action);
    }

    public static FlatButton soft(String text, ActionListener action) {
        return build(text, Kind.SOFT, action);
    }

    public static FlatButton danger(String text, ActionListener action) {
        return build(text, Kind.DANGER, action);
    }

    public static FlatButton link(String text, ActionListener action) {
        return build(text, Kind.LINK, action);
    }

    private static FlatButton build(String text, Kind kind, ActionListener action) {
        FlatButton button = new FlatButton(text, kind);
        if (action != null) {
            button.addActionListener(action);
        }
        return button;
    }

    private Color background() {
        boolean pressed = getModel().isArmed() && getModel().isPressed();
        boolean hover = getModel().isRollover();
        if (!isEnabled()) {
            return switch (kind) {
                case ACCENT -> new Color(0xB9, 0xC7, 0xEE);
                case LINK -> Theme.CARD;
                default -> new Color(0xEC, 0xEF, 0xF4);
            };
        }
        return switch (kind) {
            case ACCENT -> pressed ? new Color(0x1A, 0x43, 0xBD)
                    : hover ? Theme.PRIMARY_DARK : Theme.PRIMARY;
            case DANGER -> pressed ? new Color(0xF6, 0xCF, 0xCF)
                    : hover ? new Color(0xFA, 0xDA, 0xDA) : new Color(0xFD, 0xEA, 0xEA);
            case LINK -> Theme.CARD;
            case SOFT -> pressed ? new Color(0xCD, 0xD5, 0xE0)
                    : hover ? new Color(0xDA, 0xE0, 0xEA) : Theme.SOFT;
        };
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (kind != Kind.LINK) {
            g2.setColor(background());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
        }
        g2.dispose();

        if (!isEnabled()) {
            setForeground(kind == Kind.ACCENT ? new Color(0xEE, 0xF2, 0xFF)
                    : new Color(0x9A, 0xA3, 0xAF));
        } else {
            setForeground(switch (kind) {
                case ACCENT -> Color.WHITE;
                case DANGER -> Theme.DANGER;
                case LINK -> getModel().isRollover() ? Theme.PRIMARY_DARK : Theme.PRIMARY;
                case SOFT -> Theme.INK;
            });
        }
        super.paintComponent(g);
    }
}
