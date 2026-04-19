
import java.util.Scanner;

import data.ConsoleUi;
import data.Database;
import data.InputReaders;
import library.model.PatronType;


// Sign-up for library members (student, faculty, or public). Creates both a login and a patron record.
public final class Register {

    private Register() {
    }

    public static void prompt(Scanner scn) {
        ConsoleUi.sectionTitle("Register (library member)");
        ConsoleUi.muted("Enter 0 as name to cancel registration before creating an account.");
        ConsoleUi.fieldPrompt("Name (0 = cancel)");
        String name = scn.nextLine();
        if ("0".equals(name.trim())) {
            ConsoleUi.info("Registration cancelled.");
            return;
        }
        ConsoleUi.fieldPrompt("Email (0 = cancel)");
        String email = scn.nextLine();
        if ("0".equals(email.trim())) {
            ConsoleUi.info("Registration cancelled.");
            return;
        }
        ConsoleUi.fieldPrompt("Password (0 = cancel)");
        String password = scn.nextLine();
        if ("0".equals(password.trim())) {
            ConsoleUi.info("Registration cancelled.");
            return;
        }
        ConsoleUi.fieldPrompt("Confirm password (0 = cancel)");
        String conPassword = scn.nextLine();
        if ("0".equals(conPassword.trim())) {
            ConsoleUi.info("Registration cancelled.");
            return;
        }

        if (!password.equals(conPassword)) {
            ConsoleUi.warn("The password and confirmation do not match. Re-enter them so both fields are identical.");
            ConsoleUi.pauseThenClear(scn);
            return;
        }

        if (!Database.isValidEmail(email)) {
            ConsoleUi.warn("That email does not look valid. Use the form name@provider (for example you@school.edu).");
            System.out.printf("\t\t\tEmail format is <name> + @ + your email provider!! FAH");
            System.out.println();
            ConsoleUi.pauseThenClear(scn);
            return;
        }
        if (Database.userExists(name, email)) {
            ConsoleUi.warn("An account already uses that name or email. Sign in instead, or use a different email.");
            ConsoleUi.pauseThenClear(scn);
            return;
        }

        PatronType memberType = PatronType.PUBLIC;
        ConsoleUi.menuStart("Member type");
        ConsoleUi.menuOption(0, "Cancel — do not create account");
        ConsoleUi.menuOption(1, "Student");
        ConsoleUi.menuOption(2, "Faculty");
        ConsoleUi.menuOption(3, "Public member");
        ConsoleUi.menuEnd();
        int mt = InputReaders.readMenuInt(scn, 0, 3);
        if (mt == 0) {
            ConsoleUi.info("Registration cancelled. No account was created.");
            return;
        }
        memberType = switch (mt) {
            case 1 -> PatronType.STUDENT;
            case 2 -> PatronType.FACULTY;
            default -> PatronType.PUBLIC;
        };

        if (Database.addUser(name, email, password)) {
            Database.ensurePatronForNewUser(name, email, memberType);
            ConsoleUi.ok("Account created as " + memberType.toDisplayString() + ". You can sign in now.");
        }
    }
}
