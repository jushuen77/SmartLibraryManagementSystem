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
 * Small helpers (status text, ISBN prompt, librarian add-book) live at the bottom of this file.
 */

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import data.ConsoleUi;
import data.Database;
import data.InputReaders;
import data.InputValidation;
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
    // -------------------------------------------------------------------------
    // Small helpers (used everywhere)
    // -------------------------------------------------------------------------

    private static final int EBOOK_READER_LINES_PER_PAGE = 14;

    // -------------------------------------------------------------------------
    // 1. Program entry
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        Database.createTable();
        ConsoleUi.welcomeBanner();

        Scanner scn = new Scanner(System.in);
        boolean running = true;
        int mainMenuVisits = 0;

        while (running) {
            if (mainMenuVisits++ > 0) {
                ConsoleUi.clearScreen();
            }
            ConsoleUi.menuStart("Main Menu");
            ConsoleUi.menuOption(1, "Login as Member");
            ConsoleUi.menuOption(2, "Register as Member");
            ConsoleUi.menuOption(3, "Login as Librarian Staff");
            ConsoleUi.menuOption(4, "Exit");
            ConsoleUi.menuEnd();

            int choice = InputReaders.readMenuInt(scn, 1, 4);

            switch (choice) {
                case 1:
                    if (UserSession.isLoggedIn()) {
                        ConsoleUi.sectionTitle("Library Member Login");
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
                        ConsoleUi.pauseThenClear(scn);
                    }
                    break;
                case 4:
                    running = false;
                    UserSession.clear();
                    ConsoleUi.blank();
                    ConsoleUi.ok("Goodbye. See you at the library.");
                    break;
                default:
                    break;
            }
        }
        scn.close();
    }

    // -------------------------------------------------------------------------
    // 2. Library portal (main menu → member login → this loop)
    // Menu order: books, journals, digital, profile, catalog, e-reader, study rooms, log out, back.
    // -------------------------------------------------------------------------

    private static void runLibraryPortal(Scanner scn) {
        boolean showSignedInBanner = true;
        boolean inLibraryMenu = true;
        while (inLibraryMenu && UserSession.isLoggedIn()) {
            ConsoleUi.clearScreen();
            if (showSignedInBanner) {
                ConsoleUi.ok("Signed in as " + UserSession.getDisplayName()
                        + " (" + UserSession.getMemberType().toDisplayString() + ") <"
                        + UserSession.getEmail() + ">.");
                ConsoleUi.blank();
                showSignedInBanner = false;
            }
            ConsoleUi.menuStart("Library Portal");
            ConsoleUi.menuOption(1, "View Books");
            ConsoleUi.menuOption(2, "View Journals");
            ConsoleUi.menuOption(3, "View Digital Resources");
            ConsoleUi.menuOption(4, "My Profile");
            ConsoleUi.menuOption(5, "Catalog");
            ConsoleUi.menuOption(6, "E-book Reader");
            ConsoleUi.menuOption(7, "Study Room Booking");
            ConsoleUi.menuOption(8, "Log Out");
            ConsoleUi.menuOption(9, "Back to Main Menu");
            ConsoleUi.menuEnd();
            int libraryChoice = InputReaders.readMenuInt(scn, 1, 9);
            switch (libraryChoice) {
                case 9:
                    inLibraryMenu = false;
                    break;
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
                default:
                    break;
            }
        }
    }

    // -------------------------------------------------------------------------
    // 3. Portal option 4 — My profile (Patron record for signed-in email)
    // -------------------------------------------------------------------------

    private static void showUserProfilePage(Scanner scn) {
        if (!UserSession.isLoggedIn()) {
            ConsoleUi.warn("Sign in as a library member from the main menu (option 1) before opening your profile.");
            ConsoleUi.pauseThenClear(scn);
            return;
        }
        Patron profile = Database.getPatronByEmail(UserSession.getEmail());
        ConsoleUi.clearScreen();
        ConsoleUi.sectionTitle("My Profile");
        if (profile == null) {
            ConsoleUi.warn("No library member profile is linked to this sign-in. Ask staff to link your account, or register again.");
            ConsoleUi.pauseThenClear(scn);
            return;
        }
        ConsoleUi.muted("Sign-in Name: " + UserSession.getDisplayName());
        for (String line : profile.profileLines()) {
            ConsoleUi.listItem(line);
        }
        List<ActiveBookLoan> borrowed = Database.getActiveBookLoansForPatron(UserSession.getEmail());
        ConsoleUi.blank();
        ConsoleUi.muted("Books Borrowed Currently: " + borrowed.size());
        if (borrowed.isEmpty()) {
            ConsoleUi.muted("(You have no borrowed books.)");
        } else {
            for (ActiveBookLoan row : borrowed) {
                ConsoleUi.listItem(row.title + " | due " + ConsoleUi.formatDate(row.dueDate));
            }
        }
        ConsoleUi.pauseThenClear(scn);
    }

    // -------------------------------------------------------------------------
    // 4. Portal option 1 — View books
    // -------------------------------------------------------------------------

    private static void showBooksPage(Scanner scn) {
        ConsoleUi.clearScreen();
        ConsoleUi.sectionTitle("Books");
        for (Book b : Book.fetchAllFromDatabase()) {
            ConsoleUi.listItem(b.getTitle() + " by " + b.getAuthor());
        }
        ConsoleUi.pauseThenClear(scn);
    }

    // -------------------------------------------------------------------------
    // 5. Portal option 2 — View journals
    // -------------------------------------------------------------------------

    private static void showJournalsPage(Scanner scn) {
        ConsoleUi.clearScreen();
        ConsoleUi.sectionTitle("Journals");
        for (Journal j : Journal.fetchAllFromDatabase()) {
            ConsoleUi.listItem(j.getTitle() + " by " + j.getAuthor());
        }
        ConsoleUi.pauseThenClear(scn);
    }

    // -------------------------------------------------------------------------
    // 6. Portal option 3 — View digital resources
    // -------------------------------------------------------------------------

    private static void showDigitalResourcesPage(Scanner scn) {
        ConsoleUi.clearScreen();
        ConsoleUi.sectionTitle("Digital Resources");
        for (DigitalResource d : DigitalResource.fetchAllFromDatabase()) {
            ConsoleUi.listItem(d.getTitle() + " by " + d.getAuthor());
        }
        ConsoleUi.pauseThenClear(scn);
    }

    // -------------------------------------------------------------------------
    // 7. Portal option 5 — Catalog (browse / search / borrow / return)
    // -------------------------------------------------------------------------

    private static void showCatalogSystemPage(Scanner scn) {
        if (!UserSession.isLoggedIn()) {
            ConsoleUi.warn("Sign in as a library member from the main menu (option 1) before using the catalog.");
            ConsoleUi.pauseThenClear(scn);
            return;
        }
        final String patronEmail = UserSession.getEmail();
        boolean inCatalogMenu = true;
        while (inCatalogMenu) {
            ConsoleUi.clearScreen();
            ConsoleUi.menuStart("Catalog");
            ConsoleUi.menuOption(1, "Browse by Genre");
            ConsoleUi.menuOption(2, "Browse by Author");
            ConsoleUi.menuOption(3, "Search book Availability");
            ConsoleUi.menuOption(4, "Borrow a Book");
            ConsoleUi.menuOption(5, "Return a Book");
            ConsoleUi.menuOption(6, "Back to Library Portal");
            ConsoleUi.menuEnd();

            int choice = InputReaders.readMenuInt(scn, 1, 6);
            switch (choice) {
                    case 1:
                        ConsoleUi.sectionTitle("By Genre");
                        for (Map.Entry<String, List<LibraryItem>> entry : CatalogSystem.organizeByGenre().entrySet()) {
                            ConsoleUi.muted(entry.getKey());
                            for (LibraryItem item : entry.getValue()) {
                                ConsoleUi.listItem(item.getTitle() + " | " + item.getAuthor() + " | ISBN: " + item.getIsbn());
                            }
                            ConsoleUi.blank();
                        }
                        ConsoleUi.pauseThenClear(scn);
                        break;
                    case 2:
                        ConsoleUi.sectionTitle("By Author");
                        for (Map.Entry<String, List<LibraryItem>> entry : CatalogSystem.organizeByAuthor().entrySet()) {
                            ConsoleUi.muted(entry.getKey());
                            for (LibraryItem item : entry.getValue()) {
                                ConsoleUi.listItem(item.getTitle() + " | " + item.getGenre() + " | ISBN: " + item.getIsbn());
                            }
                            ConsoleUi.blank();
                        }
                        ConsoleUi.pauseThenClear(scn);
                        break;
                    case 3:
                        ConsoleUi.fieldPrompt("Book Title or ISBN (0 = cancel)");
                        String query = scn.nextLine().trim();
                        if ("0".equals(query)) {
                            ConsoleUi.info("Search cancelled.");
                            ConsoleUi.pauseThenClear(scn);
                            break;
                        }
                        List<LibraryItem> matches = CatalogSystem.searchBooks(query);
                        if (matches.isEmpty()) {
                            ConsoleUi.info("No books matched that search.");
                        } else {
                            ConsoleUi.sectionTitle("Matching Books");
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
                                ConsoleUi.fieldPrompt("Reserve a book that is on loan? (y/n, 0 = no)");
                                String reserveChoice = scn.nextLine().trim();
                                if (reserveChoice.equalsIgnoreCase("y")) {
                                    ConsoleUi.fieldPrompt("ISBN to reserve (blank to cancel)");
                                    String reserveRaw = scn.nextLine();
                                    if (reserveRaw.isBlank()) {
                                        ConsoleUi.info("Reservation cancelled.");
                                    } else {
                                        String isbnErr = InputValidation.validateIsbn(reserveRaw);
                                        if (isbnErr != null) {
                                            ConsoleUi.warn(isbnErr);
                                        } else {
                                            String reserveIsbn = InputValidation.normalizeIsbn(reserveRaw);
                                            LibraryItem selected = CatalogSystem.findByIsbn(reserveIsbn);

                                            if (selected == null || !"BOOK".equals(selected.getSqlItemType())) {
                                                ConsoleUi.warn("No book in the catalog matches that ISBN.");
                                            } else if (!"ON_LOAN".equals(selected.getStatus().name())) {
                                                ConsoleUi.info("That book is not on loan; no reservation needed.");
                                            } else {
                                                String requesterName = UserSession.getDisplayName();
                                                if (requesterName == null || requesterName.isBlank()) {
                                                    requesterName = "Patron";
                                                }
                                                String requesterEmail = UserSession.getEmail();
                                                if (!Database.isValidEmail(requesterEmail)) {
                                                    ConsoleUi.warn("Your profile email is not valid for reservations. Update your account or contact staff.");
                                                } else {
                                                    Database.addBookReservation(
                                                            new BookReservation(selected.getId(), requesterName, requesterEmail));
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        ConsoleUi.pauseThenClear(scn);
                        break;
                    case 4:
                        ConsoleUi.info("Signed in as: " + UserSession.getDisplayName()
                                + " (" + UserSession.getMemberType().toDisplayString() + ") <" + patronEmail + ">");
                        ConsoleUi.menuStart("Borrow by");
                        ConsoleUi.menuOption(1, "Pick from Available Books");
                        ConsoleUi.menuOption(2, "Search by Title Keywords");
                        ConsoleUi.menuOption(3, "Enter ISBN");
                        ConsoleUi.menuOption(4, "Back to Catalog");
                        ConsoleUi.menuEnd();
                        int borrowMethod = InputReaders.readMenuInt(scn, 1, 4);
                        switch (borrowMethod) {
                            case 4:
                                break;
                            case 1:
                                List<Book> availableBooks = Database.getAvailableBooks();
                                if (availableBooks.isEmpty()) {
                                    ConsoleUi.info("No available books right now.");
                                    break;
                                }
                                ConsoleUi.sectionTitle("Available Books");
                                for (int i = 0; i < availableBooks.size(); i++) {
                                    Book b = availableBooks.get(i);
                                    ConsoleUi.numberedLine(i + 1, b.getTitle() + " | ISBN: " + b.getIsbn());
                                }
                                ConsoleUi.fieldPrompt("Select number (1-" + availableBooks.size() + ", 0 = cancel)");
                                Integer pickBook = InputReaders.readIntInRangeOrZeroCancel(scn, 1, availableBooks.size(),
                                        "Select number (1-" + availableBooks.size() + ", 0 = cancel)");
                                if (pickBook == null) {
                                    ConsoleUi.info("Borrow cancelled.");
                                    break;
                                }
                                Book selectedBook = availableBooks.get(pickBook - 1);
                                Database.borrowBookByPatronEmailAndIsbn(patronEmail, selectedBook.getIsbn());
                                break;
                            case 2:
                                ConsoleUi.fieldPrompt("Title keywords (0 = cancel)");
                                String titleQ = scn.nextLine().trim();
                                if ("0".equals(titleQ)) {
                                    ConsoleUi.info("Borrow cancelled.");
                                    break;
                                }
                                List<Book> titleMatches = Database.findAvailableBooksByTitleLike(titleQ);
                                if (titleMatches.isEmpty()) {
                                    ConsoleUi.info("No available books matched that title search.");
                                    break;
                                }
                                if (titleMatches.size() == 1) {
                                    Database.borrowBookByPatronEmailAndIsbn(patronEmail, titleMatches.get(0).getIsbn());
                                    break;
                                }
                                ConsoleUi.sectionTitle("Pick a Book");
                                for (int i = 0; i < titleMatches.size(); i++) {
                                    Book b = titleMatches.get(i);
                                    ConsoleUi.numberedLine(i + 1, b.getTitle() + " | ISBN: " + b.getIsbn());
                                }
                                ConsoleUi.fieldPrompt("Select number (1-" + titleMatches.size() + ", 0 = cancel)");
                                Integer tiBoxed = InputReaders.readIntInRangeOrZeroCancel(scn, 1, titleMatches.size(),
                                        "Select number (1-" + titleMatches.size() + ", 0 = cancel)");
                                if (tiBoxed == null) {
                                    ConsoleUi.info("Borrow cancelled.");
                                    break;
                                }
                                Database.borrowBookByPatronEmailAndIsbn(patronEmail, titleMatches.get(tiBoxed - 1).getIsbn());
                                break;
                            case 3:
                                String borrowIsbn = promptValidIsbnOrCancel(scn, "Book ISBN");
                                if (borrowIsbn == null) {
                                    ConsoleUi.info("Borrow cancelled.");
                                    break;
                                }
                                Database.borrowBookByPatronEmailAndIsbn(patronEmail, borrowIsbn);
                                break;
                            default:
                                ConsoleUi.warn("That option is not valid here. Choose 1–4 from the borrow menu.");
                        }
                        ConsoleUi.pauseThenClear(scn);
                        break;
                    case 5:
                        ConsoleUi.info("Returning as: " + UserSession.getDisplayName()
                                + " (" + UserSession.getMemberType().toDisplayString() + ") <" + patronEmail + ">");
                        List<ActiveBookLoan> borrowed = Database.getActiveBookLoansForPatron(patronEmail);
                        if (borrowed.isEmpty()) {
                            ConsoleUi.info("You have no borrowed books to return.");
                            ConsoleUi.pauseThenClear(scn);
                            break;
                        }
                        ConsoleUi.menuStart("Return a Book");
                        ConsoleUi.menuOption(1, "Choose from My Borrowed Books");
                        ConsoleUi.menuOption(2, "Enter ISBN");
                        ConsoleUi.menuOption(3, "Back to Catalog");
                        ConsoleUi.menuEnd();
                        int retMethod = InputReaders.readMenuInt(scn, 1, 3);
                        switch (retMethod) {
                            case 3:
                                break;
                            case 1:
                                ConsoleUi.sectionTitle("Books You have Borrowed");
                                for (int i = 0; i < borrowed.size(); i++) {
                                    ActiveBookLoan row = borrowed.get(i);
                                    ConsoleUi.numberedLine(i + 1, row.title + " | ISBN: " + row.isbn
                                            + " | Borrowed " + ConsoleUi.formatDate(row.signedOutDate)
                                            + " | Due " + ConsoleUi.formatDate(row.dueDate));
                                }
                                ConsoleUi.fieldPrompt("Book number (1-" + borrowed.size() + "), or 0 to cancel");
                                int pick = InputReaders.readIntInRange(scn, 0, borrowed.size(),
                                        "Book number (1-" + borrowed.size() + "), or 0 to cancel");
                                if (pick == 0) {
                                    break;
                                }
                                Database.returnBookForPatronByItemId(patronEmail, borrowed.get(pick - 1).itemId);
                                break;
                            case 2:
                                String returnIsbn = promptValidIsbnOrCancel(scn, "Book ISBN");
                                if (returnIsbn == null) {
                                    ConsoleUi.info("Return cancelled.");
                                    break;
                                }
                                Database.returnBookByPatronEmailAndIsbn(patronEmail, returnIsbn);
                                break;
                            default:
                                ConsoleUi.warn("That option is not valid here. Choose 1–3 from the return menu.");
                        }
                        ConsoleUi.pauseThenClear(scn);
                        break;
                    case 6:
                        inCatalogMenu = false;
                        break;
                    default:
                        break;
                }
        }
    }

    // -------------------------------------------------------------------------
    // 8. Portal option 6 — E-book reader
    // -------------------------------------------------------------------------

    private static void showEbookReaderPage(Scanner scn) {
        List<DigitalResource> list = DigitalResource.fetchAllFromDatabase();
        if (list.isEmpty()) {
            ConsoleUi.warn("There are no digital titles in the catalog yet.");
            ConsoleUi.pauseThenClear(scn);
            return;
        }
        ConsoleUi.clearScreen();
        ConsoleUi.sectionTitle("E-book Reader");
        for (int i = 0; i < list.size(); i++) {
            DigitalResource d = list.get(i);
            boolean hasText = d.getReaderContent() != null && !d.getReaderContent().isBlank();
            ConsoleUi.numberedLine(i + 1, d.getTitle() + " | ISBN: " + d.getIsbn()
                    + (hasText ? "" : "  (no reader text yet)"));
        }
        int backChoice = list.size() + 1;
        ConsoleUi.numberedLine(backChoice, "Back to Library Portal");
        ConsoleUi.fieldPrompt("Open title # (1-" + list.size() + ") or " + backChoice + " to go back");
        int pick = InputReaders.readIntInRange(scn, 1, backChoice,
                "Open title # (1-" + list.size() + ") or " + backChoice + " to go back");
        if (pick == backChoice) {
            ConsoleUi.clearScreen();
            return;
        }
        try {
            DigitalResource book = list.get(pick - 1);
            String content = book.getReaderContent();
            if (content == null || content.isBlank()) {
                ConsoleUi.info("This title has no reader text loaded. Ask staff to attach an excerpt.");
                ConsoleUi.pauseThenClear(scn);
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
                    ConsoleUi.clearScreen();
                } else if (cmd.equals("n") && page < totalPages - 1) {
                    page++;
                } else if (cmd.equals("p") && page > 0) {
                    page--;
                }
            }
        } catch (Exception e) {
            ConsoleUi.warn("Something went wrong opening that title. Try another book or contact staff if it keeps happening.");
            ConsoleUi.pauseThenClear(scn);
        }
    }

    // -------------------------------------------------------------------------
    // 9. Portal option 7 — Study room booking
    // -------------------------------------------------------------------------

    private static void showStudyRoomBookingPage(Scanner scn) {
        boolean sub = true;
        while (sub) {
            ConsoleUi.clearScreen();
            ConsoleUi.menuStart("Study Room Booking");
            ConsoleUi.menuOption(1, "List Rooms & Book a Slot");
            ConsoleUi.menuOption(2, "My Bookings");
            ConsoleUi.menuOption(3, "Cancel a Booking");
            ConsoleUi.menuOption(4, "Back to Library Portal");
            ConsoleUi.menuEnd();
            int c = InputReaders.readMenuInt(scn, 1, 4);
            switch (c) {
                    case 1:
                        if (!UserSession.isLoggedIn()) {
                            ConsoleUi.warn("Sign in as a library member from the main menu (option 1) before booking a study room.");
                            ConsoleUi.pauseThenClear(scn);
                            break;
                        }
                        List<StudyRoomRow> rooms = Database.getStudyRoomsList();
                        if (rooms.isEmpty()) {
                            ConsoleUi.warn("No study rooms are configured in the system yet. Ask staff to add rooms.");
                            ConsoleUi.pauseThenClear(scn);
                            break;
                        }
                        ConsoleUi.sectionTitle("Choose a Room");
                        for (int i = 0; i < rooms.size(); i++) {
                            StudyRoomRow r = rooms.get(i);
                            ConsoleUi.numberedLine(i + 1, r.name + " | seats " + r.capacity);
                        }
                        ConsoleUi.fieldPrompt("Room Number (1-" + rooms.size() + ", 0 = cancel)");
                        Integer roomPickBoxed = InputReaders.readIntInRangeOrZeroCancel(scn, 1, rooms.size(),
                                "Room number (1-" + rooms.size() + ", 0 = cancel)");
                        if (roomPickBoxed == null) {
                            ConsoleUi.info("Booking cancelled.");
                            ConsoleUi.pauseThenClear(scn);
                            break;
                        }
                        int roomId = rooms.get(roomPickBoxed - 1).id;
                        ConsoleUi.fieldPrompt("Date (dd/mm/yyyy, 0 or cancel = abort)");
                        String dateLine = scn.nextLine().trim();
                        if ("0".equals(dateLine) || "cancel".equalsIgnoreCase(dateLine)) {
                            ConsoleUi.info("Booking cancelled.");
                            ConsoleUi.pauseThenClear(scn);
                            break;
                        }
                        LocalDate bookingDay = Database.parseFlexibleDate(dateLine);
                        if (bookingDay == null) {
                            ConsoleUi.warn("That date was not understood. Use day/month/year with slashes, for example 17/04/2026.");
                            ConsoleUi.pauseThenClear(scn);
                            break;
                        }
                        Database.printStudySlotsForRoom(roomId, bookingDay);
                        ConsoleUi.fieldPrompt("Slot number (1-" + Database.STUDY_TIME_SLOTS.length + ", 0 = cancel)");
                        Integer snBoxed = InputReaders.readIntInRangeOrZeroCancel(scn, 1, Database.STUDY_TIME_SLOTS.length,
                                "Slot number (1-" + Database.STUDY_TIME_SLOTS.length + ", 0 = cancel)");
                        if (snBoxed == null) {
                            ConsoleUi.info("Booking cancelled.");
                            ConsoleUi.pauseThenClear(scn);
                            break;
                        }
                        int sn = snBoxed;
                        Database.bookStudyRoom(roomId, UserSession.getEmail(), bookingDay,
                                Database.STUDY_TIME_SLOTS[sn - 1]);
                        ConsoleUi.pauseThenClear(scn);
                        break;
                    case 2:
                        if (!UserSession.isLoggedIn()) {
                            ConsoleUi.warn("Sign in as a library member from the main menu (option 1) to view your bookings.");
                            ConsoleUi.pauseThenClear(scn);
                            break;
                        }
                        Database.printStudyBookingsForPatron(UserSession.getEmail());
                        ConsoleUi.pauseThenClear(scn);
                        break;
                    case 3:
                        if (!UserSession.isLoggedIn()) {
                            ConsoleUi.warn("Sign in as a library member from the main menu (option 1) to cancel a booking.");
                            ConsoleUi.pauseThenClear(scn);
                            break;
                        }
                        List<StudyBookingRow> bookings = Database.getStudyBookingsForPatron(UserSession.getEmail());
                        if (bookings.isEmpty()) {
                            ConsoleUi.info("You have no study room bookings to cancel.");
                            ConsoleUi.pauseThenClear(scn);
                            break;
                        }
                        ConsoleUi.sectionTitle("Your Bookings — Choose One to Cancel");
                        for (int i = 0; i < bookings.size(); i++) {
                            StudyBookingRow b = bookings.get(i);
                            ConsoleUi.numberedLine(i + 1, b.roomName + " | " + ConsoleUi.formatDate(b.bookingDate)
                                    + " " + b.slotLabel);
                        }
                        ConsoleUi.fieldPrompt("Number to Cancel (1-" + bookings.size() + "), or 0 to go back");
                        int cancelPick = InputReaders.readIntInRange(scn, 0, bookings.size(),
                                "Number to cancel (1-" + bookings.size() + "), or 0 to go back");
                        if (cancelPick == 0) {
                            break;
                        }
                        Database.cancelStudyBooking(bookings.get(cancelPick - 1).id, UserSession.getEmail());
                        ConsoleUi.pauseThenClear(scn);
                        break;
                    case 4:
                        sub = false;
                        break;
                    default:
                        ConsoleUi.warn("That number is not on the menu. Choose 1–4.");
                        ConsoleUi.pauseThenClear(scn);
                }
        }
    }

    // -------------------------------------------------------------------------
    // 10. Main menu branch — Librarian portal (after staff login)
    // -------------------------------------------------------------------------

    private static void showLibrarianPortal(Scanner scn) {
        boolean showStaffBanner = true;
        boolean inStaffMenu = true;
        while (inStaffMenu) {
            ConsoleUi.clearScreen();
            if (showStaffBanner) {
                ConsoleUi.ok("Librarian Portal (Staff Mode).");
                ConsoleUi.blank();
                showStaffBanner = false;
            }
            ConsoleUi.menuStart("Librarian Portal");
            ConsoleUi.menuOption(1, "Add New Book Acquisition");
            ConsoleUi.menuOption(2, "Mark Lost / Damanged Books (ISBN)");
            ConsoleUi.menuOption(3, "Audit Inventory");
            ConsoleUi.menuOption(4, "List Lost Books");
            ConsoleUi.menuOption(5, "View Outstanding Fines");
            ConsoleUi.menuOption(6, "Record Fine Payment");
            ConsoleUi.menuOption(7, "View Registered Patrons");
            ConsoleUi.menuOption(8, "Back to Main Menu");
            ConsoleUi.menuEnd();
            int choice = InputReaders.readMenuInt(scn, 1, 8);
            switch (choice) {
                    case 1:
                        runLibrarianAddBook(scn);
                        ConsoleUi.pauseThenClear(scn);
                        break;
                    case 2:
                        String damagedIsbn = promptValidIsbnOrCancel(scn, "ISBN to mark as lost or damaged");
                        if (damagedIsbn != null) {
                            Database.removeDamagedBookByIsbn(damagedIsbn);
                        } else {
                            ConsoleUi.info("Cancelled.");
                        }
                        ConsoleUi.pauseThenClear(scn);
                        break;
                    case 3:
                        Database.printInventoryAudit();
                        ConsoleUi.pauseThenClear(scn);
                        break;
                    case 4:
                        Database.printLostBooks();
                        ConsoleUi.pauseThenClear(scn);
                        break;
                    case 5:
                        Database.printOutstandingFines();
                        ConsoleUi.pauseThenClear(scn);
                        break;
                    case 6:
                        ConsoleUi.fieldPrompt("Fine ID (1-" + 99_999_999 + ", 0 = cancel)");
                        Integer fineIdBoxed = InputReaders.readIntInRangeOrZeroCancel(scn, 1, 99_999_999,
                                "Fine ID (positive integer, 0 = cancel)");
                        if (fineIdBoxed == null) {
                            ConsoleUi.info("Fine payment cancelled.");
                            ConsoleUi.pauseThenClear(scn);
                            break;
                        }
                        int fineId = fineIdBoxed;
                        ConsoleUi.fieldPrompt("Payment amount ($, max 1,000,000; type cancel to abort)");
                        Double payAmountBoxed = InputReaders.readNonNegativeDoubleOrCancel(scn,
                                "Payment amount ($, max 1,000,000; type cancel to abort)", 1_000_000);
                        if (payAmountBoxed == null) {
                            ConsoleUi.info("Fine payment cancelled.");
                            ConsoleUi.pauseThenClear(scn);
                            break;
                        }
                        Database.recordFinePayment(fineId, payAmountBoxed);
                        ConsoleUi.pauseThenClear(scn);
                        break;
                    case 7:
                        showPatronsPage(scn);
                        break;
                    case 8:
                        inStaffMenu = false;
                        break;
                    default:
                        break;
                }
        }
    }

    // -------------------------------------------------------------------------
    // 11. Librarian portal option 7 — Patron list (staff only)
    // -------------------------------------------------------------------------

    private static void showPatronsPage(Scanner scn) {
        ConsoleUi.clearScreen();
        ConsoleUi.sectionTitle("Registered Patrons");
        for (Patron p : Patron.fetchAllFromDatabase()) {
            ConsoleUi.listItem(p.getName() + " (" + p.getPatronType().toDisplayString() + ")");
        }
        ConsoleUi.pauseThenClear(scn);
    }

    // -------------------------------------------------------------------------
    // 12. Extra last min added methods 
    // -------------------------------------------------------------------------

    // catalog / search screen: turn ugly enum text into normal words for user (ON_LOAN -> On Loan etc)
    private static String toUserStatus(LibraryItem item) {
        if ("ON_LOAN".equals(item.getStatus().name())) {
            return "On Loan";
        }
        if ("AVAILABLE".equals(item.getStatus().name())) {
            return "Available";
        }
        return item.getStatus().name();
    }

    // keep asking ISBN until valid checksum OR user type 0 / cancel to go back halfway
    private static String promptValidIsbnOrCancel(Scanner scn, String fieldLabel) {
        while (true) {
            ConsoleUi.fieldPrompt(fieldLabel + " (0 or cancel = go back)");
            String raw = scn.nextLine().trim();
            if ("0".equals(raw) || "cancel".equalsIgnoreCase(raw)) {
                return null;
            }
            String err = InputValidation.validateIsbn(raw);
            if (err == null) {
                return InputValidation.normalizeIsbn(raw);
            }
            ConsoleUi.warn(err);
        }
    }

    // librarian add new book into DB: step by step form, if wrong input show warn msg, 0 or cancel can bail out anytime
    private static void runLibrarianAddBook(Scanner scn) {
        // fresh screen + title bar so staff know what screen this is
        ConsoleUi.clearScreen();
        ConsoleUi.sectionTitle("New Book Acquisition");
        ConsoleUi.muted("Tip: 0 alone cancels at Title / Author / Genre / loan fields. ISBN: 0 or cancel. Daily fine: type cancel to abort (0.00 is a valid fine).");
        // title cannot be empty, 0 = cancel whole add book flow
        String title;
        while (true) {
            ConsoleUi.fieldPrompt("Title (0 = cancel)");
            title = scn.nextLine().trim();
            if ("0".equals(title)) {
                ConsoleUi.info("Add book cancelled.");
                return;
            }
            if (!title.isEmpty()) {
                break;
            }
            ConsoleUi.warn("The title cannot be blank. Enter the book title, or 0 to cancel.");
        }
        // author can skip -> we store Unknown; 0 still means cancel
        ConsoleUi.fieldPrompt("Author (0 = cancel)");
        String author = scn.nextLine().trim();
        if ("0".equals(author)) {
            ConsoleUi.info("Add book cancelled.");
            return;
        }
        if (author.isEmpty()) {
            author = "Unknown";
            ConsoleUi.info("Author was empty; saved as Unknown.");
        }
        // ISBN uses validation class, loop until ok or user give up
        String isbn = promptValidIsbnOrCancel(scn, "ISBN (ISBN-10 or ISBN-13, hyphens allowed)");
        if (isbn == null) {
            ConsoleUi.info("Add book cancelled.");
            return;
        }
        // genre same idea as title, must have something
        String genre;
        while (true) {
            ConsoleUi.fieldPrompt("Genre (0 = cancel)");
            genre = scn.nextLine().trim();
            if ("0".equals(genre)) {
                ConsoleUi.info("Add book cancelled.");
                return;
            }
            if (!genre.isEmpty()) {
                break;
            }
            ConsoleUi.warn("The genre cannot be blank. Enter a genre label, or 0 to cancel.");
        }
        // loan policy numbers: InputReaders handle empty / out of range, 0 = cancel this step
        ConsoleUi.fieldPrompt("Loan duration (days, 1-365, 0 = cancel)");
        Integer loanDaysBoxed = InputReaders.readIntInRangeOrZeroCancel(scn, 1, 365, "Loan duration (days, 1-365, 0 = cancel)");
        if (loanDaysBoxed == null) {
            ConsoleUi.info("Add book cancelled.");
            return;
        }
        int loanDays = loanDaysBoxed;
        ConsoleUi.fieldPrompt("Borrowing limit (1-50, 0 = cancel)");
        Integer borrowingLimitBoxed = InputReaders.readIntInRangeOrZeroCancel(scn, 1, 50, "Borrowing limit (1-50, 0 = cancel)");
        if (borrowingLimitBoxed == null) {
            ConsoleUi.info("Add book cancelled.");
            return;
        }
        int borrowingLimit = borrowingLimitBoxed;
        // daily fine: cancel word cuz 0.00 is legit fine, cant use 0 to abort here
        ConsoleUi.fieldPrompt("Daily fine ($, max 500; type cancel to abort)");
        Double dailyFineBoxed = InputReaders.readNonNegativeDoubleOrCancel(scn, "Daily fine ($, max 500; type cancel to abort)", 500);
        if (dailyFineBoxed == null) {
            ConsoleUi.info("Add book cancelled.");
            return;
        }
        double dailyFine = dailyFineBoxed;
        // all good -> push into sqlite via Database
        Database.addBookAcquisition(title, author, isbn, genre, loanDays, borrowingLimit, dailyFine);
    }
}
