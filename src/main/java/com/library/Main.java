package com.library;

import com.library.db.LibraryDatabase;
import com.library.db.LibraryException;
import com.library.ui.LibraryFrame;
import com.library.ui.Theme;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.nio.file.Path;

/**
 * Entry point.
 *
 * <pre>
 *   java -cp ... com.library.Main                 open the app on library.db
 *   java -cp ... com.library.Main --demo          seed sample records on first run
 *   java -cp ... com.library.Main --db other.db   use a different database file
 * </pre>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Path dbPath = LibraryDatabase.DEFAULT_PATH;
        boolean demo = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--db" -> {
                    if (i + 1 >= args.length) {
                        System.err.println("--db needs a file path.");
                        System.exit(2);
                    }
                    dbPath = Path.of(args[++i]);
                }
                case "--demo" -> demo = true;
                case "--help", "-h" -> {
                    printUsage();
                    return;
                }
                default -> {
                    System.err.println("Unknown option: " + args[i]);
                    printUsage();
                    System.exit(2);
                }
            }
        }

        if (demo) {
            try (LibraryDatabase db = new LibraryDatabase(dbPath)) {
                if (db.stats().titles() == 0) {
                    SampleData.Counts counts = SampleData.load(db);
                    System.out.println("Seeded " + counts.books() + " books, "
                            + counts.members() + " members and " + counts.loans()
                            + " loans into " + db.location() + ".");
                } else {
                    System.out.println(db.location()
                            + " already holds records; nothing seeded.");
                }
            } catch (LibraryException exc) {
                System.err.println(exc.getMessage());
                System.exit(1);
            }
        }

        Path path = dbPath;
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            try {
                new LibraryFrame(new LibraryDatabase(path)).setVisible(true);
            } catch (LibraryException exc) {
                JOptionPane.showMessageDialog(null, exc.getMessage(),
                        "Could not start", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }

    private static void printUsage() {
        System.out.println("""
                Library Management System

                  --db <path>   database file to open (default: library.db)
                  --demo        seed sample books, members and loans when the
                                database is empty
                  --help        show this message
                """);
    }
}
