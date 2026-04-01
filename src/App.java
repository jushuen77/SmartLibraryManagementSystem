import data.Database;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import library.CatalogSystem;
import library.model.Book;
import library.model.BookReservation;
import library.model.DigitalResource;
import library.model.Journal;
import library.model.LibraryItem;
import library.model.Patron;

public class App {
    private static void pressEnterToContinue(Scanner scn) {
        System.out.print("Press Enter to continue...");
        scn.nextLine();
    }

    private static void showBooksPage(Scanner scn) {
        System.out.println("\nBooks");
        for (Book b : Book.fetchAllFromDatabase()) {
            System.out.println("- " + b.getTitle() + " by " + b.getAuthor());
        }
        pressEnterToContinue(scn);
    }

    private static void showJournalsPage(Scanner scn) {
        System.out.println("\nJournals");
        for (Journal j : Journal.fetchAllFromDatabase()) {
            System.out.println("- " + j.getTitle() + " by " + j.getAuthor());
        }
        pressEnterToContinue(scn);
    }

    private static void showDigitalResourcesPage(Scanner scn) {
        System.out.println("\nDigital Resources");
        for (DigitalResource d : DigitalResource.fetchAllFromDatabase()) {
            System.out.println("- " + d.getTitle() + " by " + d.getAuthor());
        }
        pressEnterToContinue(scn);
    }

    private static void showPatronsPage(Scanner scn) {
        System.out.println("\nPatrons");
        for (Patron p : Patron.fetchAllFromDatabase()) {
            System.out.println("- " + p.getName() + " (" + p.getPatronType() + ")");
        }
        pressEnterToContinue(scn);
    }

    private static void showCatalogSystemPage(Scanner scn) {
        boolean inCatalogMenu = true;
        while (inCatalogMenu) {
            System.out.println("\nCatalog System");
            System.out.println("1. Browse by Genre");
            System.out.println("2. Browse by Author");
            System.out.println("3. Search Book Availability");
            System.out.println("4. Borrow Book");
            System.out.println("5. Back");
            System.out.print("Enter your choice: ");

            try {
                int choice = scn.nextInt();
                scn.nextLine();
                switch (choice) {
                    case 1:
                        System.out.println("\nCatalog by Genre");
                        for (Map.Entry<String, List<LibraryItem>> entry : CatalogSystem.organizeByGenre().entrySet()) {
                            System.out.println("\n" + entry.getKey() + ":");
                            for (LibraryItem item : entry.getValue()) {
                                System.out.println("- " + item.getTitle() + " | " + item.getAuthor() + " | ISBN: " + item.getIsbn());
                            }
                        }
                        pressEnterToContinue(scn);
                        break;
                    case 2:
                        System.out.println("\nCatalog by Author");
                        for (Map.Entry<String, List<LibraryItem>> entry : CatalogSystem.organizeByAuthor().entrySet()) {
                            System.out.println("\n" + entry.getKey() + ":");
                            for (LibraryItem item : entry.getValue()) {
                                System.out.println("- " + item.getTitle() + " | " + item.getGenre() + " | ISBN: " + item.getIsbn());
                            }
                        }
                        pressEnterToContinue(scn);
                        break;
                    case 3:
                        System.out.print("Enter book title or ISBN: ");
                        String query = scn.nextLine();
                        List<LibraryItem> matches = CatalogSystem.searchBooks(query);
                        if (matches.isEmpty()) {
                            System.out.println("No books found for that search.");
                        } else {
                            System.out.println("\nMatching Books:");
                            for (LibraryItem item : matches) {
                                System.out.println("- " + item.getTitle()
                                        + " | ISBN: " + item.getIsbn()
                                        + " | Status: " + item.getStatus());
                            }

                            boolean hasOnLoan = false;
                            for (LibraryItem item : matches) {
                                if ("ON_LOAN".equals(item.getStatus().name())) {
                                    hasOnLoan = true;
                                    break;
                                }
                            }

                            if (hasOnLoan) {
                                System.out.print("\nDo you want to place a reservation for an ON_LOAN book? (y/n): ");
                                String reserveChoice = scn.nextLine().trim();
                                if (reserveChoice.equalsIgnoreCase("y")) {
                                    System.out.print("Enter book ISBN to reserve: ");
                                    String reserveIsbn = scn.nextLine();
                                    LibraryItem selected = CatalogSystem.findByIsbn(reserveIsbn);

                                    if (selected == null || !"BOOK".equals(selected.getSqlItemType())) {
                                        System.out.println("Book with that ISBN was not found.");
                                    } else if (!"ON_LOAN".equals(selected.getStatus().name())) {
                                        System.out.println("That book is not currently on loan.");
                                    } else {
                                        System.out.print("Your name: ");
                                        String requesterName = scn.nextLine();
                                        System.out.print("Your email: ");
                                        String requesterEmail = scn.nextLine();
                                        if (!Database.isValidEmail(requesterEmail)) {
                                            System.out.println("Invalid email format.");
                                        } else {
                                            Database.addBookReservation(
                                                    new BookReservation(selected.getId(), requesterName, requesterEmail));
                                        }
                                    }
                                }
                            }
                        }
                        pressEnterToContinue(scn);
                        break;
                    case 4:
                        System.out.print("Enter your patron email: ");
                        String patronEmail = scn.nextLine();
                        System.out.print("Enter book ISBN to borrow: ");
                        String borrowIsbn = scn.nextLine();
                        Database.borrowBookByPatronEmailAndIsbn(patronEmail, borrowIsbn);
                        pressEnterToContinue(scn);
                        break;
                    case 5:
                        inCatalogMenu = false;
                        break;
                    default:
                        System.out.println("Invalid choice.");
                        pressEnterToContinue(scn);
                }
            } catch (Exception e) {
                System.out.println("Invalid input. Please enter a number.");
                scn.nextLine();
                pressEnterToContinue(scn);
            }
        }
    }

    private static void showLibrarianPortal(Scanner scn) {
        boolean inStaffMenu = true;
        while (inStaffMenu) {
            System.out.println("\nLibrarian Portal");
            System.out.println("1. Add New Acquisition (Book)");
            System.out.println("2. Remove Damaged Book");
            System.out.println("3. Audit Inventory");
            System.out.println("4. Back");
            System.out.print("Enter your choice: ");
            try {
                int choice = scn.nextInt();
                scn.nextLine();
                switch (choice) {
                    case 1:
                        System.out.print("Title: ");
                        String title = scn.nextLine();
                        System.out.print("Author: ");
                        String author = scn.nextLine();
                        System.out.print("ISBN: ");
                        String isbn = scn.nextLine();
                        System.out.print("Genre: ");
                        String genre = scn.nextLine();
                        System.out.print("Loan duration days: ");
                        int loanDays = scn.nextInt();
                        scn.nextLine();
                        System.out.print("Borrowing limit: ");
                        int borrowingLimit = scn.nextInt();
                        scn.nextLine();
                        System.out.print("Daily fine: ");
                        double dailyFine = scn.nextDouble();
                        scn.nextLine();
                        Database.addBookAcquisition(title, author, isbn, genre, loanDays, borrowingLimit, dailyFine);
                        pressEnterToContinue(scn);
                        break;
                    case 2:
                        System.out.print("Enter damaged book ISBN: ");
                        String damagedIsbn = scn.nextLine();
                        Database.removeDamagedBookByIsbn(damagedIsbn);
                        pressEnterToContinue(scn);
                        break;
                    case 3:
                        Database.printInventoryAudit();
                        pressEnterToContinue(scn);
                        break;
                    case 4:
                        inStaffMenu = false;
                        break;
                    default:
                        System.out.println("Invalid choice.");
                        pressEnterToContinue(scn);
                }
            } catch (Exception e) {
                System.out.println("Invalid input. Please enter a number.");
                scn.nextLine();
                pressEnterToContinue(scn);
            }
        }
    }

    public static void main(String[] args) {
        Database.createTable();

        Scanner scn = new Scanner(System.in);
        boolean running = true;
        boolean loggedIn = false;

        while (running) {
            System.out.println("\nG4 Smart Library");
            System.out.println("1. Login");
            System.out.println("2. Register");
            System.out.println("3. Logout");
            System.out.println("4. Librarian Staff Login");
            System.out.println("5. Exit");
            System.out.print("Enter your choice: ");

            try {
                int choice = scn.nextInt();
                scn.nextLine();

                switch (choice) {
                    case 1:
                        loggedIn = Login.prompt(scn);
                        if (loggedIn) {
                            boolean inLibraryMenu = true;
                            while (inLibraryMenu && loggedIn) {
                                System.out.println("\nLibrary Portal (Logged In)");
                                System.out.println("1. View Books");
                                System.out.println("2. View Journals");
                                System.out.println("3. View Digital Resources");
                                System.out.println("4. View Patrons");
                                System.out.println("5. Catalog System");
                                System.out.println("6. Back to main menu");
                                System.out.print("Enter your choice: ");
                                try {
                                    int libraryChoice = scn.nextInt();
                                    scn.nextLine();
                                    switch (libraryChoice) {
                                        case 1:
                                            showBooksPage(scn);
                                            break;
                                        case 2:
                                            showJournalsPage(scn);
                                            break;
                                        case 3:
                                            showDigitalResourcesPage(scn);
                                            break;
                                        case 4:
                                            showPatronsPage(scn);
                                            break;
                                        case 5:
                                            showCatalogSystemPage(scn);
                                            break;
                                        case 6:
                                            inLibraryMenu = false;
                                            break;
                                        default:
                                            System.out.println("Invalid choice.");
                                            pressEnterToContinue(scn);
                                    }
                                } catch (Exception e) {
                                    System.out.println("Invalid input. Please enter a number.");
                                    scn.nextLine();
                                    pressEnterToContinue(scn);
                                }
                            }
                        }
                        break;
                    case 2:
                        Register.prompt(scn);
                        break;
                    case 3:
                        if (loggedIn) {
                            loggedIn = false;
                            System.out.println("Logged out.");
                        } else {
                            System.out.println("You are not logged in.");
                            pressEnterToContinue(scn);
                        }
                        break;
                    case 4:
                        if (LibrarianLogin.prompt(scn)) {
                            showLibrarianPortal(scn);
                        } else {
                            pressEnterToContinue(scn);
                        }
                        break;
                    case 5:
                        running = false;
                        System.out.println("Goodbye.");
                        break;
                    default:
                        System.out.println("Invalid choice.");
                        pressEnterToContinue(scn);
                }
            } catch (Exception e) {
                System.out.println("Invalid input. Please enter a number.");
                scn.nextLine();
                pressEnterToContinue(scn);
            }
        }
        scn.close();
    }
}
