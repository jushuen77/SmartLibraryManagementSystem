import data.Database;
import java.util.Scanner;

/** Console login flow; call {@link #prompt(Scanner)} from {@link App}. For Swing, call {@link Database#authenticate} from your button handler. */
public final class Login {

    private Login() {
    }

    private static void pressEnterToContinue(Scanner scn) {
        System.out.print("Press Enter to continue...");
        scn.nextLine();
    }

    public static boolean prompt(Scanner scn) {
        System.out.print("Email or username: ");
        String identifier = scn.nextLine();
        System.out.print("Password: ");
        String password = scn.nextLine();

        if (Database.authenticate(identifier, password)) {
            System.out.println("Login successful.");
            return true;
        } else {
            System.out.println("Wrong email/username or password.");
            pressEnterToContinue(scn);
            return false;
        }
    }
}
