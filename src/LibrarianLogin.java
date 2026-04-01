import java.util.Scanner;

/** Simple staff login for librarian operations. */
public final class LibrarianLogin {

    private static final String STAFF_USERNAME = "librarian";
    private static final String STAFF_PASSWORD = "lib123";

    private LibrarianLogin() {
    }

    public static boolean prompt(Scanner scn) {
        System.out.print("Staff username: ");
        String username = scn.nextLine();
        System.out.print("Staff password: ");
        String password = scn.nextLine();

        if (STAFF_USERNAME.equals(username) && STAFF_PASSWORD.equals(password)) {
            System.out.println("Staff login successful.");
            return true;
        }
        System.out.println("Invalid staff credentials.");
        return false;
    }
}
