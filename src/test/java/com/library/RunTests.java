package com.library;

/** Runs both suites; exits non-zero when anything failed. */
public final class RunTests {

    private RunTests() {
    }

    public static void main(String[] args) {
        System.out.println("Database tests");
        System.out.println("--------------");
        int failures = DatabaseTests.run();

        System.out.println();
        System.out.println("UI tests");
        System.out.println("--------");
        failures += UiTests.run();

        System.out.println();
        if (failures == 0) {
            System.out.println("All tests passed.");
        } else {
            System.out.println(failures + " test(s) failed.");
        }
        System.exit(failures == 0 ? 0 : 1);
    }
}
