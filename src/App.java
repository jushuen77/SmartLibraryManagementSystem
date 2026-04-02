/*
 * Main entry for the G4 Smart Library console app.
 * The outer loop is the main menu; after login, an inner loop runs the library portal until the user goes back.
 * Start at main() at the bottom to see how menus call each show…Page() method.
 * (Yes, it is mostly Scanner + println. The glamour of enterprise software.)
 */
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
import data.ConsoleUi;

public class App {

    // Lines of e-book text per "page" before we ask you to press n again. Kindle, but make it homework.
    private static final int EBOOK_READER_LINES_PER_PAGE = 14;

    // Lets the human actually read the last wall of text before the next menu attacks.
    private static void pressEnterToContinue(Scanner scn) {
        ConsoleUi.pressEnterHint();
        scn.nextLine();
    }

    // --- List views: we ask the DB nicely, then spam the console ---

    private static void showBooksPage(Scanner scn) {
        ConsoleUi.sectionTitle("Books");
        for (Book b : Book.fetchAllFromDatabase()) {
            ConsoleUi.listItem(b.getTitle() + " by " + b.getAuthor());
        }
        pressEnterToContinue(scn);
    }

    private static void showJournalsPage(Scanner scn) {
        ConsoleUi.sectionTitle("Journals");
        for (Journal j : Journal.fetchAllFromDatabase()) {
            ConsoleUi.listItem(j.getTitle() + " by " + j.getAuthor());
        }
        pressEnterToContinue(scn);
    }

    private static void showDigitalResourcesPage(Scanner scn) {
        ConsoleUi.sectionTitle("Digital resources");
        for (DigitalResource d : DigitalResource.fetchAllFromDatabase()) {
            ConsoleUi.listItem(d.getTitle() + " by " + d.getAuthor());
        }
        pressEnterToContinue(scn);
    }

    private static void showPatronsPage(Scanner scn) {
        ConsoleUi.sectionTitle("Patrons");
        for (Patron p : Patron.fetchAllFromDatabase()) {
            ConsoleUi.listItem(p.getName() + " (" + p.getPatronType() + ")");
        }
        pressEnterToContinue(scn);
    }

    /*
     * Optional feature: in-terminal e-book viewer using plain text stored in reader_content on digital items.
     * A full product would use EPUB/PDF; here we paginate with n (next), p (previous), q (quit).
     * Budget e-ink. Zero batteries. Maximum ASCII.
     */
    private static void showEbookReaderPage(Scanner scn) {
        List<DigitalResource> list = DigitalResource.fetchAllFromDatabase();
        if (list.isEmpty()) {
            ConsoleUi.warn("No digital titles in the catalog.");
            pressEnterToContinue(scn);
            return;
        }
        ConsoleUi.sectionTitle("E-Book reader");
        for (int i = 0; i < list.size(); i++) {
            DigitalResource d = list.get(i);
            boolean hasText = d.getReaderContent() != null && !d.getReaderContent().isBlank();
            ConsoleUi.numberedLine(i + 1, d.getTitle() + " | ISBN: " + d.getIsbn()
                    + (hasText ? "" : "  (no reader text yet)"));
        }
        ConsoleUi.fieldPrompt("Open title # (0 = back)");
        try {
            int pick = scn.nextInt();
            scn.nextLine();
            if (pick <= 0 || pick > list.size()) {
                return;
            }
            DigitalResource book = list.get(pick - 1);
            String content = book.getReaderContent();
            if (content == null || content.isBlank()) {
                ConsoleUi.info("This title has no reader text loaded. Ask staff to attach an excerpt.");
                pressEnterToContinue(scn);
                return;
            }
            // split on any newline style (\n, \r\n, etc.) so excerpts from SampleCatalog behave on Windows too
            String[] lines = content.split("\\R");
            int totalPages = Math.max(1, (lines.length + EBOOK_READER_LINES_PER_PAGE - 1) / EBOOK_READER_LINES_PER_PAGE);
            int page = 0;
            boolean reading = true;
            while (reading) {
                ConsoleUi.readerPageBar(book.getTitle(), page + 1, totalPages);
                int start = page * EBOOK_READER_LINES_PER_PAGE;
                int end = Math.min(start + EBOOK_READER_LINES_PER_PAGE, lines.length);
                for (int i = start; i < end; i++) {
                    System.out.println("  " + lines[i]);
                }
                ConsoleUi.readerControlsHint();
                String cmd = scn.nextLine().trim().toLowerCase();
                if (cmd.equals("q")) {
                    reading = false;
                } else if (cmd.equals("n") && page < totalPages - 1) {
                    page++;
                } else if (cmd.equals("p") && page > 0) {
                    page--;
                }
            }
        } catch (Exception e) {
            ConsoleUi.warn("Invalid choice.");
            scn.nextLine();
        }
    }

    // Study rooms: Database does the rules; we just collect answers like a polite form wizard.
    private static void showStudyRoomBookingPage(Scanner scn) {
        boolean sub = true;
        while (sub) {
            ConsoleUi.menuStart("Study room booking");
            ConsoleUi.menuOption(1, "List rooms & book a slot");
            ConsoleUi.menuOption(2, "My bookings");
            ConsoleUi.menuOption(3, "Cancel a booking");
            ConsoleUi.menuOption(4, "Back");
            ConsoleUi.menuEnd();
            try {
                int c = scn.nextInt();
                scn.nextLine();
                switch (c) {
                    case 1:
                        // List rooms, then show which hourly slots are free or taken for the chosen date.
                        Database.printStudyRooms();
                        ConsoleUi.fieldPrompt("Room id to book");
                        int roomId = scn.nextInt();
                        scn.nextLine();
                        ConsoleUi.fieldPrompt("Your patron email");
                        String email = scn.nextLine();
                        ConsoleUi.fieldPrompt("Date (yyyy-MM-dd)");
                        String dateIso = scn.nextLine().trim();
                        Database.printStudySlotsForRoom(roomId, dateIso);
                        ConsoleUi.fieldPrompt("Slot number (1-" + Database.STUDY_TIME_SLOTS.length + ")");
                        int sn = scn.nextInt();
                        scn.nextLine();
                        if (sn < 1 || sn > Database.STUDY_TIME_SLOTS.length) {
                            ConsoleUi.warn("Invalid slot number.");
                        } else {
                            Database.bookStudyRoom(roomId, email, dateIso, Database.STUDY_TIME_SLOTS[sn - 1]);
                        }
                        pressEnterToContinue(scn);
                        break;
                    case 2:
                        System.out.print("Your patron email: ");
                        Database.printStudyBookingsForPatron(scn.nextLine());
                        pressEnterToContinue(scn);
                        break;
                    case 3:
                        ConsoleUi.fieldPrompt("Your patron email");
                        String em = scn.nextLine();
                        ConsoleUi.fieldPrompt("Booking id to cancel");
                        int bid = scn.nextInt();
                        scn.nextLine();
                        Database.cancelStudyBooking(bid, em);
                        pressEnterToContinue(scn);
                        break;
                    case 4:
                        sub = false;
                        break;
                    default:
                        ConsoleUi.warn("Invalid choice.");
                        pressEnterToContinue(scn);
                }
            } catch (Exception e) {
                ConsoleUi.warn("Invalid input. Use a number from the menu.");
                scn.nextLine();
                pressEnterToContinue(scn);
            }
        }
    }

    /*
     * Patron-side catalog: browse, search, borrow/return physical books, optional reservation when something's ON_LOAN.
     * Database does the heavy lifting; we're mostly parsing ints/lines and hoping the user does not type "banana".
     */
    private static void showCatalogSystemPage(Scanner scn) {
        boolean inCatalogMenu = true;
        while (inCatalogMenu) {
            ConsoleUi.menuStart("Catalog system");
            ConsoleUi.menuOption(1, "Browse by genre");
            ConsoleUi.menuOption(2, "Browse by author");
            ConsoleUi.menuOption(3, "Search book availability");
            ConsoleUi.menuOption(4, "Borrow book");
            ConsoleUi.menuOption(5, "Return book");
            ConsoleUi.menuOption(6, "Back");
            ConsoleUi.menuEnd();

            try {
                int choice = scn.nextInt();
                scn.nextLine();
                switch (choice) {
                    case 1:
                        ConsoleUi.sectionTitle("Catalog by genre");
                        for (Map.Entry<String, List<LibraryItem>> entry : CatalogSystem.organizeByGenre().entrySet()) {
                            ConsoleUi.muted(entry.getKey());
                            for (LibraryItem item : entry.getValue()) {
                                ConsoleUi.listItem(item.getTitle() + " | " + item.getAuthor() + " | ISBN: " + item.getIsbn());
                            }
                            ConsoleUi.blank();
                        }
                        pressEnterToContinue(scn);
                        break;
                    case 2:
                        ConsoleUi.sectionTitle("Catalog by author");
                        for (Map.Entry<String, List<LibraryItem>> entry : CatalogSystem.organizeByAuthor().entrySet()) {
                            ConsoleUi.muted(entry.getKey());
                            for (LibraryItem item : entry.getValue()) {
                                ConsoleUi.listItem(item.getTitle() + " | " + item.getGenre() + " | ISBN: " + item.getIsbn());
                            }
                            ConsoleUi.blank();
                        }
                        pressEnterToContinue(scn);
                        break;
                    case 3:
                        ConsoleUi.fieldPrompt("Book title or ISBN");
                        String query = scn.nextLine();
                        List<LibraryItem> matches = CatalogSystem.searchBooks(query);
                        if (matches.isEmpty()) {
                            ConsoleUi.info("No books matched that search.");
                        } else {
                            ConsoleUi.sectionTitle("Matching books");
                            for (LibraryItem item : matches) {
                                ConsoleUi.listItem(item.getTitle()
                                        + " | ISBN: " + item.getIsbn()
                                        + " | Status: " + item.getStatus());
                            }

                            // only bother asking about reservations if at least one match is actually out on loan
                            boolean hasOnLoan = false;
                            for (LibraryItem item : matches) {
                                if ("ON_LOAN".equals(item.getStatus().name())) {
                                    hasOnLoan = true;
                                    break;
                                }
                            }

                            if (hasOnLoan) {
                                ConsoleUi.fieldPrompt("Reserve an ON_LOAN copy? (y/n)");
                                String reserveChoice = scn.nextLine().trim();
                                if (reserveChoice.equalsIgnoreCase("y")) {
                                    ConsoleUi.fieldPrompt("ISBN to reserve");
                                    String reserveIsbn = scn.nextLine();
                                    LibraryItem selected = CatalogSystem.findByIsbn(reserveIsbn);

                                    if (selected == null || !"BOOK".equals(selected.getSqlItemType())) {
                                        ConsoleUi.warn("No book with that ISBN.");
                                    } else if (!"ON_LOAN".equals(selected.getStatus().name())) {
                                        ConsoleUi.info("That book is not on loan; no reservation needed.");
                                    } else {
                                        ConsoleUi.fieldPrompt("Your name");
                                        String requesterName = scn.nextLine();
                                        ConsoleUi.fieldPrompt("Your email");
                                        String requesterEmail = scn.nextLine();
                                        if (!Database.isValidEmail(requesterEmail)) {
                                            ConsoleUi.warn("Invalid email format.");
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
                        ConsoleUi.fieldPrompt("Patron email");
                        String patronEmail = scn.nextLine();
                        ConsoleUi.fieldPrompt("Book ISBN");
                        String borrowIsbn = scn.nextLine();
                        Database.borrowBookByPatronEmailAndIsbn(patronEmail, borrowIsbn);
                        pressEnterToContinue(scn);
                        break;
                    case 5:
                        ConsoleUi.fieldPrompt("Book ISBN to return");
                        Database.returnBookByIsbn(scn.nextLine());
                        pressEnterToContinue(scn);
                        break;
                    case 6:
                        inCatalogMenu = false;
                        break;
                    default:
                        ConsoleUi.warn("Invalid choice.");
                        pressEnterToContinue(scn);
                }
            } catch (Exception e) {
                ConsoleUi.warn("Invalid input. Enter a menu number.");
                scn.nextLine();
                pressEnterToContinue(scn);
            }
        }
    }

    // Staff menu: acquisitions, lost books, fines, inventory. Shh — you needed the secret handshake (LibrarianLogin) first.
    private static void showLibrarianPortal(Scanner scn) {
        boolean inStaffMenu = true;
        while (inStaffMenu) {
            ConsoleUi.menuStart("Librarian portal");
            ConsoleUi.menuOption(1, "Add new acquisition (book)");
            ConsoleUi.menuOption(2, "Mark book lost / damaged (ISBN)");
            ConsoleUi.menuOption(3, "Audit inventory");
            ConsoleUi.menuOption(4, "List lost books");
            ConsoleUi.menuOption(5, "View outstanding fines");
            ConsoleUi.menuOption(6, "Record fine payment");
            ConsoleUi.menuOption(7, "Back");
            ConsoleUi.menuEnd();
            try {
                int choice = scn.nextInt();
                scn.nextLine();
                switch (choice) {
                    case 1:
                        ConsoleUi.sectionTitle("New book acquisition");
                        ConsoleUi.fieldPrompt("Title");
                        String title = scn.nextLine();
                        ConsoleUi.fieldPrompt("Author");
                        String author = scn.nextLine();
                        ConsoleUi.fieldPrompt("ISBN");
                        String isbn = scn.nextLine();
                        ConsoleUi.fieldPrompt("Genre");
                        String genre = scn.nextLine();
                        ConsoleUi.fieldPrompt("Loan duration (days)");
                        int loanDays = scn.nextInt();
                        scn.nextLine();
                        ConsoleUi.fieldPrompt("Borrowing limit");
                        int borrowingLimit = scn.nextInt();
                        scn.nextLine();
                        ConsoleUi.fieldPrompt("Daily fine ($)");
                        double dailyFine = scn.nextDouble();
                        scn.nextLine();
                        Database.addBookAcquisition(title, author, isbn, genre, loanDays, borrowingLimit, dailyFine);
                        pressEnterToContinue(scn);
                        break;
                    case 2:
                        ConsoleUi.fieldPrompt("ISBN to mark lost/damaged");
                        String damagedIsbn = scn.nextLine();
                        Database.removeDamagedBookByIsbn(damagedIsbn);
                        pressEnterToContinue(scn);
                        break;
                    case 3:
                        Database.printInventoryAudit();
                        pressEnterToContinue(scn);
                        break;
                    case 4:
                        Database.printLostBooks();
                        pressEnterToContinue(scn);
                        break;
                    case 5:
                        Database.printOutstandingFines();
                        pressEnterToContinue(scn);
                        break;
                    case 6:
                        ConsoleUi.fieldPrompt("Fine id (from fines list)");
                        int fineId = scn.nextInt();
                        scn.nextLine();
                        ConsoleUi.fieldPrompt("Payment amount ($)");
                        double payAmount = scn.nextDouble();
                        scn.nextLine();
                        Database.recordFinePayment(fineId, payAmount);
                        pressEnterToContinue(scn);
                        break;
                    case 7:
                        inStaffMenu = false;
                        break;
                    default:
                        ConsoleUi.warn("Invalid choice.");
                        pressEnterToContinue(scn);
                }
            } catch (Exception e) {
                ConsoleUi.warn("Invalid input. Enter a menu number.");
                scn.nextLine();
                pressEnterToContinue(scn);
            }
        }
    }

    public static void main(String[] args) {
        // SQLite wakes up. If the file was empty, we throw in sample wizards and overdue policies for free.
        Database.createTable();

        ConsoleUi.welcomeBanner();

        Scanner scn = new Scanner(System.in);
        boolean running = true;
        boolean loggedIn = false;

        // Matryoshka menus: big loop = lobby, smaller loop = library after login, until someone hits Back.
        while (running) {
            ConsoleUi.menuStart("Main menu");
            ConsoleUi.menuOption(1, "Login");
            ConsoleUi.menuOption(2, "Register");
            ConsoleUi.menuOption(3, "Logout");
            ConsoleUi.menuOption(4, "Librarian staff login");
            ConsoleUi.menuOption(5, "Exit");
            ConsoleUi.menuEnd();

            try {
                int choice = scn.nextInt();
                scn.nextLine();

                switch (choice) {
                    case 1:
                        loggedIn = Login.prompt(scn);
                        if (loggedIn) {
                            boolean inLibraryMenu = true;
                            while (inLibraryMenu && loggedIn) {
                                ConsoleUi.menuStart("Library portal");
                                ConsoleUi.menuOption(1, "View books");
                                ConsoleUi.menuOption(2, "View journals");
                                ConsoleUi.menuOption(3, "View digital resources");
                                ConsoleUi.menuOption(4, "View patrons");
                                ConsoleUi.menuOption(5, "Catalog system");
                                ConsoleUi.menuOption(6, "E-Book reader");
                                ConsoleUi.menuOption(7, "Study room booking");
                                ConsoleUi.menuOption(8, "Back to main menu");
                                ConsoleUi.menuEnd();
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
                                            showEbookReaderPage(scn);
                                            break;
                                        case 7:
                                            showStudyRoomBookingPage(scn);
                                            break;
                                        case 8:
                                            inLibraryMenu = false;
                                            break;
                                        default:
                                            ConsoleUi.warn("Invalid choice.");
                                            pressEnterToContinue(scn);
                                    }
                                } catch (Exception e) {
                                    ConsoleUi.warn("Invalid input. Enter a menu number.");
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
                            ConsoleUi.ok("Logged out.");
                        } else {
                            ConsoleUi.info("You are not logged in.");
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
                        ConsoleUi.blank();
                        ConsoleUi.ok("Goodbye. See you at the library.");
                        break;
                    default:
                        ConsoleUi.warn("Invalid choice.");
                        pressEnterToContinue(scn);
                }
            } catch (Exception e) {
                ConsoleUi.warn("Invalid input. Enter a menu number.");
                scn.nextLine();
                pressEnterToContinue(scn);
            }
        }
        scn.close(); // end of shift — put the Scanner in the drawer
    }
}
