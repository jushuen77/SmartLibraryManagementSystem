package data;

import java.sql.*;
import java.time.LocalDate;
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

public class Database {
    private static final String url = "jdbc:sqlite:users.db";
    /** Loan length in days (used when borrowing). */
    public static final int LOAN_DAYS = 14;

    private static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(url);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }

    /** Creates users, patrons, items, loans (safe to run more than once). */
    public static void createTable() {
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

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(usersTable);
            stmt.execute(patronsTable);
            stmt.execute(itemsTable);
            ensureItemsPolicyColumns(stmt);
            stmt.execute(loansTable);
            stmt.execute(reservationsTable);
            stmt.execute(activeLoanIndex);
            stmt.execute(activeReservationIndex);
            System.out.println("Database tables ready (users, patrons, items, loans, reservations).");
            seedSampleDataIfEmpty();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

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
                System.out.println("Loaded sample books, journals, digital resources, and patrons into the database.");
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

    /** Persists a catalog item and sets {@link LibraryItem#setId(int)}. */
    public static boolean saveItem(LibraryItem item) {
        String sql = """
                INSERT INTO items(item_type, title, author, isbn, genre, status, loan_duration_days, borrowing_limit, daily_fine)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
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
     * Checks {@code users} for a matching name or email and password.
     */
    public static boolean authenticate(String emailOrName, String password) {
        if (password == null || emailOrName == null || emailOrName.isBlank()) {
            return false;
        }
        String trimmed = emailOrName.trim();
        String sql = "SELECT 1 FROM users WHERE (email = ? OR name = ?) AND password = ? LIMIT 1";

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, trimmed);
            pstmt.setString(2, trimmed);
            pstmt.setString(3, password);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
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
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
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

    /** itemType: BOOK, JOURNAL, DIGITAL */
    public static void addItem(String itemType, String title, String author, String isbn, String genre) {
        String sql = """
                INSERT INTO items(item_type, title, author, isbn, genre, status, loan_duration_days, borrowing_limit, daily_fine)
                VALUES(?, ?, ?, ?, ?, 'AVAILABLE', 14, 5, 1.0)
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

    /** Librarian: remove damaged books from circulation by marking LOST. */
    public static boolean removeDamagedBookByIsbn(String isbn) {
        String sql = "UPDATE items SET status = 'LOST' WHERE item_type = 'BOOK' AND isbn = ?";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, isbn);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                System.out.println("Book marked as damaged/removed from circulation.");
                return true;
            }
            System.out.println("No book found with that ISBN.");
            return false;
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
            System.out.println("\nInventory Audit");
            try (Statement st = conn.createStatement();
                    ResultSet rs = st.executeQuery(byStatus)) {
                System.out.println("By Status:");
                while (rs.next()) {
                    System.out.println("- " + rs.getString("status") + ": " + rs.getInt("total"));
                }
            }
            try (Statement st = conn.createStatement();
                    ResultSet rs = st.executeQuery(byType)) {
                System.out.println("By Type:");
                while (rs.next()) {
                    System.out.println("- " + rs.getString("item_type") + ": " + rs.getInt("total"));
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
                , loan_duration_days, borrowing_limit, daily_fine
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
                        rs.getDouble("daily_fine")));
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
                , loan_duration_days, borrowing_limit, daily_fine
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

                switch (type) {
                    case "BOOK":
                        items.add(new Book(id, title, author, isbn, genre, status, loanDays, borrowingLimit, dailyFine));
                        break;
                    case "JOURNAL":
                        items.add(new Journal(id, title, author, isbn, genre, status, loanDays, borrowingLimit, dailyFine));
                        break;
                    case "DIGITAL":
                        items.add(new DigitalResource(id, title, author, isbn, genre, status, loanDays, borrowingLimit, dailyFine));
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

    /** Borrows if item is AVAILABLE; sets due date based on item loan duration. */
    public static boolean borrowItem(int patronId, int itemId) {
        LocalDate issue = LocalDate.now();

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                int loanDays;
                int borrowingLimit;
                try (PreparedStatement policyStmt = conn.prepareStatement(
                        "SELECT loan_duration_days, borrowing_limit FROM items WHERE id = ? AND item_type = 'BOOK'")) {
                    policyStmt.setInt(1, itemId);
                    try (ResultSet rs = policyStmt.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            System.out.println("Book not found.");
                            return false;
                        }
                        loanDays = rs.getInt("loan_duration_days");
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
                            System.out.println("Borrowing limit reached for this book.");
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
                        System.out.println("Item not available (wrong id, on loan, or lost).");
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
                System.out.println("Borrowed successfully.");
                System.out.println("Issue date: " + issue);
                System.out.println("Due date: " + due);
                return true;
            } catch (SQLException e) {
                conn.rollback();
                if (e.getMessage() != null && e.getMessage().contains("FOREIGN KEY")) {
                    System.out.println("Invalid patron or item id.");
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
            System.out.println("Patron email and ISBN are required.");
            return false;
        }

        String patronSql = "SELECT id FROM patrons WHERE email = ? LIMIT 1";
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
                System.out.println("No patron found with that email. Register patron first.");
                return false;
            }
            if (itemId == null) {
                System.out.println("No book found with that ISBN.");
                return false;
            }
            return borrowItem(patronId, itemId);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Returns an item that is currently on loan (one active loan per item). */
    public static boolean returnItem(int itemId) {
        LocalDate today = LocalDate.now();
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                int activeLoanId;
                LocalDate dueDate;
                double dailyFine;
                try (PreparedStatement loanInfo = conn.prepareStatement("""
                        SELECT l.id, l.due_date, i.daily_fine
                        FROM loans l
                        JOIN items i ON i.id = l.item_id
                        WHERE l.item_id = ? AND l.return_date IS NULL
                        LIMIT 1
                        """)) {
                    loanInfo.setInt(1, itemId);
                    try (ResultSet rs = loanInfo.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            System.out.println("No active loan for that item id.");
                            return false;
                        }
                        activeLoanId = rs.getInt("id");
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
                        System.out.println("Could not close active loan.");
                        return false;
                    }
                }
                try (PreparedStatement free = conn.prepareStatement(
                        "UPDATE items SET status = 'AVAILABLE' WHERE id = ?")) {
                    free.setInt(1, itemId);
                    free.executeUpdate();
                }
                conn.commit();
                System.out.println("Item returned.");
                long lateDays = Math.max(0, ChronoUnit.DAYS.between(dueDate, today));
                if (lateDays > 0) {
                    double fine = lateDays * dailyFine;
                    System.out.println("Overdue by " + lateDays + " day(s). Fine: $" + String.format("%.2f", fine));
                } else {
                    System.out.println("Returned on time. No fine.");
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

    /** Saves a reservation for an on-loan book. */
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
                        System.out.println("Book not found.");
                        return false;
                    }
                    if (!"ON_LOAN".equals(rs.getString("status"))) {
                        System.out.println("This book is not on loan, so reservation is not required.");
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
            System.out.println("Reservation request submitted.");
            return true;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed")) {
                System.out.println("You already have a pending reservation for this book.");
            } else {
                e.printStackTrace();
            }
            return false;
        }
    }
}
