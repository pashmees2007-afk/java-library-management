package com.library.ui;

import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Color;
import java.awt.Font;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import java.util.Locale;

/** One place for the colours, fonts and small formatting helpers the UI shares. */
public final class Theme {

    private Theme() {
    }

    // ------------------------------------------------------------------ palette
    public static final Color BG = new Color(0xEE, 0xF1, 0xF5);
    public static final Color CARD = Color.WHITE;
    public static final Color INK = new Color(0x1F, 0x29, 0x37);
    public static final Color MUTED = new Color(0x6B, 0x72, 0x80);
    public static final Color PRIMARY = new Color(0x25, 0x63, 0xEB);
    public static final Color PRIMARY_DARK = new Color(0x1D, 0x4E, 0xD8);
    public static final Color OK = new Color(0x04, 0x78, 0x57);
    public static final Color WARN = new Color(0xB4, 0x53, 0x09);
    public static final Color DANGER = new Color(0xB9, 0x1C, 0x1C);
    public static final Color BORDER = new Color(0xD7, 0xDC, 0xE3);
    public static final Color STRIPE = new Color(0xF7, 0xF9, 0xFC);
    public static final Color SELECTION = new Color(0xDB, 0xE6, 0xFF);
    public static final Color HEADER = new Color(0xE3, 0xE8, 0xF0);
    public static final Color SOFT = new Color(0xE5, 0xE9, 0xF0);

    // -------------------------------------------------------------------- fonts
    private static final String FAMILY = "Segoe UI";
    public static final Font BASE = new Font(FAMILY, Font.PLAIN, 13);
    public static final Font BOLD = new Font(FAMILY, Font.BOLD, 13);
    public static final Font SMALL = new Font(FAMILY, Font.PLAIN, 12);
    public static final Font SMALL_BOLD = new Font(FAMILY, Font.BOLD, 12);
    public static final Font SECTION = new Font(FAMILY, Font.BOLD, 15);
    public static final Font TITLE = new Font(FAMILY, Font.BOLD, 22);
    public static final Font STAT = new Font(FAMILY, Font.BOLD, 26);

    /** Applies the system look and feel and the shared font/colour defaults. */
    public static void install() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception exc) {
            // Keep the default look and feel; the app still works.
        }

        Enumeration<Object> keys = UIManager.getLookAndFeelDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            if (UIManager.get(key) instanceof FontUIResource) {
                UIManager.put(key, new FontUIResource(BASE));
            }
        }

        UIManager.put("Panel.background", BG);
        UIManager.put("OptionPane.background", BG);
        UIManager.put("OptionPane.messageFont", BASE);
        UIManager.put("Table.font", BASE);
        UIManager.put("Table.foreground", INK);
        UIManager.put("Table.background", CARD);
        UIManager.put("Table.selectionBackground", SELECTION);
        UIManager.put("Table.selectionForeground", INK);
        UIManager.put("Table.gridColor", new Color(0xEC, 0xEF, 0xF3));
        UIManager.put("TableHeader.font", SMALL_BOLD);
        UIManager.put("TableHeader.background", HEADER);
        UIManager.put("TableHeader.foreground", INK);
        UIManager.put("List.font", BASE);
        UIManager.put("List.selectionBackground", SELECTION);
        UIManager.put("List.selectionForeground", INK);
        UIManager.put("TabbedPane.font", BOLD);
        UIManager.put("TextField.font", BASE);
        UIManager.put("TextArea.font", BASE);
        UIManager.put("ComboBox.font", BASE);
        UIManager.put("ToolTip.font", SMALL);
    }

    // --------------------------------------------------------------- formatting
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    public static String money(double value) {
        return MONEY.format(value);
    }

    public static String date(LocalDate value) {
        return value == null ? "-" : value.format(DATE);
    }

    public static String plural(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }

    /** Text that is empty or null shown as a dash, so columns never look broken. */
    public static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    // ------------------------------------------------------------------- labels
    public static JLabel title(String text) {
        return label(text, TITLE, INK);
    }

    public static JLabel section(String text) {
        return label(text, SECTION, INK);
    }

    public static JLabel body(String text) {
        return label(text, BASE, INK);
    }

    public static JLabel muted(String text) {
        return label(text, SMALL, MUTED);
    }

    private static JLabel label(String text, Font font, Color colour) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(colour);
        label.setOpaque(false);
        return label;
    }

    /** Wraps text in HTML so a label can run onto several lines at a fixed width. */
    public static String wrap(String text, int widthPx) {
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\n", "<br>");
        return "<html><div style='width:" + widthPx + "px'>" + escaped + "</div></html>";
    }
}
