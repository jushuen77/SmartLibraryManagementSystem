package library.model;

import java.util.List;

import data.Database;

/** Physical or standard circulating book ({@code item_type = BOOK}). */
public class Book extends LibraryItem {

    public Book(String title, String author, String isbn, String genre) {
        super(title, author, isbn, genre);
    }

    public Book(int id, String title, String author, String isbn, String genre, ItemStatus status) {
        super(id, title, author, isbn, genre, status);
    }

    public Book(
            int id,
            String title,
            String author,
            String isbn,
            String genre,
            ItemStatus status,
            int loanDurationDays,
            int borrowingLimit,
            double dailyFine) {
        super(id, title, author, isbn, genre, status, loanDurationDays, borrowingLimit, dailyFine);
    }

    @Override
    public String getSqlItemType() {
        return "BOOK";
    }

    /** Convenience loader so the class can request its own DB records. */
    public static List<Book> fetchAllFromDatabase() {
        return Database.getAllBooks();
    }
}
