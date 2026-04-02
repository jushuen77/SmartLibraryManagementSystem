package library.model;

import data.Database;
import java.util.List;

/*
 * Anything in sqlite with item_type = DIGITAL ends up as one of these.
 * reader_content is optional plain text for the built-in "e-book reader" screens in App.
 */
public class DigitalResource extends LibraryItem {

    /** Excerpt / chapter stub we show in the console reader — null means "no text loaded yet" */
    private String readerContent;

    public DigitalResource(String title, String author, String isbn, String genre) {
        super(title, author, isbn, genre);
        this.readerContent = null;
    }

    public DigitalResource(int id, String title, String author, String isbn, String genre, ItemStatus status) {
        super(id, title, author, isbn, genre, status);
        this.readerContent = null;
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
        this(id, title, author, isbn, genre, status, loanDurationDays, borrowingLimit, dailyFine, null);
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
            double dailyFine,
            String readerContent) {
        super(id, title, author, isbn, genre, status, loanDurationDays, borrowingLimit, dailyFine);
        this.readerContent = readerContent;
    }

    @Override
    public String getSqlItemType() {
        return "DIGITAL";
    }

    public String getReaderContent() {
        return readerContent;
    }

    public void setReaderContent(String readerContent) {
        this.readerContent = readerContent;
    }

    /** Convenience loader so this class can request its DB records. */
    public static List<DigitalResource> fetchAllFromDatabase() {
        return Database.getAllDigitalResources();
    }
}
