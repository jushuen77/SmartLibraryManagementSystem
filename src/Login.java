import data.ConsoleUi;
import data.Database;
import data.UserSession;
import java.util.Scanner;

/*
 * Library member login (students, faculty, and public members share this flow).
 */
public final class Login {

    private Login() {
    }

    private static void pressEnterToContinue(Scanner scn) {
        ConsoleUi.pressEnterHint();
        scn.nextLine();
    }

    public static boolean prompt(Scanner scn) {
        ConsoleUi.sectionTitle("Library member login");
        ConsoleUi.info("Students, faculty, and public members use this sign-in.");
        ConsoleUi.fieldPrompt("Email or username");
        String identifier = scn.nextLine();
        ConsoleUi.fieldPrompt("Password");
        String password = scn.nextLine();

        String[] profile = Database.findUserNameEmailIfValidCredentials(identifier, password);
        if (profile != null) {
            UserSession.set(profile[0], profile[1], Database.getPatronTypeForEmail(profile[1]));
            ConsoleUi.ok("Welcome, " + UserSession.getMemberType().toDisplayString() + ".");
            return true;
        }
        ConsoleUi.warn("Wrong email/username or password.");
        pressEnterToContinue(scn);
        return false;
    }
}
