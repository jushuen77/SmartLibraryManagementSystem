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
import library.model.EBook;
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

    /** Default loan length (days); real per-book rules use the row's loan_duration_days when borrowing. */
    public static final int LOAN_DAYS = 14;

    // Opens the db file; PRAGMA foreign_keys = ON so SQLite actually enforces "this patron id exists, right?"
    private static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(url);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }

    /**
     * Bootstraps every table and index. IF NOT EXISTS allows safe runs on every startup without dropping existing data.
     * After DDL we seed empty tables and backfill optional columns (reader text, etc.).
     * Think of it as moving day: we only unpack boxes if the shelves are empty.
     */
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

        // The actual stuff on shelves (and some pretend shelves for digital rows)
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

        // Someone walked out with a book; return_date null = "they still have it (hopefully)"
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

        // "Save my spot" when the only copy is out — logic lives in addBookReservation(), not in the table name
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

        // You cannot loan the same physical copy to two people unless physics breaks. SQLite agrees.
        String activeLoanIndex = """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_loans_one_active_per_item
                ON loans(item_id) WHERE return_date IS NULL;
                """;

        String activeReservationIndex = """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_reservation_one_pending_per_user_item
                ON book_reservations(item_id, requester_email) WHERE status = 'PENDING';
                """;

        // Where polite reminders become numbers with dollar signs
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

        // Optional: fight over chairs in a civilized way (one booking per room/slot/day)
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

        try (Connection conn = getConnection();//this is to get connection for the database
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
            ConsoleUi.muted("Database ready: users, patrons, items, loans, reservations, fines, study rooms.");
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

    public static final String[] STUDY_TIME_SLOTS = {
            "09:00-10:00", "10:00-11:00", "11:00-12:00", "12:00-13:00",
            "13:00-14:00", "14:00-15:00", "15:00-16:00", "16:00-17:00"
    };

    // Gentle migrations: if someone's DB predates a column, we ALTER; if not, we skip and nobody cries
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

    // So digital rows can smuggle a pretend chapter inside the database for the console reader
    private static void ensureItemsReaderContentColumn(Statement stmt) throws SQLException {
        if (!hasColumn("items", "reader_content")) {
            stmt.execute("ALTER TABLE items ADD COLUMN reader_content TEXT");
        }
    }

    // introspect sqlite schema — used so we don't run ALTER twice
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

    /**
     * Inserts {@link SampleCatalog} patrons and items when those tables are empty (Harry Potter and other examples).
     */
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

    /**
     * Ensures every {@code users} row has a matching {@code patrons} row (fixes accounts created before this link existed).
     */
    public static void syncPatronsFromUsersIfMissing() {
        String sql = """
                INSERT INTO patrons(name, email, patron_type)
                SELECT u.name, u.email, 'PUBLIC' FROM users u
                WHERE NOT EXISTS (
                    SELECT 1 FROM patrons p WHERE LOWER(TRIM(p.email)) = LOWER(TRIM(u.email))
                )
                """;
        try (Connection conn = getConnection();
                Statement st = conn.createStatement()) {
            int n = st.executeUpdate(sql);
            if (n > 0) {
                ConsoleUi.muted("Linked " + n + " user account(s) to patron records.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // First launch: we invent four rooms. After that, your furniture is your problem.
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

    private static void insertRoom(PreparedStatement ps, String name, int capacity) throws SQLException {
        ps.setString(1, name);
        ps.setInt(2, capacity);
        ps.executeUpdate();
    }

    /**
     * For databases created before reader_content existed: fill sample excerpts by ISBN.
     * Rows that already have non-empty reader_content are left unchanged.
     */
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

    /** Persists a patron and sets {@link Patron#setId(int)} from the database. */
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
                System.out.println("Patron email already exists: " + patron.getEmail());
            } else {
                e.printStackTrace();
            }
            return false;
        }
    }

    /** Inserts any LibraryItem; DigitalResource may carry reader_content for the e-reader feature. */
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

    /**
     * Inserts a row into {@code users}. Password is stored as provided (hash in production).
     * @return true if a row was inserted
     */
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
                System.out.println("Username or email already exists.");
            } else {
                e.printStackTrace();
            }
            return false;
        }
    }

    /**
     * Validates patron login and returns {@code [display name, canonical email]} from {@code users}, or {@code null}.
     */
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

    /**
     * Checks {@code users} for a matching name or email and password.
     */
    public static boolean authenticate(String emailOrName, String password) {
        return findUserNameEmailIfValidCredentials(emailOrName, password) != null;
    }

    /**
     * After a new {@code users} row, ensure a matching {@code patrons} row so loans and study bookings work.
     */
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

    /** Same as {@link #ensurePatronForNewUser(String, String, PatronType)} with {@link PatronType#PUBLIC}. */
    public static void ensurePatronForNewUser(String name, String email) {
        ensurePatronForNewUser(name, email, PatronType.PUBLIC);
    }

    /** Resolves {@link PatronType} from the {@code patrons} table (library member category). */
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

    public static boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    public static void printAllUsers() {
        String sql = "SELECT id, name, email FROM users";

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                System.out.println("ID: " + rs.getInt("id")
                        + ", Name: " + rs.getString("name")
                        + ", Email: " + rs.getString("email"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** STUDENT, FACULTY, or PUBLIC */
    public static void addPatron(String name, String email, String patronType) {
        String sql = "INSERT INTO patrons(name, email, patron_type) VALUES(?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, email);
            pstmt.setString(3, patronType.toUpperCase());
            pstmt.executeUpdate();
            System.out.println("Patron registered.");
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed")) {
                System.out.println("Email already registered as a patron.");
            } else if (e.getMessage() != null && e.getMessage().contains("CHECK constraint")) {
                System.out.println("Invalid patron type. Use STUDENT, FACULTY, or PUBLIC.");
            } else {
                e.printStackTrace();
            }
        }
    }

    public static void printAllPatrons() {
        String sql = "SELECT id, name, email, patron_type FROM patrons ORDER BY id";
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                System.out.println("ID: " + rs.getInt("id")
                        + ", " + rs.getString("name")
                        + ", " + rs.getString("email")
                        + ", " + rs.getString("patron_type"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Loads every patron row as {@link Patron} objects. */
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

    /** Single patron row by email (library profile), or {@code null}. */
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

    /** itemType: BOOK, JOURNAL, DIGITAL */
    public static void addItem(String itemType, String title, String author, String isbn, String genre) {
        String sql = """
                INSERT INTO items(item_type, title, author, isbn, genre, status, loan_duration_days, borrowing_limit, daily_fine, reader_content)
                VALUES(?, ?, ?, ?, ?, 'AVAILABLE', 14, 5, 1.0, NULL)
                """;
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, itemType.toUpperCase());
            pstmt.setString(2, title);
            pstmt.setString(3, author == null || author.isBlank() ? null : author);
            pstmt.setString(4, isbn == null || isbn.isBlank() ? null : isbn);
            pstmt.setString(5, genre == null || genre.isBlank() ? null : genre);
            pstmt.executeUpdate();
            System.out.println("Item added to catalog.");
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("CHECK constraint")) {
                System.out.println("Invalid item type. Use BOOK, JOURNAL, or DIGITAL.");
            } else {
                e.printStackTrace();
            }
        }
    }

    /** Librarian: add a new book acquisition with custom policy. */
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
            System.out.println("New acquisition added to inventory.");
        }
        return ok;
    }

    /**
     * Mark a copy LOST/damaged. Any open loan for that item is closed first so loans stay consistent with inventory.
     */
    public static boolean removeDamagedBookByIsbn(String isbn) {
        if (isbn == null || isbn.isBlank()) {
            System.out.println("ISBN is required.");
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
                    System.out.println("No book found with that ISBN.");
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
                        System.out.println("Could not update book status.");
                        return false;
                    }
                }
                conn.commit();
                System.out.println("Book marked as lost/damaged and removed from circulation.");
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

    /** Lists physical books currently marked LOST (for tracking and replacement). */
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

    /** Fines with a remaining balance (overdue and other recorded fines). */
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

    /**
     * Staff types how much the patron paid; we cap at remaining balance (no accidental negative debt universe).
     * @return true if money actually moved
     */
    public static boolean recordFinePayment(int fineId, double payment) {
        if (fineId <= 0 || payment <= 0) {
            System.out.println("Fine id and a positive payment amount are required.");
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
                            System.out.println("No fine found with that id.");
                            return false;
                        }
                        amount = rs.getDouble("amount");
                        paidSoFar = rs.getDouble("amount_paid");
                    }
                }
                double remaining = amount - paidSoFar;
                if (remaining <= 0.0001) {
                    conn.rollback();
                    System.out.println("This fine is already fully paid.");
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
                System.out.println("Payment recorded: $" + String.format("%.2f", applied) + ".");
                if (newPaid + 0.0001 >= amount) {
                    System.out.println("Fine #" + fineId + " is now fully paid.");
                } else {
                    System.out.println("Remaining balance: $" + String.format("%.2f", amount - newPaid) + ".");
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

    /** Librarian: inventory audit summary. */
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

    public static void printAllItems() {
        String sql = "SELECT id, item_type, title, author, isbn, genre, status FROM items ORDER BY id";
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                System.out.println("ID: " + rs.getInt("id")
                        + " [" + rs.getString("item_type") + "] "
                        + rs.getString("title")
                        + " | " + rs.getString("author")
                        + " | ISBN: " + rs.getString("isbn")
                        + " | " + rs.getString("genre")
                        + " | " + rs.getString("status"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Loads physical books ({@code item_type = BOOK}) from the database. */
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
                books.add(new Book(
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
        return books;
    }

    /** Loads digital books ({@code item_type = DIGITAL}) from the database. */
    public static List<EBook> getAllEBooks() {
        List<EBook> ebooks = new ArrayList<>();
        String sql = """
                SELECT id, title, author, isbn, genre, status
                FROM items
                WHERE item_type = 'DIGITAL'
                ORDER BY id
                """;
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ebooks.add(new EBook(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getString("author"),
                        rs.getString("isbn"),
                        rs.getString("genre"),
                        ItemStatus.valueOf(rs.getString("status"))));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return ebooks;
    }

    /** Loads journals ({@code item_type = JOURNAL}) from the database. */
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

    /** Loads digital resources ({@code item_type = DIGITAL}) from the database. */
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

    /** Loads all catalog items as concrete item classes. */
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

    /**
     * Borrow: flip book to ON_LOAN, insert loan, respect loan length and "how many times can you borrow this title" caps.
     * One transaction — if the JVM trips, you do not get a Schrödinger's checkout.
     */
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
                            ConsoleUi.warn("Member record not found.");
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
                            ConsoleUi.warn("Book not found.");
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
                            ConsoleUi.warn("Borrowing limit reached for this book.");
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
                        ConsoleUi.warn("Item not available (wrong id, on loan, or lost).");
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
                    ConsoleUi.warn("Invalid patron or item id.");
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

    /** Borrows a book using patron email and book ISBN. */
    public static boolean borrowBookByPatronEmailAndIsbn(String patronEmail, String isbn) {
        if (patronEmail == null || patronEmail.isBlank() || isbn == null || isbn.isBlank()) {
            ConsoleUi.warn("Patron email and ISBN are required.");
            return false;
        }

        String patronSql = "SELECT id FROM patrons WHERE LOWER(TRIM(email)) = LOWER(TRIM(?)) LIMIT 1";
        String itemSql = "SELECT id FROM items WHERE item_type = 'BOOK' AND isbn = ? LIMIT 1";

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
                ConsoleUi.warn("No patron record for your account. Register again or contact staff.");
                return false;
            }
            if (itemId == null) {
                ConsoleUi.warn("No book found with that ISBN.");
                return false;
            }
            return borrowItem(patronId, itemId);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Borrows a book using patron email and exact book title. */
    public static boolean borrowBookByPatronEmailAndTitle(String patronEmail, String title) {
        if (patronEmail == null || patronEmail.isBlank() || title == null || title.isBlank()) {
            ConsoleUi.warn("Patron email and title are required.");
            return false;
        }

        String patronSql = "SELECT id FROM patrons WHERE LOWER(TRIM(email)) = LOWER(TRIM(?)) LIMIT 1";
        String itemSql = "SELECT id FROM items WHERE item_type = 'BOOK' AND title = ? LIMIT 1";

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
                itemStmt.setString(1, title.trim());
                try (ResultSet rs = itemStmt.executeQuery()) {
                    if (rs.next()) {
                        itemId = rs.getInt("id");
                    }
                }
            }

            if (patronId == null) {
                ConsoleUi.warn("No patron record for your account. Register again or contact staff.");
                return false;
            }
            if (itemId == null) {
                ConsoleUi.warn("No book found with that exact title.");
                return false;
            }
            return borrowItem(patronId, itemId);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Returns available books for UI selection. */
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
                books.add(new Book(
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
        return books;
    }

    /** Available books whose title contains the query (case-insensitive). */
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
                    books.add(new Book(
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
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return books;
    }

    /** One row the patron currently has on loan (physical book). */
    public static final class ActiveBookLoan {
        public final int itemId;
        public final String title;
        public final String isbn;
        public final LocalDate signedOutDate;
        public final LocalDate dueDate;

        public ActiveBookLoan(int itemId, String title, String isbn, LocalDate signedOutDate, LocalDate dueDate) {
            this.itemId = itemId;
            this.title = title;
            this.isbn = isbn;
            this.signedOutDate = signedOutDate;
            this.dueDate = dueDate;
        }
    }

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

    public static List<ActiveBookLoan> findActiveBookLoansForPatronByTitleLike(String patronEmail, String titleQuery) {
        List<ActiveBookLoan> all = getActiveBookLoansForPatron(patronEmail);
        if (titleQuery == null || titleQuery.isBlank()) {
            return all;
        }
        String q = titleQuery.trim().toLowerCase();
        List<ActiveBookLoan> out = new ArrayList<>();
        for (ActiveBookLoan row : all) {
            if (row.title != null && row.title.toLowerCase().contains(q)) {
                out.add(row);
            }
        }
        return out;
    }

    /**
     * Returns a copy only if this patron has it on loan (prevents returning someone else's checkout).
     */
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

    /** Patron returns a book they borrowed, matched by ISBN. */
    public static boolean returnBookByPatronEmailAndIsbn(String patronEmail, String isbn) {
        if (patronEmail == null || patronEmail.isBlank() || isbn == null || isbn.isBlank()) {
            ConsoleUi.warn("Patron email and ISBN are required.");
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

    /** ISBN → item id → same as returnItem() (wrapper for the console UI). */
    public static boolean returnBookByIsbn(String isbn) {
        if (isbn == null || isbn.isBlank()) {
            System.out.println("ISBN is required.");
            return false;
        }
        String sql = "SELECT id FROM items WHERE item_type = 'BOOK' AND isbn = ? LIMIT 1";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, isbn.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("No book found with that ISBN.");
                    return false;
                }
                return returnItem(rs.getInt("id"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Return: close loan, book back to AVAILABLE. If the due date is a distant memory, we also insert a fine row.
     * Same transaction — the money reminder and the shelf update arrive together, like a polite ambush.
     */
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

    public static void printActiveLoans() {
        String sql = """
                SELECT l.id, l.patron_id, p.name, l.item_id, i.title, l.issue_date, l.due_date
                FROM loans l
                JOIN patrons p ON p.id = l.patron_id
                JOIN items i ON i.id = l.item_id
                WHERE l.return_date IS NULL
                ORDER BY l.due_date
                """;
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                System.out.println("Loan #" + rs.getInt("id")
                        + " | Patron " + rs.getInt("patron_id") + " (" + rs.getString("name") + ")"
                        + " | Item " + rs.getInt("item_id") + ": " + rs.getString("title")
                        + " | Issued " + rs.getString("issue_date")
                        + " | Due " + rs.getString("due_date"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Queue for a book that is already out — no point reserving air when the copy is sitting on the shelf. */
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
                        ConsoleUi.warn("Book not found.");
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

    // ========== Study room booking (optional feature) ==========
    // App asks questions; we say "no" if the slot is taken or the patron is imaginary.

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

    /** Room row for numbered pick lists in the console UI. */
    public static final class StudyRoomRow {
        public final int id;
        public final String name;
        public final int capacity;

        public StudyRoomRow(int id, String name, int capacity) {
            this.id = id;
            this.name = name;
            this.capacity = capacity;
        }
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

    public static void printStudyRooms() {
        ConsoleUi.reportHeader("Study rooms");
        List<StudyRoomRow> rooms = getStudyRoomsList();
        int i = 1;
        for (StudyRoomRow r : rooms) {
            ConsoleUi.numberedLine(i, "Room #" + r.id + " | " + r.name + " | seats: " + r.capacity);
            i++;
        }
        if (rooms.isEmpty()) {
            ConsoleUi.muted("(No rooms configured.)");
        }
    }

    /** One study booking for the signed-in member (used for list + cancel flows). */
    public static final class StudyBookingRow {
        public final int id;
        public final String roomName;
        public final LocalDate bookingDate;
        public final String slotLabel;

        public StudyBookingRow(int id, String roomName, LocalDate bookingDate, String slotLabel) {
            this.id = id;
            this.roomName = roomName;
            this.bookingDate = bookingDate;
            this.slotLabel = slotLabel;
        }
    }

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

    /** Prints time slots for a room on a date; marks which are already booked. */
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
        ConsoleUi.reportHeader("Room #" + roomId + "  —  " + ConsoleUi.formatDate(bookingDate));
        for (int i = 0; i < STUDY_TIME_SLOTS.length; i++) {
            String slot = STUDY_TIME_SLOTS[i];
            boolean booked = taken.contains(slot);
            ConsoleUi.numberedLine(i + 1, slot + (booked ? "    (booked)" : "    (available)"));
        }
    }

    /**
     * Inserts a booking; UNIQUE(room, date, slot) rejects a second booking for the same slot.
     * Booking date must be today or later.
     */
    public static boolean bookStudyRoom(int roomId, String patronEmail, LocalDate bookingDate, String slotLabel) {
        if (patronEmail == null || patronEmail.isBlank()) {
            ConsoleUi.warn("Patron email is required.");
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
