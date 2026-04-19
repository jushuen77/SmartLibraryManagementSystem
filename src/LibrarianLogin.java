import java.util.Scanner;

import data.ConsoleUi;

/*
 * Simple staff check before opening the librarian portal. Credentials are hardcoded for local/demo use only.
 * Replace with proper authentication for any real deployment.
 */
public final class LibrarianLogin {

    private static final String STAFF_USERNAME = "librarian";
    private static final String STAFF_PASSWORD = "lib123";

    private LibrarianLogin() {
    }

    public static boolean prompt(Scanner scn) {
        ConsoleUi.sectionTitle("Staff login");
        ConsoleUi.fieldPrompt("Username");
        String username = scn.nextLine();
        ConsoleUi.fieldPrompt("Password");
        String password = scn.nextLine();

        if (STAFF_USERNAME.equals(username) && STAFF_PASSWORD.equals(password)) {
            ConsoleUi.ok("Staff login successful.");
            return true;
        }
        ConsoleUi.warn("Invalid staff credentials.");
        return false;
    }
}
