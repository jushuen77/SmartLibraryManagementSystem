package library.model;

import java.util.List;

import data.Database;


public class DigitalResource extends LibraryItem {

    private String readerContent;

    public DigitalResource(String title, String author, String isbn, String genre) {
        super(title, author, isbn, genre);
        this.readerContent = null;//null means no text loaded yet
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

    //Convenience loader so this class can request its DB records. same thing again for easy output
    public static List<DigitalResource> fetchAllFromDatabase() {
        return Database.getAllDigitalResources();
    }
}
