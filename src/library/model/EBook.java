package library.model;

import java.util.List;

import data.Database;

/** Digital / e-book copy ({@code item_type = DIGITAL} in SQLite). */
public class EBook extends LibraryItem {

    public EBook(String title, String author, String isbn, String genre) {
        super(title, author, isbn, genre);
    }

    public EBook(int id, String title, String author, String isbn, String genre, ItemStatus status) {
        super(id, title, author, isbn, genre, status);
    }

    @Override
    public String getSqlItemType() {
        return "DIGITAL";
    }

    /** Convenience loader so the class can request its own DB records. */
    public static List<EBook> fetchAllFromDatabase() {
        return Database.getAllEBooks();
    }
}
