import data.Database;
import java.util.Scanner;

/** Console registration; for Swing, use the same checks and {@link Database#addUser}. */
public final class Register {

    private Register() {
    }

    private static void pressEnterToContinue(Scanner scn) {
        System.out.print("Press Enter to continue...");
        scn.nextLine();
    }

    public static void prompt(Scanner scn) {
        System.out.print("Name: ");
        String name = scn.nextLine();
        System.out.print("Email: ");
        String email = scn.nextLine();
        System.out.print("Password: ");
        String password = scn.nextLine();
        System.out.print("Confirm Password: ");
        String conPassword = scn.nextLine();

        if (!password.equals(conPassword)) {
            System.out.println("Passwords do not match. Please try again.");
            pressEnterToContinue(scn);
            return;
        }

        if (!Database.isValidEmail(email)) {
            System.out.println("Invalid email format.");
            pressEnterToContinue(scn);
            return;
        }
        if (Database.userExists(name, email)) {
            System.out.println("That name or email is already registered.");
            pressEnterToContinue(scn);
            return;
        }
        if (Database.addUser(name, email, password)) {
            System.out.println("Account created. You can log in now.");
        }
    }
}
