package library.model;

import data.Database;
import java.util.List;

/** Journal item ({@code item_type = JOURNAL}) in the catalog. */
public class Journal extends LibraryItem {

    public Journal(String title, String author, String isbn, String genre) {
        super(title, author, isbn, genre);
    }

    public Journal(int id, String title, String author, String isbn, String genre, ItemStatus status) {
        super(id, title, author, isbn, genre, status);
    }

    public Journal(
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
        return "JOURNAL";
    }

    /** Convenience loader so this class can request its DB records. */
    public static List<Journal> fetchAllFromDatabase() {
        return Database.getAllJournals();
    }
}
