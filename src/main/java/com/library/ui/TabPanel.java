package com.library.ui;

import com.library.db.LibraryDatabase;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/** Shared plumbing for the five tabs. */
public abstract class TabPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    protected final AppContext app;
    protected final LibraryDatabase db;

    protected TabPanel(AppContext app) {
        super(new BorderLayout(0, 12));
        this.app = app;
        this.db = app.db();
        setOpaque(true);
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    }

    /** Re-reads everything this tab shows from the database. */
    public abstract void refresh();

    /** Puts the caret in this tab's search box, if it has one. */
    public void focusSearch() {
        // Tabs without a search box do nothing.
    }

    /** Starts a blank record, if this tab has a form. */
    public void newRecord() {
        // Tabs without a form do nothing.
    }

    /** Shows the message from a rule violation without losing the user's input. */
    protected void reportProblem(String title, Exception exc) {
        app.dialogs().warn(this, title, exc.getMessage());
    }
}
