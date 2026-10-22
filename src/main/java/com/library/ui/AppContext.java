package com.library.ui;

import com.library.db.LibraryDatabase;

/** What a tab needs from the window around it. */
public interface AppContext {

    LibraryDatabase db();

    Dialogs dialogs();

    /** Shows a one-line message in the status bar. */
    void setStatus(String message);

    /** Reloads every tab after a change that others may care about. */
    void refreshAll();

    /** Brings a tab to the front by name, e.g. "Circulation". */
    void showTab(String name);
}
