import java.util.Scanner;

import data.ConsoleUi;
import data.Database;
import data.UserSession;

/*
 * Library member login (students, faculty, and public members share this flow).
 */
public final class Login {

    private Login() {
    }

    private static void pressEnterToContinue(Scanner scn) {
        ConsoleUi.pauseThenClear(scn);
    }

    public static boolean prompt(Scanner scn) {
        ConsoleUi.sectionTitle("Library member login");
        ConsoleUi.info("Students, faculty, and public members use this sign-in.");
        ConsoleUi.muted("Enter 0 as email/username to cancel login.");
        ConsoleUi.fieldPrompt("Email or username");
        String identifier = scn.nextLine();
        if ("0".equals(identifier.trim())) {
            ConsoleUi.info("Login cancelled.");
            return false;
        }
        ConsoleUi.fieldPrompt("Password (0 = cancel)");
        String password = scn.nextLine();
        if ("0".equals(password.trim())) {
            ConsoleUi.info("Login cancelled.");
            return false;
        }

        String[] profile = Database.findUserNameEmailIfValidCredentials(identifier, password);
        if (profile != null) {
            UserSession.set(profile[0], profile[1], Database.getPatronTypeForEmail(profile[1]));
            return true;
        }
        ConsoleUi.warn("Sign-in failed: check your email or username and password, then try again.");
        pressEnterToContinue(scn);
        return false;
    }
}
