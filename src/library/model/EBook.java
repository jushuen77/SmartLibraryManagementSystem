package library.model;

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
}
