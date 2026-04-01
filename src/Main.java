import data.Database;
import java.util.Scanner;

public class Main {
    void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        boolean running = true;

        System.out.println("\nSmart Library - console menu");

        while (running) {
            System.out.println(" 1. Create / update database tables");
            System.out.println(" 2. Add user (login account)");
            System.out.println(" 3. View all users");
            System.out.println(" 4. Register patron");
            System.out.println(" 5. List patrons");
            System.out.println(" 6. Add library item");
            System.out.println(" 7. List items");
            System.out.println(" 8. Borrow item (patron id + item id)");
            System.out.println(" 9. Return item (item id)");
            System.out.println("10. List active loans");
            System.out.println("11. Exit");
            System.out.print("Choose: ");

            try {
                int choice = scn.nextInt();
                scn.nextLine();

                switch (choice) {
                    case 1:
                        Database.createTable();
                        break;
                    case 2:
                        promptAddUser(scn);
                        break;
                    case 3:
                        Database.printAllUsers();
                        break;
                    case 4:
                        promptAddPatron(scn);
                        break;
                    case 5:
                        Database.printAllPatrons();
                        break;
                    case 6:
                        promptAddItem(scn);
                        break;
                    case 7:
                        Database.printAllItems();
                        break;
                    case 8:
                        System.out.print("Patron id: ");
                        int pid = scn.nextInt();
                        System.out.print("Item id: ");
                        int iid = scn.nextInt();
                        scn.nextLine();
                        Database.borrowItem(pid, iid);
                        break;
                    case 9:
                        System.out.print("Item id: ");
                        int returnId = scn.nextInt();
                        scn.nextLine();
                        Database.returnItem(returnId);
                        break;
                    case 10:
                        Database.printActiveLoans();
                        break;
                    case 11:
                        running = false;
                        System.out.println("Goodbye.");
                        break;
                    default:
                        System.out.println("Invalid option.");
                }
            } catch (Exception e) {
                System.out.println("Invalid input. Use numbers where asked.");
                scn.nextLine();
            }
        }
        scn.close();
    }

    private static void promptAddUser(Scanner scn) {
        System.out.print("Name: ");
        String name = scn.nextLine();
        System.out.print("Email: ");
        String email = scn.nextLine();
        System.out.print("Password: ");
        String password = scn.nextLine();

        if (!Database.isValidEmail(email)) {
            System.out.println("Invalid email format.");
            return;
        }
        if (Database.userExists(name, email)) {
            System.out.println("Username or email already exists.");
            return;
        }
        if (Database.addUser(name, email, password)) {
            System.out.println("User registered.");
        }
    }

    private static void promptAddPatron(Scanner scn) {
        System.out.print("Name: ");
        String name = scn.nextLine();
        System.out.print("Email: ");
        String email = scn.nextLine();
        if (!Database.isValidEmail(email)) {
            System.out.println("Invalid email format.");
            return;
        }
        System.out.print("Patron type (STUDENT / FACULTY / PUBLIC): ");
        String type = scn.nextLine().trim();
        Database.addPatron(name, email, type);
    }

    private static void promptAddItem(Scanner scn) {
        System.out.print("Item type (BOOK / JOURNAL / DIGITAL): ");
        String itemType = scn.nextLine().trim();
        System.out.print("Title: ");
        String title = scn.nextLine();
        System.out.print("Author (optional): ");
        String author = scn.nextLine();
        System.out.print("ISBN (optional): ");
        String isbn = scn.nextLine();
        System.out.print("Genre (optional): ");
        String genre = scn.nextLine();
        Database.addItem(itemType, title, author, isbn, genre);
    }
}
