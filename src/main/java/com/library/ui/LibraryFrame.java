package com.library.ui;

import com.library.SampleData;
import com.library.db.LibraryDatabase;
import com.library.model.Stats;
import com.library.ui.components.CardPanel;
import com.library.ui.components.FlatButton;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** The application window: tabs, menu bar, status bar and keyboard shortcuts. */
public class LibraryFrame extends JFrame implements AppContext {

    private static final long serialVersionUID = 1L;

    private static final String[][] SHORTCUTS = {
            {"Ctrl+1 ... Ctrl+5", "Jump to a tab"},
            {"Ctrl+F", "Focus the search box on the current tab"},
            {"Ctrl+N", "Start a new book / member record"},
            {"F5", "Reload every tab from the database"},
            {"Double-click a row", "Edit it, or accept the return at the desk"},
            {"Esc", "Clear the search box"},
            {"Ctrl+Q", "Quit"},
    };

    private final LibraryDatabase db;
    private final Dialogs dialogs;
    private final JTabbedPane tabs = new JTabbedPane();
    private final Map<String, TabPanel> panels = new LinkedHashMap<>();
    private final JLabel status = new JLabel();
    private final JLabel clock = new JLabel();
    private final Timer clockTimer;

    public LibraryFrame(LibraryDatabase db) {
        this(db, new Dialogs.Swing());
    }

    public LibraryFrame(LibraryDatabase db, Dialogs dialogs) {
        this.db = db;
        this.dialogs = dialogs;

        setTitle(db.libraryName() + " - Library Management System");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setSize(1280, 800);
        setMinimumSize(new Dimension(1100, 680));
        setLocationRelativeTo(null);
        getContentPane().setBackground(Theme.BG);

        buildTabs();
        setJMenuBar(buildMenuBar());
        add(buildStatusBar(), BorderLayout.SOUTH);
        installShortcuts();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                quit();
            }
        });

        clockTimer = new Timer(30_000, event -> tickClock());
        clockTimer.start();
        tickClock();
        setStatus("Opened " + db.location() + ". Press F5 to reload, F1 for help.");
    }

    // ------------------------------------------------------------------ layout
    private void buildTabs() {
        panels.put("Dashboard", new DashboardPanel(this));
        panels.put("Catalogue", new BooksPanel(this));
        panels.put("Members", new MembersPanel(this));
        panels.put("Circulation", new CirculationPanel(this));
        panels.put("Settings", new SettingsPanel(this));

        tabs.setFont(Theme.BOLD);
        panels.forEach((name, panel) -> tabs.addTab("  " + name + "  ", panel));
        tabs.addChangeListener(event -> {
            TabPanel current = currentTab();
            if (current != null) {
                current.refresh();
            }
        });

        JPanel holder = new JPanel(new BorderLayout());
        holder.setBackground(Theme.BG);
        holder.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        holder.add(tabs, BorderLayout.CENTER);
        add(holder, BorderLayout.CENTER);
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        file.add(item("Reload from database  (F5)", this::refreshAll));
        file.addSeparator();
        file.add(item("Export catalogue to CSV...", () -> exportFrom("books")));
        file.add(item("Export members to CSV...", () -> exportFrom("members")));
        file.add(item("Export loan history to CSV...", () -> exportFrom("loans")));
        file.addSeparator();
        file.add(item("Back up database...", () -> settings().backup()));
        file.add(item("Load sample data...", this::loadSampleData));
        file.addSeparator();
        file.add(item("Exit  (Ctrl+Q)", this::quit));
        bar.add(file);

        JMenu view = new JMenu("View");
        int index = 1;
        for (String name : panels.keySet()) {
            view.add(item(name + "  (Ctrl+" + index++ + ")", () -> showTab(name)));
        }
        bar.add(view);

        JMenu help = new JMenu("Help");
        help.add(item("Keyboard shortcuts  (F1)", this::showShortcuts));
        help.add(item("About", this::showAbout));
        bar.add(help);
        return bar;
    }

    private JMenuItem item(String label, Runnable action) {
        JMenuItem menuItem = new JMenuItem(label);
        menuItem.addActionListener(event -> action.run());
        return menuItem;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.HEADER);
        bar.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        status.setFont(Theme.SMALL);
        status.setForeground(Theme.MUTED);
        clock.setFont(Theme.SMALL);
        clock.setForeground(Theme.MUTED);
        bar.add(status, BorderLayout.WEST);
        bar.add(clock, BorderLayout.EAST);
        return bar;
    }

    private void tickClock() {
        clock.setText(java.time.LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("EEE dd MMM yyyy  HH:mm",
                        Locale.ENGLISH)));
    }

    private void installShortcuts() {
        JComponent root = getRootPane();
        int index = 1;
        for (String name : panels.keySet()) {
            bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_0 + index,
                    java.awt.event.InputEvent.CTRL_DOWN_MASK), "tab" + index,
                    () -> showTab(name));
            index++;
        }
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "reload", this::refreshAll);
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), "help", this::showShortcuts);
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_F,
                java.awt.event.InputEvent.CTRL_DOWN_MASK), "search",
                () -> currentTab().focusSearch());
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_N,
                java.awt.event.InputEvent.CTRL_DOWN_MASK), "new",
                () -> currentTab().newRecord());
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_Q,
                java.awt.event.InputEvent.CTRL_DOWN_MASK), "quit", this::quit);
    }

    private void bind(JComponent root, KeyStroke keys, String name, Runnable action) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keys, name);
        root.getActionMap().put(name, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }

    // ------------------------------------------------------------- AppContext
    @Override
    public LibraryDatabase db() {
        return db;
    }

    @Override
    public Dialogs dialogs() {
        return dialogs;
    }

    @Override
    public void setStatus(String message) {
        status.setText(message);
    }

    @Override
    public void refreshAll() {
        panels.values().forEach(TabPanel::refresh);
        setTitle(db.libraryName() + " - Library Management System");
    }

    @Override
    public void showTab(String name) {
        TabPanel panel = panels.get(name);
        if (panel != null) {
            tabs.setSelectedComponent(panel);
        }
    }

    public TabPanel currentTab() {
        java.awt.Component selected = tabs.getSelectedComponent();
        return selected instanceof TabPanel panel ? panel : null;
    }

    public TabPanel tab(String name) {
        return panels.get(name);
    }

    public Map<String, TabPanel> tabs() {
        return Map.copyOf(panels);
    }

    public String statusText() {
        return status.getText();
    }

    private SettingsPanel settings() {
        return (SettingsPanel) panels.get("Settings");
    }

    private void exportFrom(String table) {
        showTab("Settings");
        settings().export(table);
    }

    // --------------------------------------------------------------- dialogs
    /** Seeds the demo records, asking first when the database already has data. */
    public void loadSampleData() {
        Stats stats = db.stats();
        if ((stats.titles() > 0 || stats.membersTotal() > 0)
                && !dialogs.confirm(this, "Load sample data",
                "The database already holds records. Add the sample catalogue, "
                        + "members and loans on top of them?")) {
            return;
        }
        SampleData.Counts counts = SampleData.load(db);
        refreshAll();
        setStatus("Loaded " + counts.books() + " books, " + counts.members()
                + " members and " + counts.loans() + " loans of sample data.");
    }

    public void showShortcuts() {
        JDialog dialog = new JDialog(this, "Keyboard shortcuts", true);
        CardPanel card = new CardPanel(new GridBagLayout(), 18, 18);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 10, 0);
        card.add(Theme.section("Keyboard shortcuts"), c);

        c.gridwidth = 1;
        for (String[] row : SHORTCUTS) {
            c.gridy++;
            c.gridx = 0;
            c.insets = new Insets(3, 0, 3, 20);
            JLabel keys = new JLabel(row[0]);
            keys.setFont(Theme.BOLD);
            keys.setForeground(Theme.INK);
            card.add(keys, c);

            c.gridx = 1;
            c.insets = new Insets(3, 0, 3, 0);
            card.add(Theme.body(row[1]), c);
        }

        c.gridy++;
        c.gridx = 0;
        c.gridwidth = 2;
        c.insets = new Insets(16, 0, 0, 0);
        card.add(FlatButton.accent("Close", event -> dialog.dispose()), c);

        JPanel holder = new JPanel(new BorderLayout());
        holder.setBackground(Theme.BG);
        holder.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        holder.add(card, BorderLayout.CENTER);

        dialog.setContentPane(holder);
        dialog.getRootPane().registerKeyboardAction(event -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    public void showAbout() {
        Stats stats = db.stats();
        dialogs.info(this, "About",
                "Library Management System 1.0\n"
                        + "Java Swing front end on a SQLite database.\n\n"
                        + "Database: " + db.location() + "\n"
                        + stats.titles() + " titles · " + stats.copies() + " copies · "
                        + stats.membersTotal() + " members · " + stats.loansActive()
                        + " open loans\n\n"
                        + "Loan period " + db.loanDays() + " days · fine "
                        + Theme.money(db.finePerDay()) + " per day");
    }

    /** Closes the database and the window. */
    public void quit() {
        clockTimer.stop();
        db.close();
        dispose();
    }
}
