import data.Database;
import java.util.Scanner;

/** Console login flow; call {@link #prompt(Scanner)} from {@link App}. For Swing, call {@link Database#authenticate} from your button handler. */
public final class Login {

    private Login() {
    }

    public static void prompt(Scanner scn) {
        System.out.print("Email or username: ");
        String identifier = scn.nextLine();
        System.out.print("Password: ");
        String password = scn.nextLine();

        if (Database.authenticate(identifier, password)) {
            System.out.println("Login successful.");
        } else {
            System.out.println("Wrong email/username or password.");
        }
    }
}
