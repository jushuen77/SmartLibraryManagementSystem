import data.ConsoleUi;
import data.Database;
import java.util.Scanner;
import library.model.PatronType;

/*
 * Sign-up for library members (student, faculty, or public). Creates both a login and a patron record.
 */
public final class Register {

    private Register() {
    }

    private static void pressEnterToContinue(Scanner scn) {
        ConsoleUi.pressEnterHint();
        scn.nextLine();
    }

    public static void prompt(Scanner scn) {
        ConsoleUi.sectionTitle("Register (library member)");
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
            System.out.printf("\t\t\tEmail format is <name> + @ + your email provider!! FAH");
            System.out.println();
            pressEnterToContinue(scn);
            return;
        }
        if (Database.userExists(name, email)) {
            ConsoleUi.warn("That name or email is already registered.");
            pressEnterToContinue(scn);
            return;
        }

        PatronType memberType = PatronType.PUBLIC;
        ConsoleUi.menuStart("Member type");
        ConsoleUi.menuOption(1, "Student");
        ConsoleUi.menuOption(2, "Faculty");
        ConsoleUi.menuOption(3, "Public member");
        ConsoleUi.menuEnd();
        try {
            int mt = scn.nextInt();
            scn.nextLine();
            memberType = switch (mt) {
                case 1 -> PatronType.STUDENT;
                case 2 -> PatronType.FACULTY;
                default -> PatronType.PUBLIC;
            };
        } catch (Exception e) {
            scn.nextLine();
            ConsoleUi.warn("Invalid choice; using public member.");
        }

        if (Database.addUser(name, email, password)) {
            Database.ensurePatronForNewUser(name, email, memberType);
            ConsoleUi.ok("Account created as " + memberType.toDisplayString() + ". You can sign in now.");
        }
    }
}
