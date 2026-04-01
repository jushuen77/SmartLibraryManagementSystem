package library.model;

/**
 * Base type for catalog entries stored in {@code items} ({@code BOOK}, {@code DIGITAL}, {@code JOURNAL}).
 */
public abstract class LibraryItem {

    private int id;
    private final String title;
    private final String author;
    private final String isbn;
    private final String genre;
    private ItemStatus status;

    protected LibraryItem(String title, String author, String isbn, String genre) {
        this(0, title, author, isbn, genre, ItemStatus.AVAILABLE);
    }

    protected LibraryItem(int id, String title, String author, String isbn, String genre, ItemStatus status) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.genre = genre;
        this.status = status != null ? status : ItemStatus.AVAILABLE;
    }

    /** Value for the {@code item_type} column: BOOK, DIGITAL, or JOURNAL. */
    public abstract String getSqlItemType();

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getGenre() {
        return genre;
    }

    public ItemStatus getStatus() {
        return status;
    }

    public void setStatus(ItemStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return getSqlItemType() + ": " + title + " by " + author + " [" + genre + "]";
    }
}
