package com.library.ui;

import com.library.db.LoanFilter;
import com.library.model.DailyCount;
import com.library.model.LoanView;
import com.library.model.PopularBook;
import com.library.model.Stats;
import com.library.ui.components.BarChart;
import com.library.ui.components.CardPanel;
import com.library.ui.components.DataTable;
import com.library.ui.components.FlatButton;
import com.library.ui.components.StatCard;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Dashboard tab: headline numbers, the overdue queue and borrowing trends. */
public class DashboardPanel extends TabPanel {

    private static final long serialVersionUID = 1L;
    private static final int CHART_DAYS = 14;

    private final JLabel heading = Theme.title("Library overview");
    private final JLabel stamp = Theme.muted("");
    private final Map<String, StatCard> cards = new HashMap<>();
    private final JLabel overdueNote = Theme.muted("");
    private final DataTable<LoanView> overdueTable;
    private final JPanel popularBody = new JPanel();
    private final BarChart chart = new BarChart();

    public DashboardPanel(AppContext app) {
        super(app);

        overdueTable = new DataTable<>(List.of(
                DataTable.Column.text("Book", 180, LoanView::bookTitle),
                DataTable.Column.text("Member", 140, LoanView::memberDisplay),
                DataTable.Column.text("Contact", 115, LoanView::memberContact),
                DataTable.Column.centre("Due", 95, v -> Theme.date(v.dueDate())),
                DataTable.Column.centre("Days late", 75,
                        v -> String.valueOf(v.daysLate())),
                DataTable.Column.right("Fine", 70, v -> Theme.money(v.accruedFine()))),
                LoanView::id, 10);
        overdueTable.setRowStyle(view -> DataTable.RowStyle.OVERDUE);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        refresh();
    }

    // ------------------------------------------------------------------ layout
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(heading, BorderLayout.WEST);
        JPanel right = new JPanel(new BorderLayout());
        right.setOpaque(false);
        right.add(stamp, BorderLayout.SOUTH);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JPanel buildBody() {
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(buildCards(), BorderLayout.NORTH);

        JPanel split = new JPanel(new BorderLayout(12, 0));
        split.setOpaque(false);
        split.add(buildOverdue(), BorderLayout.CENTER);
        split.add(buildSideColumn(), BorderLayout.EAST);
        body.add(split, BorderLayout.CENTER);
        return body;
    }

    private JPanel buildCards() {
        JPanel row = new JPanel(new GridLayout(1, 5, 10, 0));
        row.setOpaque(false);
        row.setPreferredSize(new Dimension(100, 104));
        cards.put("titles", new StatCard("Titles in catalogue", Theme.INK));
        cards.put("members", new StatCard("Members", Theme.OK));
        cards.put("onLoan", new StatCard("Copies on loan", Theme.INK));
        cards.put("overdue", new StatCard("Overdue", Theme.DANGER));
        cards.put("dues", new StatCard("Fines outstanding", Theme.WARN));
        for (String key : new String[]{"titles", "members", "onLoan", "overdue", "dues"}) {
            row.add(cards.get(key));
        }
        return row;
    }

    private JPanel buildOverdue() {
        CardPanel card = new CardPanel(new BorderLayout(0, 8), 12, 12);

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Theme.section("Needs attention"), BorderLayout.WEST);
        head.add(FlatButton.link("Go to circulation desk →",
                event -> app.showTab("Circulation")), BorderLayout.EAST);

        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setOpaque(false);
        top.add(head, BorderLayout.NORTH);
        top.add(overdueNote, BorderLayout.SOUTH);

        card.add(top, BorderLayout.NORTH);
        card.add(overdueTable, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildSideColumn() {
        JPanel column = new JPanel(new GridLayout(2, 1, 0, 12));
        column.setOpaque(false);
        column.setPreferredSize(new Dimension(430, 100));

        CardPanel popular = new CardPanel(new BorderLayout(0, 8), 12, 12);
        popular.add(Theme.section("Most borrowed"), BorderLayout.NORTH);
        popularBody.setOpaque(false);
        popularBody.setLayout(new BoxLayout(popularBody, BoxLayout.Y_AXIS));
        popular.add(popularBody, BorderLayout.CENTER);

        CardPanel chartCard = new CardPanel(new BorderLayout(0, 8), 12, 12);
        chartCard.add(Theme.section("Books issued · last " + CHART_DAYS + " days"),
                BorderLayout.NORTH);
        chartCard.add(chart, BorderLayout.CENTER);

        column.add(popular);
        column.add(chartCard);
        return column;
    }

    // -------------------------------------------------------------------- data
    @Override
    public void refresh() {
        Stats stats = db.stats();
        heading.setText(db.libraryName());
        stamp.setText(Theme.date(db.today()) + " · " + stats.issuedToday() + " issued today");

        cards.get("titles").set(String.valueOf(stats.titles()),
                stats.copies() + " copies, " + stats.available() + " on shelf");
        cards.get("members").set(String.valueOf(stats.membersActive()),
                stats.membersTotal() + " registered");
        cards.get("onLoan").set(String.valueOf(stats.copiesOnLoan()),
                stats.loansActive() + " open loans");
        cards.get("overdue").set(String.valueOf(stats.loansOverdue()),
                stats.loansOverdue() == 0 ? "all loans on time" : "needs follow-up");
        cards.get("dues").set(Theme.money(stats.duesOutstanding()), "unpaid + accruing");

        fillOverdue();
        fillPopular();
        chart.setSeries(series());
    }

    private void fillOverdue() {
        List<LoanView> overdue = db.listLoans(LoanFilter.OVERDUE, "");
        overdueTable.setRows(overdue);

        if (overdue.isEmpty()) {
            long dueSoon = db.listLoans(LoanFilter.ON_LOAN, "").stream()
                    .filter(v -> !v.dueDate().isAfter(db.today().plusDays(3)))
                    .count();
            overdueNote.setText(dueSoon > 0
                    ? "Nothing overdue. " + dueSoon + " loan(s) due within 3 days."
                    : "Nothing overdue. All clear.");
        } else {
            double total = overdue.stream().mapToDouble(LoanView::accruedFine).sum();
            overdueNote.setText(overdue.size() + " overdue loan(s) · "
                    + Theme.money(total) + " in fines accrued");
        }
    }

    private void fillPopular() {
        popularBody.removeAll();
        List<PopularBook> rows = db.popularBooks(7);
        if (rows.isEmpty()) {
            JLabel empty = Theme.muted("No loans recorded yet.");
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            popularBody.add(empty);
        } else {
            int peak = rows.stream().mapToInt(PopularBook::timesIssued).max().orElse(1);
            for (PopularBook row : rows) {
                popularBody.add(popularRow(row, peak));
            }
        }
        popularBody.revalidate();
        popularBody.repaint();
    }

    private JPanel popularRow(PopularBook row, int peak) {
        JPanel line = new JPanel(new BorderLayout(8, 0));
        line.setOpaque(false);
        line.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        JLabel title = new JLabel(row.title());
        title.setFont(Theme.SMALL);
        title.setForeground(Theme.INK);

        JPanel meter = new Meter(row.timesIssued() / (double) peak);
        meter.setPreferredSize(new Dimension(90, 7));

        JLabel count = new JLabel(row.timesIssued() + "x");
        count.setFont(Theme.SMALL);
        count.setForeground(Theme.MUTED);

        JPanel right = new JPanel(new BorderLayout(8, 0));
        right.setOpaque(false);
        right.add(meter, BorderLayout.CENTER);
        right.add(count, BorderLayout.EAST);
        right.setPreferredSize(new Dimension(130, 20));

        line.add(title, BorderLayout.CENTER);
        line.add(right, BorderLayout.EAST);
        return line;
    }

    /**
     * A thin share-of-the-maximum bar. Painted here because the Windows
     * look-and-feel ignores the colours set on a JProgressBar.
     */
    private static class Meter extends JPanel {
        private static final long serialVersionUID = 1L;
        private final double fraction;

        Meter(double fraction) {
            this.fraction = Math.max(0, Math.min(1, fraction));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            int height = Math.min(getHeight(), 7);
            int y = (getHeight() - height) / 2;
            g2.setColor(Theme.HEADER);
            g2.fillRoundRect(0, y, getWidth(), height, height, height);
            g2.setColor(Theme.PRIMARY);
            g2.fillRoundRect(0, y, (int) (getWidth() * fraction), height, height, height);
            g2.dispose();
        }
    }

    /** One entry per day for the chart, including the days with no activity. */
    private List<DailyCount> series() {
        Map<LocalDate, Integer> counts = new HashMap<>();
        for (DailyCount point : db.activity(CHART_DAYS)) {
            counts.put(point.day(), point.issued());
        }
        List<DailyCount> series = new ArrayList<>();
        for (int offset = CHART_DAYS - 1; offset >= 0; offset--) {
            LocalDate day = db.today().minusDays(offset);
            series.add(new DailyCount(day, counts.getOrDefault(day, 0)));
        }
        return series;
    }

    // ------------------------------------------------------------ for the tests
    public String cardValue(String key) {
        return cards.get(key).headline();
    }

    public DataTable<LoanView> overdueTable() {
        return overdueTable;
    }

    public String headingText() {
        return heading.getText();
    }
}
