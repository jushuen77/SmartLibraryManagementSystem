package data;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import library.SampleCatalog;
import library.model.Book;
import library.model.BookReservation;
import library.model.DigitalResource;
import library.model.ItemStatus;
import library.model.Journal;
import library.model.LibraryItem;
import library.model.Patron;
import library.model.PatronType;

/*
 * Central place for SQLite access: schema, seeding, CRUD, catalog loading, loans/fines, and study-room booking.
 * Rough order: setup → seed data → patrons/items → loaders → borrow/return/fines → study rooms.
 * If inserts fail at runtime, check foreign keys and UNIQUE constraints first.
 * (This class is long. Grab coffee before scrolling to the bottom. -- jushuen)
 */
public class Database {
    // users.db lives next to wherever you run java — if you lose it, you did not lose the code, only your fines history
    private static final String url = "jdbc:sqlite:users.db";

    // Opens the db file; PRAGMA foreign_keys = ON so SQLite actually enforces "this patron id exists, right?"
    private static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(url);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }

    
    //   Bootstraps every table and index. IF NOT EXISTS allows safe runs on every startup without losing previous data  
    public static void createTable() {
        // --- Who are you? (accounts + patrons) ---
        String usersTable = """
                CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                email TEXT NOT NULL UNIQUE,
                password TEXT NOT NULL
                );
                """;

        String patronsTable = """
                CREATE TABLE IF NOT EXISTS patrons (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                email TEXT NOT NULL UNIQUE,
                patron_type TEXT NOT NULL CHECK (patron_type IN ('STUDENT','FACULTY','PUBLIC'))
                );
                """;

        String itemsTable = """
                CREATE TABLE IF NOT EXISTS items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                item_type TEXT NOT NULL CHECK (item_type IN ('BOOK','JOURNAL','DIGITAL')),
                title TEXT NOT NULL,
                author TEXT,
                isbn TEXT,
                genre TEXT,
                status TEXT NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE','ON_LOAN','LOST')),
                loan_duration_days INTEGER NOT NULL DEFAULT 14,
                borrowing_limit INTEGER NOT NULL DEFAULT 5,
                daily_fine REAL NOT NULL DEFAULT 1.0
                );
                """;

        String loansTable = """
                CREATE TABLE IF NOT EXISTS loans (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                patron_id INTEGER NOT NULL REFERENCES patrons(id),
                item_id INTEGER NOT NULL REFERENCES items(id),
                issue_date TEXT NOT NULL,
                due_date TEXT NOT NULL,
                return_date TEXT
                );
                """;

        String reservationsTable = """
                CREATE TABLE IF NOT EXISTS book_reservations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                item_id INTEGER NOT NULL REFERENCES items(id),
                requester_name TEXT NOT NULL,
                requester_email TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','FULFILLED','CANCELLED')),
                request_date TEXT NOT NULL
                );
                """;

        String activeLoanIndex = """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_loans_one_active_per_item
                ON loans(item_id) WHERE return_date IS NULL;
                """;

        String activeReservationIndex = """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_reservation_one_pending_per_user_item
                ON book_reservations(item_id, requester_email) WHERE status = 'PENDING';
                """;

        String finesTable = """
                CREATE TABLE IF NOT EXISTS fines (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                patron_id INTEGER NOT NULL REFERENCES patrons(id),
                loan_id INTEGER NOT NULL REFERENCES loans(id),
                item_id INTEGER NOT NULL REFERENCES items(id),
                amount REAL NOT NULL,
                amount_paid REAL NOT NULL DEFAULT 0,
                recorded_date TEXT NOT NULL,
                description TEXT
                );
                """;

        String studyRoomsTable = """
                CREATE TABLE IF NOT EXISTS study_rooms (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                capacity INTEGER NOT NULL DEFAULT 4
                );
                """;

        String studyBookingsTable = """
                CREATE TABLE IF NOT EXISTS study_room_bookings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                room_id INTEGER NOT NULL REFERENCES study_rooms(id),
                patron_email TEXT NOT NULL,
                booking_date TEXT NOT NULL,
                slot_label TEXT NOT NULL,
                created_date TEXT NOT NULL,
                UNIQUE(room_id, booking_date, slot_label)
                );
                """;

        try (Connection conn = getConnection();//this is to get connection for the database then prompt error msg if unsuccessful
                Statement stmt = conn.createStatement()) {
            stmt.execute(usersTable);
            stmt.execute(patronsTable);
            stmt.execute(itemsTable);
            ensureItemsPolicyColumns(stmt);
            ensureItemsReaderContentColumn(stmt);
            stmt.execute(loansTable);
            stmt.execute(reservationsTable);
            stmt.execute(finesTable);
            stmt.execute(studyRoomsTable);
            stmt.execute(studyBookingsTable);
            stmt.execute(activeLoanIndex);
            stmt.execute(activeReservationIndex);
            seedSampleDataIfEmpty();
            syncPatronsFromUsersIfMissing();
            seedStudyRoomsIfEmpty();
            backfillDigitalReaderContentFromSamples();
        } catch (SQLException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("No suitable driver")) {
                ConsoleUi.reportHeader("Database setup");
                ConsoleUi.warn("SQLite JDBC driver not found.");
                ConsoleUi.info("Use VS Code launch config: Run App (with SQLite JDBC).");
                ConsoleUi.info("Or run with classpath including lib/sqlite-jdbc-3.51.1.0.jar.");
            } else {
                e.printStackTrace();
            }
        }
    }

    //constant study room booking time slots
    public static final String[] STUDY_TIME_SLOTS = {
            "09:00-10:00", "10:00-11:00", "11:00-12:00", "12:00-13:00",
            "13:00-14:00", "14:00-15:00", "15:00-16:00", "16:00-17:00"
    };

    //alter the table if chagnes are need to be made, something liek that
    private static void ensureItemsPolicyColumns(Statement stmt) throws SQLException {
        if (!hasColumn("items", "loan_duration_days")) {
            stmt.execute("ALTER TABLE items ADD COLUMN loan_duration_days INTEGER NOT NULL DEFAULT 14");
        }
        if (!hasColumn("items", "borrowing_limit")) {
            stmt.execute("ALTER TABLE items ADD COLUMN borrowing_limit INTEGER NOT NULL DEFAULT 5");
        }
        if (!hasColumn("items", "daily_fine")) {
            stmt.execute("ALTER TABLE items ADD COLUMN daily_fine REAL NOT NULL DEFAULT 1.0");
        }
    }

    private static void ensureItemsReaderContentColumn(Statement stmt) throws SQLException {
        if (!hasColumn("items", "reader_content")) {
            stmt.execute("ALTER TABLE items ADD COLUMN reader_content TEXT");
        }
    }

    //this method is to make sure that we dont alter sql twice by accident or some shit
    private static boolean hasColumn(String table, String column) throws SQLException {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement("PRAGMA table_info(" + table + ")");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static int countTable(String table) throws SQLException {
        if (!"patrons".equals(table) && !"items".equals(table)) {
            throw new IllegalArgumentException("Invalid table: " + table);
        }
        try (Connection conn = getConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

   
    public static void seedSampleDataIfEmpty() {
        try {
            if (countTable("patrons") == 0) {
                for (Patron p : SampleCatalog.samplePatrons()) {
                    savePatron(p);
                }
            }
            if (countTable("items") == 0) {
                for (Book b : SampleCatalog.sampleBooks()) {
                    saveItem(b);
                }
                for (Journal j : SampleCatalog.sampleJournals()) {
                    saveItem(j);
                }
                for (DigitalResource d : SampleCatalog.sampleDigitalResources()) {
                    saveItem(d);
                }
                ConsoleUi.muted("Sample catalog and patrons loaded.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    //to make sure user and patrons matches, if user's patron is missig will match for user
    public static void syncPatronsFromUsersIfMissing() {
        String sql = """
                INSERT INTO patrons(name, email, patron_type)
                SELECT u.name, u.email, 'PUBLIC' FROM users u
                WHERE NOT EXISTS (
                    SELECT 1 FROM patrons p WHERE LOWER(TRIM(p.email)) = LOWER(TRIM(u.email))
                )
                """;
        try (Connection conn = getConnection();//get connection to sql again
                Statement st = conn.createStatement()) {
            int n = st.executeUpdate(sql);
            if (n > 0) {
                ConsoleUi.muted("Linked " + n + " user account(s) to patron records.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    //set the limit to study room, set study roomd details and etc into sql
    private static void seedStudyRoomsIfEmpty() {
        String countSql = "SELECT COUNT(*) FROM study_rooms";
        String insertSql = "INSERT INTO study_rooms(name, capacity) VALUES(?, ?)";
        try (Connection conn = getConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(countSql)) {
            int n = rs.next() ? rs.getInt(1) : 0;
            if (n > 0) {
                return;
            }
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                insertRoom(ps, "Quiet Study Located at North Wing", 2);
                insertRoom(ps, "Quiet Study Located at South Wing", 2);
                insertRoom(ps, "Group Study Room A", 6);
                insertRoom(ps, "Presentation Pod", 8);
            }
            ConsoleUi.muted("Study rooms seeded.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    //to insert rooms
    private static void insertRoom(PreparedStatement ps, String name, int capacity) throws SQLException {
        ps.setString(1, name);
        ps.setInt(2, capacity);
        ps.executeUpdate();
    }

    //to loop through the sample digital and update it if the content is blank or missing
    public static void backfillDigitalReaderContentFromSamples() {
        String updateSql = """
                UPDATE items SET reader_content = ?
                WHERE item_type = 'DIGITAL' AND isbn = ?
                  AND (reader_content IS NULL OR TRIM(reader_content) = '')
                """;
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(updateSql)) {
            for (DigitalResource d : SampleCatalog.sampleDigitalResources()) {
                String excerpt = d.getReaderContent();
                if (excerpt == null || excerpt.isBlank()) {
                    continue;
                }
                ps.setString(1, excerpt);
                ps.setString(2, d.getIsbn());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    //save the patrons details into sql
    public static boolean savePatron(Patron patron) {
        String sql = "INSERT INTO patrons(name, email, patron_type) VALUES(?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, patron.getName());
            pstmt.setString(2, patron.getEmail());
            pstmt.setString(3, patron.getPatronType().name());
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) {
                    patron.setId(keys.getInt(1));
                }
            }
            return true;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed")) {
                ConsoleUi.ok("Patron email already exists: " + patron.getEmail());
            } else {
                e.printStackTrace();
            }
            return false;
        }
    }

    //save items such as book, journals and etc blah blah blah
    public static boolean saveItem(LibraryItem item) {
        String readerContent = null;
        if (item instanceof DigitalResource dr) {
            readerContent = dr.getReaderContent();
        }
        String sql = """
                INSERT INTO items(item_type, title, author, isbn, genre, status, loan_duration_days, borrowing_limit, daily_fine, reader_content)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, item.getSqlItemType());
            pstmt.setString(2, item.getTitle());
            pstmt.setString(3, item.getAuthor());
            pstmt.setString(4, item.getIsbn());
            pstmt.setString(5, item.getGenre());
            pstmt.setString(6, item.getStatus().name());
            pstmt.setInt(7, item.getLoanDurationDays());
            pstmt.setInt(8, item.getBorrowingLimit());
            pstmt.setDouble(9, item.getDailyFine());
            pstmt.setString(10, readerContent);
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) {
                    item.setId(keys.getInt(1));
                }
            }
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

   //create new user get name email and password and what not
    public static boolean addUser(String name, String email, String password) {
        String sql = "INSERT INTO users(name, email, password) VALUES(?, ?, ?)";

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, email);
            pstmt.setString(3, password);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed")) {
                ConsoleUi.ok("Username or email already exists.");
            } else {
                e.printStackTrace();
            }
            return false;
        }
    }

    //user can either log in with usernmae or email so if log in with email will use this method to find their emmail. Basically user may enter username or email so reutnr their name and email if password matches
    public static String[] findUserNameEmailIfValidCredentials(String emailOrName, String password) {
        if (password == null || emailOrName == null || emailOrName.isBlank()) {
            return null;
        }
        String trimmed = emailOrName.trim();
        String sql = "SELECT name, email FROM users WHERE (email = ? OR name = ?) AND password = ? LIMIT 1";

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, trimmed);
            pstmt.setString(2, trimmed);
            pstmt.setString(3, password);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new String[] { rs.getString("name"), rs.getString("email") };
            }
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

   //exit if name or email is missing. else set user membertype to public as default. Then exit and save and whatnot
    public static void ensurePatronForNewUser(String name, String email, PatronType memberType) {
        if (name == null || email == null || email.isBlank()) {
            return;
        }
        PatronType type = memberType != null ? memberType : PatronType.PUBLIC;
        String check = "SELECT 1 FROM patrons WHERE LOWER(TRIM(email)) = LOWER(TRIM(?)) LIMIT 1";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(check)) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
            savePatron(new Patron(name.trim(), email.trim(), type));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

   //seach for the user patron type if found retunr the necessary patron type if not found set to public as default again.
    public static PatronType getPatronTypeForEmail(String email) {
        if (email == null || email.isBlank()) {
            return PatronType.PUBLIC;
        }
        String sql = "SELECT patron_type FROM patrons WHERE LOWER(TRIM(email)) = LOWER(TRIM(?)) LIMIT 1";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return PatronType.valueOf(rs.getString("patron_type"));
                }
            }
        } catch (Exception e) {
            // IllegalArgumentException or SQLException — fall through
        }
        return PatronType.PUBLIC;
    }

    /** Accepts dd/mm/yyyy, d/m/yyyy, dd-mm-yyyy, or yyyy-mm-dd. */
    public static LocalDate parseFlexibleDate(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        DateTimeFormatter[] fmts = {
                DateTimeFormatter.ofPattern("d/M/yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("d-M-yyyy"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ISO_LOCAL_DATE
        };
        for (DateTimeFormatter fmt : fmts) {
            try {
                return LocalDate.parse(s, fmt);
            } catch (DateTimeParseException ignored) {
                // try next pattern
            }
        }
        return null;
    }

    //check is user exists or nt.
    public static boolean userExists(String name, String email) {
        String sql = "SELECT 1 FROM users WHERE name = ? OR email = ? LIMIT 1";

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, email);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    //check if email format is valid or not 
    public static boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    //get all the patron types from sql table and returns it as a list of patron objects. To show all registered lirbary members/
    public static List<Patron> getAllPatrons() {
        List<Patron> patrons = new ArrayList<>();
        String sql = "SELECT id, name, email, patron_type FROM patrons ORDER BY id";
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                patrons.add(new Patron(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        PatronType.valueOf(rs.getString("patron_type"))));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return patrons;
    }

    //seach up one patron by emial if not found then return null
    public static Patron getPatronByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String sql = """
                SELECT id, name, email, patron_type FROM patrons
                WHERE LOWER(TRIM(email)) = LOWER(TRIM(?))
                LIMIT 1
                """;
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                PatronType type;
                try {
                    type = PatronType.valueOf(rs.getString("patron_type"));
                } catch (IllegalArgumentException e) {
                    type = PatronType.PUBLIC;
                }
                return new Patron(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        type);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Librarian: add a new book acquisition with custom policy. 
    public static boolean addBookAcquisition(
            String title,
            String author,
            String isbn,
            String genre,
            int loanDurationDays,
            int borrowingLimit,
            double dailyFine) {
        Book book = new Book(title, author, isbn, genre);
        book.setLoanPolicy(loanDurationDays, borrowingLimit, dailyFine);
        boolean ok = saveItem(book);
        if (ok) {
            ConsoleUi.ok("New acquisition added to inventory.");
        }
        return ok;
    }

    
    //remove the damage book. search up the damage book with isbn then takes it out from the sql database
    public static boolean removeDamagedBookByIsbn(String isbn) {
        if (isbn == null || isbn.isBlank()) {
            ConsoleUi.ok("ISBN is required.");
            return false;
        }
        String key = InputValidation.normalizeIsbn(isbn);
        String findSql = """
                SELECT id FROM items
                WHERE item_type = 'BOOK'
                  AND replace(replace(upper(isbn), '-', ''), ' ', '') = ?
                LIMIT 1
                """;
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                Integer itemId = null;
                try (PreparedStatement find = conn.prepareStatement(findSql)) {
                    find.setString(1, key);
                    try (ResultSet rs = find.executeQuery()) {
                        if (rs.next()) {
                            itemId = rs.getInt("id");
                        }
                    }
                }
                if (itemId == null) {
                    conn.rollback();
                    ConsoleUi.ok("No book found with that ISBN.");
                    return false;
                }
                try (PreparedStatement closeLoan = conn.prepareStatement(
                        "UPDATE loans SET return_date = ? WHERE item_id = ? AND return_date IS NULL")) {
                    closeLoan.setString(1, LocalDate.now().toString());
                    closeLoan.setInt(2, itemId);
                    closeLoan.executeUpdate();
                }
                try (PreparedStatement lost = conn.prepareStatement(
                        "UPDATE items SET status = 'LOST' WHERE id = ? AND item_type = 'BOOK'")) {
                    lost.setInt(1, itemId);
                    if (lost.executeUpdate() != 1) {
                        conn.rollback();
                        ConsoleUi.ok("Could not update book status.");
                        return false;
                    }
                }
                conn.commit();
                ConsoleUi.ok("Book marked as lost/damaged and removed from circulation.");
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    //list all the lost books out
    public static void printLostBooks() {
        String sql = """
                SELECT id, title, author, isbn, genre
                FROM items
                WHERE item_type = 'BOOK' AND status = 'LOST'
                ORDER BY title
                """;
        try (Connection conn = getConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            ConsoleUi.reportHeader("Lost / removed books");
            int count = 0;
            while (rs.next()) {
                count++;
                ConsoleUi.listItem("ID " + rs.getInt("id")
                        + " | " + rs.getString("title")
                        + " | " + rs.getString("author")
                        + " | ISBN: " + rs.getString("isbn")
                        + " | " + rs.getString("genre"));
            }
            if (count == 0) {
                ConsoleUi.muted("(No lost books in catalog.)");
            } else {
                ConsoleUi.muted("Total: " + count + " title(s)");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    //same thing as the lost books but for outstanding fines.
    public static void printOutstandingFines() {
        String sql = """
                SELECT f.id, p.name, p.email, i.title, f.amount, f.amount_paid,
                       (f.amount - f.amount_paid) AS balance, f.recorded_date, f.description
                FROM fines f
                JOIN patrons p ON p.id = f.patron_id
                JOIN items i ON i.id = f.item_id
                WHERE f.amount > f.amount_paid + 0.0001
                ORDER BY f.recorded_date, f.id
                """;
        try (Connection conn = getConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            ConsoleUi.reportHeader("Outstanding fines");
            int count = 0;
            while (rs.next()) {
                count++;
                double balance = rs.getDouble("balance");
                ConsoleUi.listItem("Fine #" + rs.getInt("id")
                        + " | " + rs.getString("name") + " <" + rs.getString("email") + ">"
                        + " | " + rs.getString("title"));
                ConsoleUi.muted("Due $" + String.format("%.2f", rs.getDouble("amount"))
                        + " | Paid $" + String.format("%.2f", rs.getDouble("amount_paid"))
                        + " | Balance $" + String.format("%.2f", balance)
                        + " | " + rs.getString("recorded_date"));
                String desc = rs.getString("description");
                if (desc != null && !desc.isBlank()) {
                    ConsoleUi.muted("Note: " + desc);
                }
            }
            if (count == 0) {
                ConsoleUi.muted("(No outstanding fines.)");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    //for librarian staff to record fine payment and save it into the sql database.
    public static boolean recordFinePayment(int fineId, double payment) {
        if (fineId <= 0 || payment <= 0) {
            ConsoleUi.ok("Fine id and a positive payment amount are required.");
            return false;
        }
        String selectSql = "SELECT amount, amount_paid FROM fines WHERE id = ?";
        String updateSql = "UPDATE fines SET amount_paid = ? WHERE id = ?";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                double amount;
                double paidSoFar;
                try (PreparedStatement sel = conn.prepareStatement(selectSql)) {
                    sel.setInt(1, fineId);
                    try (ResultSet rs = sel.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            ConsoleUi.ok("No fine found with that id.");
                            return false;
                        }
                        amount = rs.getDouble("amount");
                        paidSoFar = rs.getDouble("amount_paid");
                    }
                }
                double remaining = amount - paidSoFar;
                if (remaining <= 0.0001) {
                    conn.rollback();
                    ConsoleUi.ok("This fine is already fully paid.");
                    return false;
                }
                double applied = Math.min(payment, remaining);
                double newPaid = paidSoFar + applied;
                try (PreparedStatement upd = conn.prepareStatement(updateSql)) {
                    upd.setDouble(1, newPaid);
                    upd.setInt(2, fineId);
                    upd.executeUpdate();
                }
                conn.commit();
                ConsoleUi.ok("Payment recorded: $" + String.format("%.2f", applied) + ".");
                if (newPaid + 0.0001 >= amount) {
                    ConsoleUi.ok("Fine #" + fineId + " is now fully paid.");
                } else {
                    ConsoleUi.ok("Remaining balance: $" + String.format("%.2f", amount - newPaid) + ".");
                }
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    //same thing as the lost book again. But for invetory auditting
    public static void printInventoryAudit() {
        String byStatus = """
                SELECT status, COUNT(*) AS total
                FROM items
                GROUP BY status
                ORDER BY status
                """;
        String byType = """
                SELECT item_type, COUNT(*) AS total
                FROM items
                GROUP BY item_type
                ORDER BY item_type
                """;
        try (Connection conn = getConnection()) {
            ConsoleUi.reportHeader("Inventory audit");
            try (Statement st = conn.createStatement();
                    ResultSet rs = st.executeQuery(byStatus)) {
                ConsoleUi.muted("By status");
                while (rs.next()) {
                    ConsoleUi.listItem(rs.getString("status") + ": " + rs.getInt("total"));
                }
            }
            try (Statement st = conn.createStatement();
                    ResultSet rs = st.executeQuery(byType)) {
                ConsoleUi.blank();
                ConsoleUi.muted("By item type");
                while (rs.next()) {
                    ConsoleUi.listItem(rs.getString("item_type") + ": " + rs.getInt("total"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static Book mapBookFromResultSet(ResultSet rs) throws SQLException {
        return new Book(
                rs.getInt("id"),
                rs.getString("title"),
                rs.getString("author"),
                rs.getString("isbn"),
                rs.getString("genre"),
                ItemStatus.valueOf(rs.getString("status")),
                rs.getInt("loan_duration_days"),
                rs.getInt("borrowing_limit"),
                rs.getDouble("daily_fine"));
    }

    // Loads physical booksfrom the database.
    public static List<Book> getAllBooks() {
        List<Book> books = new ArrayList<>();
        String sql = """
                SELECT id, title, author, isbn, genre, status
                , loan_duration_days, borrowing_limit, daily_fine
                FROM items
                WHERE item_type = 'BOOK'
                ORDER BY id
                """;
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                books.add(mapBookFromResultSet(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return books;
    }

    // Loads journals from the database.
    public static List<Journal> getAllJournals() {
        List<Journal> journals = new ArrayList<>();
        String sql = """
                SELECT id, title, author, isbn, genre, status
                , loan_duration_days, borrowing_limit, daily_fine
                FROM items
                WHERE item_type = 'JOURNAL'
                ORDER BY id
                """;
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                journals.add(new Journal(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getString("author"),
                        rs.getString("isbn"),
                        rs.getString("genre"),
                        ItemStatus.valueOf(rs.getString("status")),
                        rs.getInt("loan_duration_days"),
                        rs.getInt("borrowing_limit"),
                        rs.getDouble("daily_fine")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return journals;
    }

    // Loads digital resources from the database.
    public static List<DigitalResource> getAllDigitalResources() {
        List<DigitalResource> resources = new ArrayList<>();
        String sql = """
                SELECT id, title, author, isbn, genre, status
                , loan_duration_days, borrowing_limit, daily_fine, reader_content
                FROM items
                WHERE item_type = 'DIGITAL'
                ORDER BY id
                """;
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                resources.add(new DigitalResource(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getString("author"),
                        rs.getString("isbn"),
                        rs.getString("genre"),
                        ItemStatus.valueOf(rs.getString("status")),
                        rs.getInt("loan_duration_days"),
                        rs.getInt("borrowing_limit"),
                        rs.getDouble("daily_fine"),
                        rs.getString("reader_content")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return resources;
    }

    //Loads all catalog items as concrete item classes.
    public static List<LibraryItem> getAllCatalogItems() {
        List<LibraryItem> items = new ArrayList<>();
        String sql = """
                SELECT id, item_type, title, author, isbn, genre, status
                , loan_duration_days, borrowing_limit, daily_fine, reader_content
                FROM items
                ORDER BY title
                """;
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int id = rs.getInt("id");
                String type = rs.getString("item_type");
                String title = rs.getString("title");
                String author = rs.getString("author");
                String isbn = rs.getString("isbn");
                String genre = rs.getString("genre");
                ItemStatus status = ItemStatus.valueOf(rs.getString("status"));
                int loanDays = rs.getInt("loan_duration_days");
                int borrowingLimit = rs.getInt("borrowing_limit");
                double dailyFine = rs.getDouble("daily_fine");
                String readerContent = rs.getString("reader_content");

                switch (type) {
                    case "BOOK":
                        items.add(new Book(id, title, author, isbn, genre, status, loanDays, borrowingLimit, dailyFine));
                        break;
                    case "JOURNAL":
                        items.add(new Journal(id, title, author, isbn, genre, status, loanDays, borrowingLimit, dailyFine));
                        break;
                    case "DIGITAL":
                        items.add(new DigitalResource(
                                id, title, author, isbn, genre, status, loanDays, borrowingLimit, dailyFine, readerContent));
                        break;
                    default:
                        break;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    
    //basically borrow item, when items are borrow wil change to ON_LOAN, then insert loan. Then check if user got reach the maximum borrowing limit for the respective user ot not if dont have proceed if have then prompt error msg lo
    public static boolean borrowItem(int patronId, int itemId) {
        LocalDate issue = LocalDate.now();

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                PatronType memberType;
                try (PreparedStatement typeStmt = conn.prepareStatement(
                        "SELECT patron_type FROM patrons WHERE id = ?")) {
                    typeStmt.setInt(1, patronId);
                    try (ResultSet rs = typeStmt.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            ConsoleUi.warn("No library member record matches that account in the database.");
                            return false;
                        }
                        try {
                            memberType = PatronType.valueOf(rs.getString("patron_type"));
                        } catch (IllegalArgumentException e) {
                            memberType = PatronType.PUBLIC;
                        }
                    }
                }

                try (PreparedStatement activeStmt = conn.prepareStatement(
                        "SELECT COUNT(*) FROM loans WHERE patron_id = ? AND return_date IS NULL")) {
                    activeStmt.setInt(1, patronId);
                    try (ResultSet rs = activeStmt.executeQuery()) {
                        int active = rs.next() ? rs.getInt(1) : 0;
                        if (active >= memberType.maxConcurrentLoans()) {
                            conn.rollback();
                            ConsoleUi.warn("You already have the maximum number of books out for a "
                                    + memberType.toDisplayString()
                                    + " (" + memberType.maxConcurrentLoans() + "). Return a book first.");
                            return false;
                        }
                    }
                }

                int loanDays;
                int borrowingLimit;
                try (PreparedStatement policyStmt = conn.prepareStatement(
                        "SELECT loan_duration_days, borrowing_limit FROM items WHERE id = ? AND item_type = 'BOOK'")) {
                    policyStmt.setInt(1, itemId);
                    try (ResultSet rs = policyStmt.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            ConsoleUi.warn("That book id is not in the catalog (or it is not stored as a BOOK item).");
                            return false;
                        }
                        int bookLoanDays = rs.getInt("loan_duration_days");
                        loanDays = memberType.effectiveLoanDays(bookLoanDays);
                        borrowingLimit = rs.getInt("borrowing_limit");
                    }
                }

                try (PreparedStatement countBorrows = conn.prepareStatement(
                        "SELECT COUNT(*) FROM loans WHERE patron_id = ? AND item_id = ?")) {
                    countBorrows.setInt(1, patronId);
                    countBorrows.setInt(2, itemId);
                    try (ResultSet rs = countBorrows.executeQuery()) {
                        int totalBorrowedTimes = rs.next() ? rs.getInt(1) : 0;
                        if (totalBorrowedTimes >= borrowingLimit) {
                            conn.rollback();
                            ConsoleUi.warn("You have borrowed this title as many times as its policy allows. Return a copy or ask staff.");
                            return false;
                        }
                    }
                }

                LocalDate due = issue.plusDays(loanDays);
                try (PreparedStatement lockItem = conn.prepareStatement(
                        "UPDATE items SET status = 'ON_LOAN' WHERE id = ? AND status = 'AVAILABLE'")) {
                    lockItem.setInt(1, itemId);
                    if (lockItem.executeUpdate() != 1) {
                        conn.rollback();
                        ConsoleUi.warn("That copy cannot be borrowed: it may be on loan, marked lost, or the id may be wrong.");
                        return false;
                    }
                }
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO loans(patron_id, item_id, issue_date, due_date, return_date) VALUES(?,?,?,?,NULL)")) {
                    ins.setInt(1, patronId);
                    ins.setInt(2, itemId);
                    ins.setString(3, issue.toString());
                    ins.setString(4, due.toString());
                    ins.executeUpdate();
                }
                conn.commit();
                ConsoleUi.ok("Borrowed successfully.");
                ConsoleUi.info("Issue date: " + ConsoleUi.formatDate(issue));
                ConsoleUi.info("Due date: " + ConsoleUi.formatDate(due));
                return true;
            } catch (SQLException e) {
                conn.rollback();
                if (e.getMessage() != null && e.getMessage().contains("FOREIGN KEY")) {
                    ConsoleUi.warn("The borrow could not be saved (account or book link failed). Try again or contact staff.");
                } else {
                    e.printStackTrace();
                }
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    //Borrows a book using patron email and book ISBN.
    public static boolean borrowBookByPatronEmailAndIsbn(String patronEmail, String isbn) {
        if (patronEmail == null || patronEmail.isBlank() || isbn == null || isbn.isBlank()) {
            ConsoleUi.warn("Both patron email and book ISBN are required to borrow.");
            return false;
        }

        String patronSql = "SELECT id FROM patrons WHERE LOWER(TRIM(email)) = LOWER(TRIM(?)) LIMIT 1";
        String itemSql = "SELECT id FROM items WHERE item_type IN ('BOOK', 'JOURNAL', 'DIGITAL') AND isbn = ? LIMIT 1";   

        try (Connection conn = getConnection()) {
            Integer patronId = null;
            Integer itemId = null;

            try (PreparedStatement patronStmt = conn.prepareStatement(patronSql)) {
                patronStmt.setString(1, patronEmail.trim());
                try (ResultSet rs = patronStmt.executeQuery()) {
                    if (rs.next()) {
                        patronId = rs.getInt("id");
                    }
                }
            }

            try (PreparedStatement itemStmt = conn.prepareStatement(itemSql)) {
                itemStmt.setString(1, isbn.trim());
                try (ResultSet rs = itemStmt.executeQuery()) {
                    if (rs.next()) {
                        itemId = rs.getInt("id");
                    }
                }
            }

            if (patronId == null) {
                ConsoleUi.warn("Your sign-in email is not on file as a library member. Register first or ask staff to link your account.");
                return false;
            }
            if (itemId == null) {
                ConsoleUi.warn("No book in the catalog has that ISBN. Check the digits or search by title.");
                return false;
            }
            return borrowItem(patronId, itemId);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Returns available books for UI selection.
    public static List<Book> getAvailableBooks() {
        List<Book> books = new ArrayList<>();
        String sql = """
                SELECT id, title, author, isbn, genre, status, loan_duration_days, borrowing_limit, daily_fine
                FROM items
                WHERE item_type = 'BOOK' AND status = 'AVAILABLE'
                ORDER BY title
                """;
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                books.add(mapBookFromResultSet(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return books;
    }

    //list or find available books to be borrowed
    public static List<Book> findAvailableBooksByTitleLike(String query) {
        List<Book> books = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return books;
        }
        String sql = """
                SELECT id, title, author, isbn, genre, status, loan_duration_days, borrowing_limit, daily_fine
                FROM items
                WHERE item_type = 'BOOK' AND status = 'AVAILABLE' AND LOWER(title) LIKE LOWER('%' || ? || '%')
                ORDER BY title
                """;
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, query.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    books.add(mapBookFromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return books;
    }

    //show the books that the user is still holding on to. Then sorted by dude date soonest. 
    public static List<ActiveBookLoan> getActiveBookLoansForPatron(String patronEmail) {
        List<ActiveBookLoan> out = new ArrayList<>();
        if (patronEmail == null || patronEmail.isBlank()) {
            return out;
        }
        String sql = """
                SELECT i.id, i.title, i.isbn, l.issue_date, l.due_date
                FROM loans l
                JOIN patrons p ON p.id = l.patron_id
                JOIN items i ON i.id = l.item_id
                WHERE LOWER(p.email) = LOWER(TRIM(?)) AND l.return_date IS NULL AND i.item_type = 'BOOK'
                ORDER BY l.due_date
                """;
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, patronEmail.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ActiveBookLoan(
                            rs.getInt("id"),
                            rs.getString("title"),
                            rs.getString("isbn"),
                            LocalDate.parse(rs.getString("issue_date")),
                            LocalDate.parse(rs.getString("due_date"))));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return out;
    }

    
    // Returns a copy only if this patron has it on loan this is to prevents returning someone else's checkout by accident
    public static boolean returnBookForPatronByItemId(String patronEmail, int itemId) {
        if (patronEmail == null || patronEmail.isBlank()) {
            ConsoleUi.warn("Patron email is required.");
            return false;
        }
        String sql = """
                SELECT 1 FROM loans l
                JOIN patrons p ON p.id = l.patron_id
                WHERE l.item_id = ? AND LOWER(p.email) = LOWER(TRIM(?)) AND l.return_date IS NULL
                LIMIT 1
                """;
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            ps.setString(2, patronEmail.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    ConsoleUi.warn("You do not have an active loan for that book.");
                    return false;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
        return returnItem(itemId);
    }

    // Patron returns a book they borrowed, matched by ISBN.
    public static boolean returnBookByPatronEmailAndIsbn(String patronEmail, String isbn) {
        if (patronEmail == null || patronEmail.isBlank() || isbn == null || isbn.isBlank()) {
            ConsoleUi.warn("Both patron email and book ISBN are required to return.");
            return false;
        }
        String sql = """
                SELECT i.id FROM items i
                JOIN loans l ON l.item_id = i.id AND l.return_date IS NULL
                JOIN patrons p ON p.id = l.patron_id
                WHERE i.item_type = 'BOOK' AND i.isbn = ? AND LOWER(p.email) = LOWER(TRIM(?))
                LIMIT 1
                """;
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, isbn.trim());
            ps.setString(2, patronEmail.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    ConsoleUi.warn("No on-loan copy with that ISBN on your account.");
                    return false;
                }
                return returnBookForPatronByItemId(patronEmail, rs.getInt("id"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    //BASICALLY means that this copy of item is being return or handed back to the library so the system will update the db accordingly. Then go sql lo. if pass due date then fine the user ez.
    public static boolean returnItem(int itemId) {
        LocalDate today = LocalDate.now();
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                int activeLoanId;
                int patronId;
                LocalDate dueDate;
                double dailyFine;
                try (PreparedStatement loanInfo = conn.prepareStatement("""
                        SELECT l.id, l.patron_id, l.due_date, i.daily_fine
                        FROM loans l
                        JOIN items i ON i.id = l.item_id
                        WHERE l.item_id = ? AND l.return_date IS NULL
                        LIMIT 1
                        """)) {
                    loanInfo.setInt(1, itemId);
                    try (ResultSet rs = loanInfo.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            ConsoleUi.warn("No active loan for that item id.");
                            return false;
                        }
                        activeLoanId = rs.getInt("id");
                        patronId = rs.getInt("patron_id");
                        dueDate = LocalDate.parse(rs.getString("due_date"));
                        dailyFine = rs.getDouble("daily_fine");
                    }
                }

                try (PreparedStatement closeLoan = conn.prepareStatement(
                        "UPDATE loans SET return_date = ? WHERE id = ?")) {
                    closeLoan.setString(1, today.toString());
                    closeLoan.setInt(2, activeLoanId);
                    if (closeLoan.executeUpdate() != 1) {
                        conn.rollback();
                        ConsoleUi.warn("Could not close active loan.");
                        return false;
                    }
                }
                try (PreparedStatement free = conn.prepareStatement(
                        "UPDATE items SET status = 'AVAILABLE' WHERE id = ?")) {
                    free.setInt(1, itemId);
                    free.executeUpdate();
                }
                long lateDays = Math.max(0, ChronoUnit.DAYS.between(dueDate, today));
                if (lateDays > 0) {
                    double fineAmount = lateDays * dailyFine;
                    String desc = "Overdue " + lateDays + " day(s) at $" + String.format("%.2f", dailyFine) + "/day";
                    try (PreparedStatement fineIns = conn.prepareStatement("""
                            INSERT INTO fines(patron_id, loan_id, item_id, amount, amount_paid, recorded_date, description)
                            VALUES(?, ?, ?, ?, 0, ?, ?)
                            """)) {
                        fineIns.setInt(1, patronId);
                        fineIns.setInt(2, activeLoanId);
                        fineIns.setInt(3, itemId);
                        fineIns.setDouble(4, fineAmount);
                        fineIns.setString(5, today.toString());
                        fineIns.setString(6, desc);
                        fineIns.executeUpdate();
                    }
                }
                conn.commit();
                ConsoleUi.ok("Item returned.");
                if (lateDays > 0) {
                    double fine = lateDays * dailyFine;
                    ConsoleUi.warn("Overdue by " + lateDays + " day(s). Fine recorded: $" + String.format("%.2f", fine));
                    ConsoleUi.info("Patron can pay via Librarian portal, Record fine payment.");
                } else {
                    ConsoleUi.info("Returned on time. No fine.");
                }
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    //book reservatiuon thing for user to resever their books
    public static boolean addBookReservation(BookReservation reservation) {
        String checkSql = "SELECT status FROM items WHERE id = ? AND item_type = 'BOOK' LIMIT 1";
        String insertSql = """
                INSERT INTO book_reservations(item_id, requester_name, requester_email, status, request_date)
                VALUES(?, ?, ?, 'PENDING', ?)
                """;
        try (Connection conn = getConnection()) {
            try (PreparedStatement check = conn.prepareStatement(checkSql)) {
                check.setInt(1, reservation.getItemId());
                try (ResultSet rs = check.executeQuery()) {
                    if (!rs.next()) {
                        ConsoleUi.warn("That book id is not in the catalog, so a reservation cannot be created.");
                        return false;
                    }
                    if (!"ON_LOAN".equals(rs.getString("status"))) {
                        ConsoleUi.info("This book is not on loan, so reservation is not required.");
                        return false;
                    }
                }
            }

            try (PreparedStatement insert = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                insert.setInt(1, reservation.getItemId());
                insert.setString(2, reservation.getRequesterName());
                insert.setString(3, reservation.getRequesterEmail());
                insert.setString(4, LocalDate.now().toString());
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (keys.next()) {
                        reservation.setId(keys.getInt(1));
                    }
                }
            }
            ConsoleUi.ok("Reservation request submitted.");
            return true;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed")) {
                ConsoleUi.warn("You already have a pending reservation for this book.");
            } else {
                e.printStackTrace();
            }
            return false;
        }
    }

   
    //study room booking feature. this is to check whether the slot is valid or not
    public static boolean isStudySlotValid(String slotLabel) {
        if (slotLabel == null) {
            return false;
        }
        String t = slotLabel.trim();
        for (String s : STUDY_TIME_SLOTS) {
            if (s.equals(t)) {
                return true;
            }
        }
        return false;
    }

    public static List<StudyRoomRow> getStudyRoomsList() {
        List<StudyRoomRow> out = new ArrayList<>();
        String sql = "SELECT id, name, capacity FROM study_rooms ORDER BY id";
        try (Connection conn = getConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(new StudyRoomRow(rs.getInt("id"), rs.getString("name"), rs.getInt("capacity")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return out;
    }

    //loads the top method into a list then return it. again basically its like show my study room reservations then a the list will be printed out
    public static List<StudyBookingRow> getStudyBookingsForPatron(String patronEmail) {
        List<StudyBookingRow> out = new ArrayList<>();
        if (patronEmail == null || patronEmail.isBlank()) {
            return out;
        }
        String sql = """
                SELECT b.id, r.name, b.booking_date, b.slot_label
                FROM study_room_bookings b
                JOIN study_rooms r ON r.id = b.room_id
                WHERE LOWER(b.patron_email) = LOWER(?)
                ORDER BY b.booking_date, b.slot_label
                """;
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, patronEmail.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new StudyBookingRow(
                            rs.getInt("id"),
                            rs.getString("name"),
                            LocalDate.parse(rs.getString("booking_date")),
                            rs.getString("slot_label")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return out;
    }

    //Prints time slots for a room on a date and marks which are already booked.
    public static void printStudySlotsForRoom(int roomId, LocalDate bookingDate) {
        String dateStr = bookingDate.toString();
        List<String> taken = new ArrayList<>();
        String q = "SELECT slot_label FROM study_room_bookings WHERE room_id = ? AND booking_date = ?";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setInt(1, roomId);
            ps.setString(2, dateStr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    taken.add(rs.getString("slot_label"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }
        ConsoleUi.reportHeader("Room #" + roomId + "  -   " + ConsoleUi.formatDate(bookingDate));
        for (int i = 0; i < STUDY_TIME_SLOTS.length; i++) {
            String slot = STUDY_TIME_SLOTS[i];
            boolean booked = taken.contains(slot);
            ConsoleUi.numberedLine(i + 1, slot + (booked ? "    (booked)" : "    (available)"));
        }
    }

    //booking for study room. then must book early early lah. have one day window to book if not cannot book lo. and also check whther user email is valid for booking or not eles return respective error msg.
    public static boolean bookStudyRoom(int roomId, String patronEmail, LocalDate bookingDate, String slotLabel) {
        if (patronEmail == null || patronEmail.isBlank()) {
            ConsoleUi.warn("Patron email is required.");
            return false;
        }
        if (bookingDate == null) {
            ConsoleUi.warn("Booking date is required.");
            return false;
        }
        if (!isStudySlotValid(slotLabel)) {
            ConsoleUi.warn("Invalid time slot.");
            return false;
        }
        String email = patronEmail.trim();
        if (bookingDate.isBefore(LocalDate.now())) {
            ConsoleUi.warn("Cannot book a date in the past.");
            return false;
        }
        String dateStr = bookingDate.toString();
        String patronCheck = "SELECT 1 FROM patrons WHERE LOWER(TRIM(email)) = LOWER(TRIM(?)) LIMIT 1";
        String roomCheck = "SELECT 1 FROM study_rooms WHERE id = ? LIMIT 1";
        String insertSql = """
                INSERT INTO study_room_bookings(room_id, patron_email, booking_date, slot_label, created_date)
                VALUES(?, ?, ?, ?, ?)
                """;
        try (Connection conn = getConnection()) {
            try (PreparedStatement pc = conn.prepareStatement(patronCheck)) {
                pc.setString(1, email);
                try (ResultSet rs = pc.executeQuery()) {
                    if (!rs.next()) {
                        ConsoleUi.warn("No patron registered with that email.");
                        return false;
                    }
                }
            }
            try (PreparedStatement rc = conn.prepareStatement(roomCheck)) {
                rc.setInt(1, roomId);
                try (ResultSet rs = rc.executeQuery()) {
                    if (!rs.next()) {
                        ConsoleUi.warn("Study room not found.");
                        return false;
                    }
                }
            }
            try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                ins.setInt(1, roomId);
                ins.setString(2, email);
                ins.setString(3, dateStr);
                ins.setString(4, slotLabel.trim());
                ins.setString(5, LocalDate.now().toString());
                ins.executeUpdate();
            }
            ConsoleUi.ok("Study room booked: room #" + roomId + " on " + ConsoleUi.formatDate(bookingDate) + " "
                    + slotLabel.trim() + ".");
            return true;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed")) {
                ConsoleUi.warn("That slot is already booked for this room and date.");
            } else {
                e.printStackTrace();
            }
            return false;
        }
    }

    //print out the study room booking for user when they request
    public static void printStudyBookingsForPatron(String patronEmail) {
        if (patronEmail == null || patronEmail.isBlank()) {
            ConsoleUi.warn("Email is required.");
            return;
        }
        List<StudyBookingRow> rows = getStudyBookingsForPatron(patronEmail);
        ConsoleUi.reportHeader("Your study room bookings");
        if (rows.isEmpty()) {
            ConsoleUi.muted("(No bookings.)");
            return;
        }
        int i = 1;
        for (StudyBookingRow r : rows) {
            ConsoleUi.numberedLine(i, r.roomName + " | " + ConsoleUi.formatDate(r.bookingDate)
                    + " " + r.slotLabel + "  (booking #" + r.id + ")");
            i++;
        }
    }

    //for cancelling study booking room check with sql de. get their emial and id and what not then check lo
    public static boolean cancelStudyBooking(int bookingId, String patronEmail) {
        if (bookingId <= 0 || patronEmail == null || patronEmail.isBlank()) {
            ConsoleUi.warn("Booking id and patron email are required.");
            return false;
        }
        String sql = "DELETE FROM study_room_bookings WHERE id = ? AND LOWER(patron_email) = LOWER(?)";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            ps.setString(2, patronEmail.trim());
            int rows = ps.executeUpdate();
            if (rows == 1) {
                ConsoleUi.ok("Booking cancelled.");
                return true;
            }
            ConsoleUi.warn("No matching booking for that id and email.");
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
