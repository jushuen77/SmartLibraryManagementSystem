package library.model;

/** Physical or standard circulating book ({@code item_type = BOOK}). */
public class Book extends LibraryItem {

    public Book(String title, String author, String isbn, String genre) {
        super(title, author, isbn, genre);
    }

    public Book(int id, String title, String author, String isbn, String genre, ItemStatus status) {
        super(id, title, author, isbn, genre, status);
    }

    @Override
    public String getSqlItemType() {
        return "BOOK";
    }
}
