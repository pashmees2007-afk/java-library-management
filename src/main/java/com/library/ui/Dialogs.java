package com.library.ui;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.nio.file.Path;

/**
 * The modal conversations the panels need. Behind an interface so the tests can
 * answer them without a window blocking the suite.
 */
public interface Dialogs {

    void warn(Component parent, String title, String message);

    void info(Component parent, String title, String message);

    boolean confirm(Component parent, String title, String message);

    /** Asks where to write a file; {@code null} when the user cancels. */
    Path chooseSaveFile(Component parent, String title, String suggestedName,
                        String description, String extension);

    /** The real dialogs, used when the application is running. */
    class Swing implements Dialogs {

        @Override
        public void warn(Component parent, String title, String message) {
            JOptionPane.showMessageDialog(parent, message, title,
                    JOptionPane.WARNING_MESSAGE);
        }

        @Override
        public void info(Component parent, String title, String message) {
            JOptionPane.showMessageDialog(parent, message, title,
                    JOptionPane.INFORMATION_MESSAGE);
        }

        @Override
        public boolean confirm(Component parent, String title, String message) {
            return JOptionPane.showConfirmDialog(parent, message, title,
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE)
                    == JOptionPane.YES_OPTION;
        }

        @Override
        public Path chooseSaveFile(Component parent, String title, String suggestedName,
                                   String description, String extension) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(title);
            chooser.setSelectedFile(new java.io.File(suggestedName));
            chooser.setFileFilter(new FileNameExtensionFilter(description, extension));
            if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
                return null;
            }
            Path chosen = chooser.getSelectedFile().toPath();
            if (!chosen.getFileName().toString().toLowerCase().endsWith("." + extension)) {
                chosen = chosen.resolveSibling(chosen.getFileName() + "." + extension);
            }
            return chosen;
        }
    }
}
