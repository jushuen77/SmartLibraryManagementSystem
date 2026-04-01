package data;

import java.sql.*;
import java.time.LocalDate;
import library.SampleCatalog;
import library.model.Book;
import library.model.EBook;
import library.model.LibraryItem;
import library.model.Patron;

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
                status TEXT NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE','ON_LOAN','LOST'))
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

        String activeLoanIndex = """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_loans_one_active_per_item
                ON loans(item_id) WHERE return_date IS NULL;
                """;

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(usersTable);
            stmt.execute(patronsTable);
            stmt.execute(itemsTable);
            stmt.execute(loansTable);
            stmt.execute(activeLoanIndex);
            System.out.println("Database tables ready (users, patrons, items, loans).");
            seedSampleDataIfEmpty();
        } catch (SQLException e) {
            e.printStackTrace();
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
                for (EBook e : SampleCatalog.sampleEBooks()) {
                    saveItem(e);
                }
                System.out.println("Loaded sample books, e-books, and patrons into the database.");
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
                INSERT INTO items(item_type, title, author, isbn, genre, status)
                VALUES(?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, item.getSqlItemType());
            pstmt.setString(2, item.getTitle());
            pstmt.setString(3, item.getAuthor());
            pstmt.setString(4, item.getIsbn());
            pstmt.setString(5, item.getGenre());
            pstmt.setString(6, item.getStatus().name());
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

    /** itemType: BOOK, JOURNAL, DIGITAL */
    public static void addItem(String itemType, String title, String author, String isbn, String genre) {
        String sql = """
                INSERT INTO items(item_type, title, author, isbn, genre, status)
                VALUES(?, ?, ?, ?, ?, 'AVAILABLE')
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

    /** Borrows if item is AVAILABLE; sets due date to today + LOAN_DAYS. */
    public static boolean borrowItem(int patronId, int itemId) {
        LocalDate issue = LocalDate.now();
        LocalDate due = issue.plusDays(LOAN_DAYS);

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
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
                System.out.println("Borrowed. Due date: " + due);
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

    /** Returns an item that is currently on loan (one active loan per item). */
    public static boolean returnItem(int itemId) {
        LocalDate today = LocalDate.now();
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement closeLoan = conn.prepareStatement(
                        "UPDATE loans SET return_date = ? WHERE item_id = ? AND return_date IS NULL")) {
                    closeLoan.setString(1, today.toString());
                    closeLoan.setInt(2, itemId);
                    if (closeLoan.executeUpdate() != 1) {
                        conn.rollback();
                        System.out.println("No active loan for that item id.");
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
}
