package data;

import java.util.Scanner;

/** Line-based numeric input so empty or non-numeric choices show clear errors. */
public final class InputReaders {

    private InputReaders() {
    }

    /**
     * Reads a menu choice after {@link ConsoleUi#menuEnd()} (which prints {@code Choice: } without a newline).
     * Retries on empty input, non-numeric input, or out-of-range values.
     */
    public static int readMenuInt(Scanner scn, int min, int max) {
        while (true) {
            String line = scn.nextLine().trim();
            if (line.isEmpty()) {
                ConsoleUi.warn("Choice cannot be empty. Enter a number from " + min + " to " + max + ".");
                ConsoleUi.choicePrompt();
                continue;
            }
            Integer v = parseIntOrNull(line);
            if (v == null) {
                ConsoleUi.warn("Invalid input. Enter a whole number from " + min + " to " + max + ".");
                ConsoleUi.choicePrompt();
                continue;
            }
            if (v < min || v > max) {
                ConsoleUi.warn("Invalid choice. Enter a number from " + min + " to " + max + ".");
                ConsoleUi.choicePrompt();
                continue;
            }
            return v;
        }
    }

    /**
     * Reads an integer in {@code [min, max]} after a {@link ConsoleUi#fieldPrompt} (or similar) has already been
     * printed. Re-prompts with {@code repromptLabel} on error.
     */
    public static int readIntInRange(Scanner scn, int min, int max, String repromptLabel) {
        while (true) {
            String line = scn.nextLine().trim();
            if (line.isEmpty()) {
                ConsoleUi.warn("Input cannot be empty. Enter a number from " + min + " to " + max + ".");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            Integer v = parseIntOrNull(line);
            if (v == null) {
                ConsoleUi.warn("Invalid input. Enter a whole number from " + min + " to " + max + ".");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            if (v < min || v > max) {
                ConsoleUi.warn("Number out of range. Enter a value from " + min + " to " + max + ".");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            return v;
        }
    }

    public static int readPositiveInt(Scanner scn, String repromptLabel, int min, int max) {
        return readIntInRange(scn, min, max, repromptLabel);
    }

    public static double readNonNegativeDouble(Scanner scn, String repromptLabel, double max) {
        while (true) {
            String line = scn.nextLine().trim();
            if (line.isEmpty()) {
                ConsoleUi.warn("Input cannot be empty. Enter a dollar amount (for example 1.50).");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            double v;
            try {
                v = Double.parseDouble(line);
            } catch (NumberFormatException e) {
                ConsoleUi.warn("Invalid number. Use digits and at most one decimal point.");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            if (v < 0) {
                ConsoleUi.warn("Amount cannot be negative.");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            if (v > max) {
                ConsoleUi.warn("Amount is too large (maximum " + max + ").");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            return v;
        }
    }

    /**
     * Like {@link #readIntInRange} but entering {@code 0} alone cancels (returns {@code null}). Use only when the
     * valid range does not include 0 as real data (e.g. loan days 1–365).
     */
    public static Integer readIntInRangeOrZeroCancel(Scanner scn, int min, int max, String repromptLabel) {
        while (true) {
            String line = scn.nextLine().trim();
            if (line.isEmpty()) {
                ConsoleUi.warn("Input cannot be empty. Enter a number from " + min + " to " + max + ", or 0 to cancel.");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            if ("0".equals(line)) {
                return null;
            }
            Integer v = parseIntOrNull(line);
            if (v == null) {
                ConsoleUi.warn("Invalid input. Enter a whole number from " + min + " to " + max + ", or 0 to cancel.");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            if (v < min || v > max) {
                ConsoleUi.warn("Number out of range. Enter a value from " + min + " to " + max + ", or 0 to cancel.");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            return v;
        }
    }

    /**
     * Like {@link #readNonNegativeDouble} but entering {@code cancel} (any case) aborts (returns {@code null}).
     * {@code 0} is still a valid amount.
     */
    public static Double readNonNegativeDoubleOrCancel(Scanner scn, String repromptLabel, double max) {
        while (true) {
            String line = scn.nextLine().trim();
            if (line.isEmpty()) {
                ConsoleUi.warn("Input cannot be empty. Enter an amount, or type cancel to abort.");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            if ("cancel".equalsIgnoreCase(line)) {
                return null;
            }
            double v;
            try {
                v = Double.parseDouble(line);
            } catch (NumberFormatException e) {
                ConsoleUi.warn("Invalid number. Use digits and at most one decimal point, or type cancel to abort.");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            if (v < 0) {
                ConsoleUi.warn("Amount cannot be negative.");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            if (v > max) {
                ConsoleUi.warn("Amount is too large (maximum " + max + ").");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            return v;
        }
    }

    private static Integer parseIntOrNull(String line) {
        try {
            return Integer.parseInt(line);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
