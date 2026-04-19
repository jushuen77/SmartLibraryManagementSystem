import java.util.Scanner;

import data.ConsoleUi;


// Simple staff check before opening the librarian portal. Credentials are hardcoded for local/demo use only.
// Replace with proper authentication for any real deployment.

public final class LibrarianLogin {

    private static final String STAFF_USERNAME = "librarian";
    private static final String STAFF_PASSWORD = "lib123";

    private LibrarianLogin() {
    }

    public static boolean prompt(Scanner scn) {
        ConsoleUi.sectionTitle("Staff login");
        ConsoleUi.muted("Enter 0 as username to cancel.");
        ConsoleUi.fieldPrompt("Username");
        String username = scn.nextLine();
        if ("0".equals(username.trim())) {
            ConsoleUi.info("Staff login cancelled.");
            return false;
        }
        ConsoleUi.fieldPrompt("Password (0 = cancel)");
        String password = scn.nextLine();
        if ("0".equals(password.trim())) {
            ConsoleUi.info("Staff login cancelled.");
            return false;
        }

        if (STAFF_USERNAME.equals(username) && STAFF_PASSWORD.equals(password)) {
            return true;
        }
        ConsoleUi.warn("Staff login failed: that username or password does not match our records.");
        return false;
    }
}
