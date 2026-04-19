package data;

import java.util.Scanner;

/** Line-based numeric input so empty or non-numeric choices show clear errors. */
public final class InputReaders {

    private InputReaders() {
    }

    
    //read menu input. if input wrong thing will prompt respective input error msg
    public static int readMenuInt(Scanner scn, int min, int max) {
        while (true) {
            String line = scn.nextLine().trim();
            if (line.isEmpty()) {
                ConsoleUi.warn("You pressed Enter without a number. Type an option from " + min + " to " + max + ".");
                ConsoleUi.choicePrompt();
                continue;
            }
            Integer v = parseIntOrNull(line);
            if (v == null) {
                ConsoleUi.warn("That is not a whole number. Choose one of the options listed (" + min + "–" + max + ").");
                ConsoleUi.choicePrompt();
                continue;
            }
            if (v < min || v > max) {
                ConsoleUi.warn("That number is not on this menu. Valid choices are " + min + " through " + max + ".");
                ConsoleUi.choicePrompt();
                continue;
            }
            return v;
        }
    }

        //keep on reading the input from user from the console unitl it matches between the min and max value and reutn elese retunr error msg.
    public static int readIntInRange(Scanner scn, int min, int max, String repromptLabel) {
        while (true) {
            String line = scn.nextLine().trim();
            if (line.isEmpty()) {
                ConsoleUi.warn("That line was empty. Enter a whole number between " + min + " and " + max + ".");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            Integer v = parseIntOrNull(line);
            if (v == null) {
                ConsoleUi.warn("That is not a valid whole number. Use digits only, in the range " + min + "–" + max + ".");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            if (v < min || v > max) {
                ConsoleUi.warn("That value is out of range. It must be between " + min + " and " + max + " (inclusive).");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            return v;
        }
    }

    /** @param allowCancel if true, line {@code cancel} (any case) returns {@code null} */
    private static Double readNonNegativeDoubleCore(Scanner scn, String repromptLabel, double max, boolean allowCancel) {
        while (true) {
            String line = scn.nextLine().trim();
            if (line.isEmpty()) {
                ConsoleUi.warn(allowCancel
                        ? "Enter a dollar amount, or type cancel to abort this step."
                        : "Enter a dollar amount using digits, for example 1.50 or 10.");
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
                        ? "That is not a valid amount. Use digits (and one decimal point if needed), or type cancel to abort."
                        : "That is not a valid amount. Use digits and at most one decimal point (example: 2.50).");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            if (v < 0) {
                ConsoleUi.warn(allowCancel
                        ? "The amount cannot be negative. Enter a positive value or zero."
                        : "The amount cannot be negative. Enter zero or a positive number.");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            if (v > max) {
                ConsoleUi.warn(allowCancel
                        ? "That amount is too high. Enter at most " + max + ", or type cancel to abort."
                        : "That amount is too high. The maximum allowed here is " + max + ".");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            return v;
        }
    }

    //to read the positive double/float value 有小数点的. then error msg if wrong input
    public static double readNonNegativeDouble(Scanner scn, String repromptLabel, double max) {
        return readNonNegativeDoubleCore(scn, repromptLabel, max, false);
    }

    //this is for when user input half way they can enter 0 to esc or type "cancel" to exit de and also to check whther their input is in range or not
    public static Integer readIntInRangeOrZeroCancel(Scanner scn, int min, int max, String repromptLabel) {
        while (true) {
            String line = scn.nextLine().trim();
            if (line.isEmpty()) {
                ConsoleUi.warn("Enter a number from " + min + " to " + max + ", or type 0 to cancel this step.");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            if ("0".equals(line)) {
                return null;
            }
            Integer v = parseIntOrNull(line);
            if (v == null) {
                ConsoleUi.warn("That is not a whole number. Enter a value from " + min + " to " + max + ", or 0 to cancel.");
                ConsoleUi.fieldPrompt(repromptLabel);
                continue;
            }
            if (v < min || v > max) {
                ConsoleUi.warn("That number is outside the allowed range (" + min + "–" + max + "). Try again, or enter 0 to cancel.");
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
