package data;

import java.util.Scanner;

public final class InputReaders {

    private InputReaders() {
    }

    
    // read menu input with user-friendly validation
    public static int readMenuInt(Scanner scn, int min, int max) {
        while (true) {
            String line = scn.nextLine().trim();
            if (line.isEmpty()) {
                ConsoleUi.warn("Input cannot be empty. Please enter a number from " + min + " to " + max + ".");
                ConsoleUi.choicePrompt();
                continue;
            }
            Integer v = parseIntOrNull(line);
            if (v == null) {
                ConsoleUi.warn("Please enter a whole number from " + min + " to " + max + ".");
                ConsoleUi.choicePrompt();
                continue;
            }
            if (v < min || v > max) {
                ConsoleUi.warn("Out of range. Enter a number from " + min + " to " + max + ".");
                ConsoleUi.choicePrompt();
                continue;
            }
            return v;
        }
    }

    // reads until user enters an integer between min and max
    public static int readIntInRange(Scanner scn, int min, int max, String repromptLabel) {
        while (true) {
            String line = scn.nextLine().trim();
            if (line.isEmpty()) {
                ConsoleUi.warn("Input cannot be empty. Enter a whole number from " + min + " to " + max + ".");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            Integer v = parseIntOrNull(line);
            if (v == null) {
                ConsoleUi.warn("Please enter digits only (whole number) from " + min + " to " + max + ".");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            if (v < min || v > max) {
                ConsoleUi.warn("Out of range. Enter a number from " + min + " to " + max + ".");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            return v;
        }
    }

    // reads non-negative decimal amount, optionally allowing "cancel"
    private static Double readNonNegativeDoubleCore(Scanner scn, String repromptLabel, double max, boolean allowCancel) {
        while (true) {
            String line = scn.nextLine().trim();
            if (line.isEmpty()) {
                ConsoleUi.warn(allowCancel
                        ? "Input cannot be empty. Enter an amount, or type cancel."
                        : "Input cannot be empty. Enter an amount (example: 2.50).");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            if (allowCancel && "cancel".equalsIgnoreCase(line)) {
                return null;
            }
            double v;
            try {
                v = Double.parseDouble(line);
            } catch (NumberFormatException e) {
                ConsoleUi.warn(allowCancel
                        ? "Invalid amount. Use digits (e.g. 2.50), or type cancel."
                        : "Invalid amount. Use digits only (example: 2.50).");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            if (v < 0) {
                ConsoleUi.warn(allowCancel
                        ? "Amount cannot be negative. Enter 0 or more."
                        : "Amount cannot be negative. Enter 0 or more.");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            if (v > max) {
                ConsoleUi.warn(allowCancel
                        ? "Amount is too high. Enter up to " + max + ", or type cancel."
                        : "That amount is too high. The maximum allowed here is " + max + ".");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            return v;
        }
    }

    // reads non-negative decimal value
    public static double readNonNegativeDouble(Scanner scn, String repromptLabel, double max) {
        return readNonNegativeDoubleCore(scn, repromptLabel, max, false);
    }

    // reads integer in range, with 0 to cancel (returns null)
    public static Integer readIntInRangeOrZeroCancel(Scanner scn, int min, int max, String repromptLabel) {
        while (true) {
            String line = scn.nextLine().trim();
            if (line.isEmpty()) {
                ConsoleUi.warn("Input cannot be empty. Enter " + min + " to " + max + ", or 0 to cancel.");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            if ("0".equals(line)) {
                return null;
            }
            Integer v = parseIntOrNull(line);
            if (v == null) {
                ConsoleUi.warn("Please enter a whole number from " + min + " to " + max + ", or 0 to cancel.");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            if (v < min || v > max) {
                ConsoleUi.warn("Out of range. Enter " + min + " to " + max + ", or 0 to cancel.");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            return v;
        }
    }

    //same thing but user may type cancel to abort (returns null)
    public static Double readNonNegativeDoubleOrCancel(Scanner scn, String repromptLabel, double max) {
        return readNonNegativeDoubleCore(scn, repromptLabel, max, true);
    }

    private static Integer parseIntOrNull(String line) {
        try {
            return Integer.parseInt(line);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
