/*
 * G4 Smart Library — console UI entry point.
 *
 * READ IN THIS ORDER (matches what the user sees on screen):
 *
 *   1. main()                    → startup, then main menu loop
 *   2. runLibraryPortal()        → after member login: options 1–9 (books … back)
 *   3. showBooksPage()           → portal option 1
 *   4. showJournalsPage()        → portal option 2
 *   5. showDigitalResourcesPage()→ portal option 3
 *   6. showUserProfilePage()     → portal option 4 (Patron record)
 *   7. showCatalogSystemPage()   → portal option 5
 *   8. showEbookReaderPage()     → portal option 6
 *   9. showStudyRoomBookingPage()→ portal option 7
 *  10. showLibrarianPortal()     → main menu → staff login
 *  11. showPatronsPage()         → librarian portal (staff)
 *
 * Data access lives in data.Database; layout in data.ConsoleUi.
 */

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import data.ConsoleUi;
import data.Database;
import data.Database.ActiveBookLoan;
import data.Database.StudyBookingRow;
import data.Database.StudyRoomRow;
import data.UserSession;
import library.CatalogSystem;
import library.model.Book;
import library.model.BookReservation;
import library.model.DigitalResource;
import library.model.Journal;
import library.model.LibraryItem;
import library.model.Patron;

public class App {

    private static final int EBOOK_READER_LINES_PER_PAGE = 14;

    // -------------------------------------------------------------------------
    // Small helpers (used everywhere)
    // -------------------------------------------------------------------------

    private static void pressEnterToContinue(Scanner scn) {
        ConsoleUi.pressEnterHint();
        scn.nextLine();
    }

    private static String toUserStatus(LibraryItem item) {
        if ("ON_LOAN".equals(item.getStatus().name())) {
            return "On Loan";
        }
        if ("AVAILABLE".equals(item.getStatus().name())) {
            return "Available";
        }
        return item.getStatus().name();
    }

    // -------------------------------------------------------------------------
    // 1. Program entry
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        Database.createTable();
        ConsoleUi.welcomeBanner();

        Scanner scn = new Scanner(System.in);
        boolean running = true;

        while (running) {
            ConsoleUi.menuStart("Main menu");
            ConsoleUi.menuOption(1, "Library member login");
            ConsoleUi.menuOption(2, "Register");
            ConsoleUi.menuOption(3, "Librarian staff login");
            ConsoleUi.menuOption(4, "Exit");
            ConsoleUi.menuEnd();

            try {
                int choice = scn.nextInt();
                scn.nextLine();

                switch (choice) {
                    case 1:
                        if (UserSession.isLoggedIn()) {
                            ConsoleUi.sectionTitle("Library member login");
                            ConsoleUi.info("Already signed in as " + UserSession.getDisplayName()
                                    + " (" + UserSession.getMemberType().toDisplayString() + ") <"
                                    + UserSession.getEmail() + ">.");
                        } else if (!Login.prompt(scn)) {
                            break;
                        }
                        runLibraryPortal(scn);
                        break;
                    case 2:
                        Register.prompt(scn);
                        break;
                    case 3:
                        if (LibrarianLogin.prompt(scn)) {
                            showLibrarianPortal(scn);
                        } else {
                            pressEnterToContinue(scn);
                        }
                        break;
                    case 4:
                        running = false;
                        UserSession.clear();
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
        scn.close();
    }

    // -------------------------------------------------------------------------
    // 2. Library portal (main menu → member login → this loop)
    // Menu order: books, journals, digital, profile, catalog, e-reader, study rooms, log out, back.
    // -------------------------------------------------------------------------

    private static void runLibraryPortal(Scanner scn) {
        boolean inLibraryMenu = true;
        while (inLibraryMenu && UserSession.isLoggedIn()) {
            ConsoleUi.menuStart("Library portal");
            ConsoleUi.menuOption(1, "View books");
            ConsoleUi.menuOption(2, "View journals");
            ConsoleUi.menuOption(3, "View digital resources");
            ConsoleUi.menuOption(4, "My profile");
            ConsoleUi.menuOption(5, "Catalog");
            ConsoleUi.menuOption(6, "E-book reader");
            ConsoleUi.menuOption(7, "Study room booking");
            ConsoleUi.menuOption(8, "Log out");
            ConsoleUi.menuOption(9, "Back to main menu");
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
                        showUserProfilePage(scn);
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
                        UserSession.clear();
                        ConsoleUi.ok("Logged out.");
                        inLibraryMenu = false;
                        break;
                    case 9:
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

    // -------------------------------------------------------------------------
    // 3. Portal option 4 — My profile (Patron record for signed-in email)
    // -------------------------------------------------------------------------

    private static void showUserProfilePage(Scanner scn) {
        if (!UserSession.isLoggedIn()) {
            ConsoleUi.warn("Sign in from the main menu first.");
            pressEnterToContinue(scn);
            return;
        }
        Patron profile = Database.getPatronByEmail(UserSession.getEmail());
        ConsoleUi.sectionTitle("My profile");
        if (profile == null) {
            ConsoleUi.warn("No library member record is linked to your account. Contact staff or register again.");
            pressEnterToContinue(scn);
            return;
        }
        ConsoleUi.muted("Sign-in name: " + UserSession.getDisplayName());
        for (String line : profile.profileLines()) {
            ConsoleUi.listItem(line);
        }
        List<ActiveBookLoan> borrowed = Database.getActiveBookLoansForPatron(UserSession.getEmail());
        ConsoleUi.blank();
        ConsoleUi.muted("Books borrowed right now: " + borrowed.size());
        if (borrowed.isEmpty()) {
            ConsoleUi.muted("(You have no borrowed books.)");
        } else {
            for (ActiveBookLoan row : borrowed) {
                ConsoleUi.listItem(row.title + " | due " + ConsoleUi.formatDate(row.dueDate));
            }
        }
        pressEnterToContinue(scn);
    }

    // -------------------------------------------------------------------------
    // 4. Portal option 1 — View books
    // -------------------------------------------------------------------------

    private static void showBooksPage(Scanner scn) {
        ConsoleUi.sectionTitle("Books");
        for (Book b : Book.fetchAllFromDatabase()) {
            ConsoleUi.listItem(b.getTitle() + " by " + b.getAuthor());
        }
        pressEnterToContinue(scn);
    }

    // -------------------------------------------------------------------------
    // 5. Portal option 2 — View journals
    // -------------------------------------------------------------------------

    private static void showJournalsPage(Scanner scn) {
        ConsoleUi.sectionTitle("Journals");
        for (Journal j : Journal.fetchAllFromDatabase()) {
            ConsoleUi.listItem(j.getTitle() + " by " + j.getAuthor());
        }
        pressEnterToContinue(scn);
    }

    // -------------------------------------------------------------------------
    // 6. Portal option 3 — View digital resources
    // -------------------------------------------------------------------------

    private static void showDigitalResourcesPage(Scanner scn) {
        ConsoleUi.sectionTitle("Digital resources");
        for (DigitalResource d : DigitalResource.fetchAllFromDatabase()) {
            ConsoleUi.listItem(d.getTitle() + " by " + d.getAuthor());
        }
        pressEnterToContinue(scn);
    }

    // -------------------------------------------------------------------------
    // 7. Portal option 5 — Catalog (browse / search / borrow / return)
    // -------------------------------------------------------------------------

    private static void showCatalogSystemPage(Scanner scn) {
        if (!UserSession.isLoggedIn()) {
            ConsoleUi.warn("Sign in from the main menu first.");
            pressEnterToContinue(scn);
            return;
        }
        final String patronEmail = UserSession.getEmail();
        boolean inCatalogMenu = true;
        while (inCatalogMenu) {
            ConsoleUi.menuStart("Catalog");
            ConsoleUi.menuOption(1, "Browse by genre");
            ConsoleUi.menuOption(2, "Browse by author");
            ConsoleUi.menuOption(3, "Search book availability");
            ConsoleUi.menuOption(4, "Borrow a book");
            ConsoleUi.menuOption(5, "Return a book");
            ConsoleUi.menuOption(6, "Back");
            ConsoleUi.menuEnd();

            try {
                int choice = scn.nextInt();
                scn.nextLine();
                switch (choice) {
                    case 1:
                        ConsoleUi.sectionTitle("By genre");
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
                        ConsoleUi.sectionTitle("By author");
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
                                        + " | Status: " + toUserStatus(item));
                            }

                            boolean hasOnLoan = false;
                            for (LibraryItem item : matches) {
                                if ("ON_LOAN".equals(item.getStatus().name())) {
                                    hasOnLoan = true;
                                    break;
                                }
                            }

                            if (hasOnLoan) {
                                ConsoleUi.fieldPrompt("Reserve a book that is on loan? (y/n)");
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
                                        String requesterName = UserSession.getDisplayName();
                                        if (requesterName == null || requesterName.isBlank()) {
                                            requesterName = "Patron";
                                        }
                                        String requesterEmail = UserSession.getEmail();
                                        if (!Database.isValidEmail(requesterEmail)) {
                                            ConsoleUi.warn("Your account email is invalid for a reservation.");
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
                        ConsoleUi.info("Signed in as: " + UserSession.getDisplayName()
                                + " (" + UserSession.getMemberType().toDisplayString() + ") <" + patronEmail + ">");
                        ConsoleUi.menuStart("Borrow by");
                        ConsoleUi.menuOption(1, "Pick from available books");
                        ConsoleUi.menuOption(2, "Search by title keywords");
                        ConsoleUi.menuOption(3, "Enter ISBN");
                        ConsoleUi.menuEnd();
                        int borrowMethod = scn.nextInt();
                        scn.nextLine();
                        switch (borrowMethod) {
                            case 1:
                                List<Book> availableBooks = Database.getAvailableBooks();
                                if (availableBooks.isEmpty()) {
                                    ConsoleUi.info("No available books right now.");
                                    break;
                                }
                                ConsoleUi.sectionTitle("Available books");
                                for (int i = 0; i < availableBooks.size(); i++) {
                                    Book b = availableBooks.get(i);
                                    ConsoleUi.numberedLine(i + 1, b.getTitle() + " | ISBN: " + b.getIsbn());
                                }
                                ConsoleUi.fieldPrompt("Select number (1-" + availableBooks.size() + ")");
                                int selectedIndex = scn.nextInt();
                                scn.nextLine();
                                if (selectedIndex < 1 || selectedIndex > availableBooks.size()) {
                                    ConsoleUi.warn("Invalid selection.");
                                    break;
                                }
                                Book selectedBook = availableBooks.get(selectedIndex - 1);
                                Database.borrowBookByPatronEmailAndIsbn(patronEmail, selectedBook.getIsbn());
                                break;
                            case 2:
                                ConsoleUi.fieldPrompt("Title keywords");
                                String titleQ = scn.nextLine();
                                List<Book> titleMatches = Database.findAvailableBooksByTitleLike(titleQ);
                                if (titleMatches.isEmpty()) {
                                    ConsoleUi.info("No available books matched that title search.");
                                    break;
                                }
                                if (titleMatches.size() == 1) {
                                    Database.borrowBookByPatronEmailAndIsbn(patronEmail, titleMatches.get(0).getIsbn());
                                    break;
                                }
                                ConsoleUi.sectionTitle("Pick a book");
                                for (int i = 0; i < titleMatches.size(); i++) {
                                    Book b = titleMatches.get(i);
                                    ConsoleUi.numberedLine(i + 1, b.getTitle() + " | ISBN: " + b.getIsbn());
                                }
                                ConsoleUi.fieldPrompt("Select number (1-" + titleMatches.size() + ")");
                                int ti = scn.nextInt();
                                scn.nextLine();
                                if (ti < 1 || ti > titleMatches.size()) {
                                    ConsoleUi.warn("Invalid selection.");
                                    break;
                                }
                                Database.borrowBookByPatronEmailAndIsbn(patronEmail, titleMatches.get(ti - 1).getIsbn());
                                break;
                            case 3:
                                ConsoleUi.fieldPrompt("Book ISBN");
                                Database.borrowBookByPatronEmailAndIsbn(patronEmail, scn.nextLine());
                                break;
                            default:
                                ConsoleUi.warn("Invalid borrow method.");
                        }
                        pressEnterToContinue(scn);
                        break;
                    case 5:
                        ConsoleUi.info("Returning as: " + UserSession.getDisplayName()
                                + " (" + UserSession.getMemberType().toDisplayString() + ") <" + patronEmail + ">");
                        List<ActiveBookLoan> borrowed = Database.getActiveBookLoansForPatron(patronEmail);
                        if (borrowed.isEmpty()) {
                            ConsoleUi.info("You have no borrowed books to return.");
                            pressEnterToContinue(scn);
                            break;
                        }
                        ConsoleUi.menuStart("Return a book");
                        ConsoleUi.menuOption(1, "Choose from my borrowed books");
                        ConsoleUi.menuOption(2, "Enter ISBN (optional)");
                        ConsoleUi.menuEnd();
                        int retMethod = scn.nextInt();
                        scn.nextLine();
                        switch (retMethod) {
                            case 1:
                                ConsoleUi.sectionTitle("Books you have borrowed");
                                for (int i = 0; i < borrowed.size(); i++) {
                                    ActiveBookLoan row = borrowed.get(i);
                                    ConsoleUi.numberedLine(i + 1, row.title + " | ISBN: " + row.isbn
                                            + " | Borrowed " + ConsoleUi.formatDate(row.signedOutDate)
                                            + " | Due " + ConsoleUi.formatDate(row.dueDate));
                                }
                                ConsoleUi.fieldPrompt("Book number (1-" + borrowed.size() + "), or 0 to cancel");
                                int pick = scn.nextInt();
                                scn.nextLine();
                                if (pick == 0) {
                                    break;
                                }
                                if (pick < 1 || pick > borrowed.size()) {
                                    ConsoleUi.warn("Invalid selection.");
                                    break;
                                }
                                Database.returnBookForPatronByItemId(patronEmail, borrowed.get(pick - 1).itemId);
                                break;
                            case 2:
                                ConsoleUi.fieldPrompt("Book ISBN");
                                Database.returnBookByPatronEmailAndIsbn(patronEmail, scn.nextLine());
                                break;
                            default:
                                ConsoleUi.warn("Invalid choice.");
                        }
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

    // -------------------------------------------------------------------------
    // 8. Portal option 6 — E-book reader
    // -------------------------------------------------------------------------

    private static void showEbookReaderPage(Scanner scn) {
        List<DigitalResource> list = DigitalResource.fetchAllFromDatabase();
        if (list.isEmpty()) {
            ConsoleUi.warn("No digital titles in the catalog.");
            pressEnterToContinue(scn);
            return;
        }
        ConsoleUi.sectionTitle("E-book reader");
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

    // -------------------------------------------------------------------------
    // 9. Portal option 7 — Study room booking
    // -------------------------------------------------------------------------

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
                        if (!UserSession.isLoggedIn()) {
                            ConsoleUi.warn("Sign in from the main menu first.");
                            pressEnterToContinue(scn);
                            break;
                        }
                        List<StudyRoomRow> rooms = Database.getStudyRoomsList();
                        if (rooms.isEmpty()) {
                            ConsoleUi.warn("No study rooms are set up yet.");
                            pressEnterToContinue(scn);
                            break;
                        }
                        ConsoleUi.sectionTitle("Choose a room");
                        for (int i = 0; i < rooms.size(); i++) {
                            StudyRoomRow r = rooms.get(i);
                            ConsoleUi.numberedLine(i + 1, r.name + " | seats " + r.capacity);
                        }
                        ConsoleUi.fieldPrompt("Room number (1-" + rooms.size() + ")");
                        int roomPick = scn.nextInt();
                        scn.nextLine();
                        if (roomPick < 1 || roomPick > rooms.size()) {
                            ConsoleUi.warn("Invalid room number.");
                            pressEnterToContinue(scn);
                            break;
                        }
                        int roomId = rooms.get(roomPick - 1).id;
                        ConsoleUi.fieldPrompt("Date (dd/mm/yyyy)");
                        LocalDate bookingDay = Database.parseFlexibleDate(scn.nextLine());
                        if (bookingDay == null) {
                            ConsoleUi.warn("Use dd/mm/yyyy, for example 17/04/2026.");
                            pressEnterToContinue(scn);
                            break;
                        }
                        Database.printStudySlotsForRoom(roomId, bookingDay);
                        ConsoleUi.fieldPrompt("Slot number (1-" + Database.STUDY_TIME_SLOTS.length + ")");
                        int sn = scn.nextInt();
                        scn.nextLine();
                        if (sn < 1 || sn > Database.STUDY_TIME_SLOTS.length) {
                            ConsoleUi.warn("Invalid slot number.");
                        } else {
                            Database.bookStudyRoom(roomId, UserSession.getEmail(), bookingDay,
                                    Database.STUDY_TIME_SLOTS[sn - 1]);
                        }
                        pressEnterToContinue(scn);
                        break;
                    case 2:
                        if (!UserSession.isLoggedIn()) {
                            ConsoleUi.warn("Sign in from the main menu first.");
                            pressEnterToContinue(scn);
                            break;
                        }
                        Database.printStudyBookingsForPatron(UserSession.getEmail());
                        pressEnterToContinue(scn);
                        break;
                    case 3:
                        if (!UserSession.isLoggedIn()) {
                            ConsoleUi.warn("Sign in from the main menu first.");
                            pressEnterToContinue(scn);
                            break;
                        }
                        List<StudyBookingRow> bookings = Database.getStudyBookingsForPatron(UserSession.getEmail());
                        if (bookings.isEmpty()) {
                            ConsoleUi.info("You have no study room bookings to cancel.");
                            pressEnterToContinue(scn);
                            break;
                        }
                        ConsoleUi.sectionTitle("Your bookings — choose one to cancel");
                        for (int i = 0; i < bookings.size(); i++) {
                            StudyBookingRow b = bookings.get(i);
                            ConsoleUi.numberedLine(i + 1, b.roomName + " | " + ConsoleUi.formatDate(b.bookingDate)
                                    + " " + b.slotLabel);
                        }
                        ConsoleUi.fieldPrompt("Number to cancel (1-" + bookings.size() + "), or 0 to go back");
                        int cancelPick = scn.nextInt();
                        scn.nextLine();
                        if (cancelPick == 0) {
                            break;
                        }
                        if (cancelPick < 1 || cancelPick > bookings.size()) {
                            ConsoleUi.warn("Invalid selection.");
                        } else {
                            Database.cancelStudyBooking(bookings.get(cancelPick - 1).id, UserSession.getEmail());
                        }
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

    // -------------------------------------------------------------------------
    // 10. Main menu branch — Librarian portal (after staff login)
    // -------------------------------------------------------------------------

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
            ConsoleUi.menuOption(7, "View registered patrons");
            ConsoleUi.menuOption(8, "Back");
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
                        showPatronsPage(scn);
                        break;
                    case 8:
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

    // -------------------------------------------------------------------------
    // 11. Librarian portal option 7 — Patron list (staff only)
    // -------------------------------------------------------------------------

    private static void showPatronsPage(Scanner scn) {
        ConsoleUi.sectionTitle("Registered patrons");
        for (Patron p : Patron.fetchAllFromDatabase()) {
            ConsoleUi.listItem(p.getName() + " (" + p.getPatronType().toDisplayString() + ")");
        }
        pressEnterToContinue(scn);
    }
}
