package com.library.ui.components;

import com.library.model.DailyCount;
import com.library.ui.Theme;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** A small bar chart of books issued per day. */
public class BarChart extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

    private List<DailyCount> series = new ArrayList<>();

    public BarChart() {
        setOpaque(false);
        setBackground(Theme.CARD);
    }

    public void setSeries(List<DailyCount> series) {
        this.series = new ArrayList<>(series);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(Theme.SMALL);

        int width = getWidth();
        int height = getHeight();
        int padLeft = 30;
        int padRight = 8;
        int padTop = 14;
        int padBottom = 24;
        int plotWidth = width - padLeft - padRight;
        int plotHeight = height - padTop - padBottom;
        if (plotWidth < 40 || plotHeight < 30 || series.isEmpty()) {
            g2.dispose();
            return;
        }

        int peak = 0;
        for (DailyCount point : series) {
            peak = Math.max(peak, point.issued());
        }
        int scale = Math.max(peak, 1);
        FontMetrics metrics = g2.getFontMetrics();

        // Only label the midpoint when it reads as a different number from the ends.
        double[] fractions = scale >= 2 ? new double[]{0, 0.5, 1} : new double[]{0, 1};
        for (double fraction : fractions) {
            int y = (int) (padTop + plotHeight * (1 - fraction));
            g2.setColor(Theme.BORDER);
            g2.drawLine(padLeft, y, width - padRight, y);
            String text = String.valueOf(Math.round(scale * fraction));
            g2.setColor(Theme.MUTED);
            g2.drawString(text, padLeft - 6 - metrics.stringWidth(text),
                    y + metrics.getAscent() / 2 - 1);
        }

        double slot = plotWidth / (double) series.size();
        int barWidth = (int) Math.max(6, slot * 0.6);
        for (int i = 0; i < series.size(); i++) {
            DailyCount point = series.get(i);
            int centre = (int) (padLeft + slot * (i + 0.5));
            int barHeight = (int) (plotHeight * (point.issued() / (double) scale));
            int top = padTop + plotHeight - barHeight;

            g2.setColor(point.issued() > 0 ? Theme.PRIMARY : new Color(0xE8, 0xEC, 0xF2));
            g2.fillRoundRect(centre - barWidth / 2,
                    point.issued() > 0 ? top : padTop + plotHeight - 2,
                    barWidth, Math.max(2, barHeight), 4, 4);

            if (point.issued() > 0) {
                String value = String.valueOf(point.issued());
                g2.setColor(Theme.MUTED);
                g2.drawString(value, centre - metrics.stringWidth(value) / 2, top - 4);
            }
            // Thin out the day labels so the last two never collide.
            boolean nearEnd = i >= series.size() - 2;
            if ((i % 3 == 0 && !nearEnd) || i == series.size() - 1) {
                String label = point.day().format(DAY);
                g2.setColor(Theme.MUTED);
                g2.drawString(label, centre - metrics.stringWidth(label) / 2,
                        height - padBottom + metrics.getAscent() + 2);
            }
        }

        if (peak == 0) {
            String message = "No books issued in this period";
            g2.setColor(Theme.MUTED);
            g2.drawString(message, (width - metrics.stringWidth(message)) / 2, height / 2);
        }
        g2.dispose();
    }
}
