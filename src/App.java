import data.Database;
import java.util.Scanner;

public class App {
    public static void main(String[] args) {
        Database.createTable();

        Scanner scn = new Scanner(System.in);
        boolean running = true;

        while (running) {
            System.out.println("\nG4 Smart Library");
            System.out.println("1. Login");
            System.out.println("2. Register");
            System.out.println("3. Exit");
            System.out.print("Enter your choice: ");

            try {
                int choice = scn.nextInt();
                scn.nextLine();

                switch (choice) {
                    case 1:
                        Login.prompt(scn);
                        break;
                    case 2:
                        Register.prompt(scn);
                        break;
                    case 3:
                        running = false;
                        System.out.println("Goodbye.");
                        break;
                    default:
                        System.out.println("Invalid choice.");
                }
            } catch (Exception e) {
                System.out.println("Invalid input. Please enter a number.");
                scn.nextLine();
            }
        }
        scn.close();
    }
}
