package com.library;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A very small test runner, so the project needs no test-framework JAR: each
 * test is a named block, failures are collected and reported at the end.
 */
public class TestHarness {

    /** A test body that is allowed to throw. */
    @FunctionalInterface
    public interface Body {
        void run() throws Exception;
    }

    private final String suite;
    private final List<String> failures = new ArrayList<>();
    private int run;
    private boolean verbose = true;

    public TestHarness(String suite) {
        this.suite = suite;
    }

    public TestHarness quiet() {
        this.verbose = false;
        return this;
    }

    /** Runs one test, recording a failure rather than stopping the suite. */
    public void test(String name, Body body) {
        run++;
        try {
            body.run();
            if (verbose) {
                System.out.println("  ok    " + name);
            }
        } catch (AssertionError | Exception failure) {
            String message = failure.getMessage() == null
                    ? failure.getClass().getSimpleName() : failure.getMessage();
            failures.add(name + "\n          " + message);
            System.out.println("  FAIL  " + name + "\n          " + message);
        }
    }

    /** Prints the summary; returns the number of failures. */
    public int report() {
        System.out.println();
        System.out.println(suite + ": " + (run - failures.size()) + "/" + run + " passed");
        if (!failures.isEmpty()) {
            System.out.println("Failures:");
            for (String failure : failures) {
                System.out.println("  - " + failure);
            }
        }
        return failures.size();
    }

    public int count() {
        return run;
    }

    // ------------------------------------------------------------- assertions
    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " — expected <" + expected
                    + "> but was <" + actual + ">");
        }
    }

    public static void assertEquals(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.001) {
            throw new AssertionError(message + " — expected <" + expected
                    + "> but was <" + actual + ">");
        }
    }

    public static void assertNull(Object value, String message) {
        if (value != null) {
            throw new AssertionError(message + " — expected null but was <" + value + ">");
        }
    }

    public static void assertNotNull(Object value, String message) {
        if (value == null) {
            throw new AssertionError(message + " — expected a value but was null");
        }
    }

    public static void assertContains(String haystack, String needle, String message) {
        if (haystack == null || !haystack.toLowerCase().contains(needle.toLowerCase())) {
            throw new AssertionError(message + " — <" + haystack + "> does not contain <"
                    + needle + ">");
        }
    }

    /**
     * Asserts that the body throws, and that the message mentions {@code contains}.
     * Returns the message so a test can check it further.
     */
    public static String assertThrows(Body body, String contains, String message) {
        try {
            body.run();
        } catch (Exception | AssertionError thrown) {
            String text = String.valueOf(thrown.getMessage());
            if (contains != null && !text.toLowerCase().contains(contains.toLowerCase())) {
                throw new AssertionError(message + " — threw, but the message <" + text
                        + "> does not mention <" + contains + ">");
            }
            return text;
        }
        throw new AssertionError(message + " — nothing was thrown");
    }
}
