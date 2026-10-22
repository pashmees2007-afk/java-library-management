package com.library.db;

/**
 * A rule violation or invalid input that the user should see as a plain
 * message, e.g. "No copies are on the shelf". Unchecked so the data layer stays
 * readable; the UI catches it at each action boundary and shows the text.
 */
public class LibraryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LibraryException(String message) {
        super(message);
    }

    public LibraryException(String message, Throwable cause) {
        super(message, cause);
    }
}
