package data;

import java.time.LocalDate;

public final class ActiveBookLoan {
    public final int itemId;
    public final String title;
    public final String isbn;
    public final LocalDate signedOutDate;
    public final LocalDate dueDate;

    public ActiveBookLoan(int itemId, String title, String isbn, LocalDate signedOutDate, LocalDate dueDate) {
        this.itemId = itemId;
        this.title = title;
        this.isbn = isbn;
        this.signedOutDate = signedOutDate;
        this.dueDate = dueDate;
    }
}
