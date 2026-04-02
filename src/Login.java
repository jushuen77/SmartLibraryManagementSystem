import data.Database;
import java.util.Scanner;
import data.ConsoleUi;

/*
 * Patron/student login screen in the terminal.
 * Reads identifier and password, then calls Database.authenticate.
 * A GUI could call the same Database method from a login button handler.
 */
public final class Login {

    private Login() {
    }

    private static void pressEnterToContinue(Scanner scn) {
        ConsoleUi.pressEnterHint();
        scn.nextLine();
    }

    public static boolean prompt(Scanner scn) {
        ConsoleUi.sectionTitle("Patron login");
        ConsoleUi.fieldPrompt("Email or username");
        String identifier = scn.nextLine();
        ConsoleUi.fieldPrompt("Password");
        String password = scn.nextLine();

        if (Database.authenticate(identifier, password)) {
            ConsoleUi.ok("Login successful.");
            return true;
        }
        ConsoleUi.warn("Wrong email/username or password.");
        pressEnterToContinue(scn);
        return false;
    }
}
