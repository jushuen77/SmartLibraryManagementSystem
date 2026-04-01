package library.model;

import data.Database;
import java.util.List;

/** Digital catalog resource ({@code item_type = DIGITAL}). */
public class DigitalResource extends LibraryItem {

    public DigitalResource(String title, String author, String isbn, String genre) {
        super(title, author, isbn, genre);
    }

    public DigitalResource(int id, String title, String author, String isbn, String genre, ItemStatus status) {
        super(id, title, author, isbn, genre, status);
    }

    public DigitalResource(
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
        return "DIGITAL";
    }

    /** Convenience loader so this class can request its DB records. */
    public static List<DigitalResource> fetchAllFromDatabase() {
        return Database.getAllDigitalResources();
    }
}
