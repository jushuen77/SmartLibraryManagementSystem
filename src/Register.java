import data.Database;
import java.util.Scanner;
import data.ConsoleUi;

/*
 * Sign-up flow: sanity-check passwords + email, make sure username/email not taken, then Database.addUser.
 * Passwords are stored as plain text here for demonstration only; production code should hash passwords.
 */
public final class Register {

    private Register() {
    }

    private static void pressEnterToContinue(Scanner scn) {
        ConsoleUi.pressEnterHint();
        scn.nextLine();
    }

    public static void prompt(Scanner scn) {
        ConsoleUi.sectionTitle("Create account");
        ConsoleUi.fieldPrompt("Name");
        String name = scn.nextLine();
        ConsoleUi.fieldPrompt("Email");
        String email = scn.nextLine();
        ConsoleUi.fieldPrompt("Password");
        String password = scn.nextLine();
        ConsoleUi.fieldPrompt("Confirm password");
        String conPassword = scn.nextLine();

        if (!password.equals(conPassword)) {
            ConsoleUi.warn("Passwords do not match.");
            pressEnterToContinue(scn);
            return;
        }

        if (!Database.isValidEmail(email)) {
            ConsoleUi.warn("Invalid email format.");
            pressEnterToContinue(scn);
            return;
        }
        if (Database.userExists(name, email)) {
            ConsoleUi.warn("That name or email is already registered.");
            pressEnterToContinue(scn);
            return;
        }
        if (Database.addUser(name, email, password)) {
            ConsoleUi.ok("Account created. You can log in now.");
        }
    }
}
