package library.model;

public abstract class LibraryItem {

    private int id;
    private final String title;
    private final String author;
    private final String isbn;
    private final String genre;
    private ItemStatus status;
    private int loanDurationDays;
    private int borrowingLimit;
    private double dailyFine;

    protected LibraryItem(String title, String author, String isbn, String genre) {
        this(0, title, author, isbn, genre, ItemStatus.AVAILABLE, 14, 5, 1.0);
    }

    protected LibraryItem(int id, String title, String author, String isbn, String genre, ItemStatus status) {
        this(id, title, author, isbn, genre, status, 14, 5, 1.0);
    }

    protected LibraryItem(
            int id,
            String title,
            String author,
            String isbn,
            String genre,
            ItemStatus status,
            int loanDurationDays,
            int borrowingLimit,
            double dailyFine) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.genre = genre;
        this.status = status != null ? status : ItemStatus.AVAILABLE;
        this.loanDurationDays = loanDurationDays;
        this.borrowingLimit = borrowingLimit;
        this.dailyFine = dailyFine;
    }

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

    public int getLoanDurationDays() {
        return loanDurationDays;
    }

    public int getBorrowingLimit() {
        return borrowingLimit;
    }

    public double getDailyFine() {
        return dailyFine;
    }

    public void setLoanPolicy(int loanDurationDays, int borrowingLimit, double dailyFine) {
        this.loanDurationDays = Math.max(1, loanDurationDays);
        this.borrowingLimit = Math.max(1, borrowingLimit);
        this.dailyFine = Math.max(0.0, dailyFine);
    }

    @Override
    public String toString() {
        return getSqlItemType() + ": " + title + " by " + author + " [" + genre + "]";
    }
}
