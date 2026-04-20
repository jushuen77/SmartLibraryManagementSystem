package library.model;

public class BookReservation {

    private int id;
    private final int itemId;
    private final String requesterName;
    private final String requesterEmail;
    private final String status;
    private final String requestDate;

    public BookReservation(int itemId, String requesterName, String requesterEmail) {
        this(0, itemId, requesterName, requesterEmail, "PENDING", null);
    }

    public BookReservation(int id, int itemId, String requesterName, String requesterEmail, String status, String requestDate) {
        this.id = id;
        this.itemId = itemId;
        this.requesterName = requesterName;
        this.requesterEmail = requesterEmail;
        this.status = status;
        this.requestDate = requestDate;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getItemId() {
        return itemId;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public String getRequesterEmail() {
        return requesterEmail;
    }

    public String getStatus() {
        return status;
    }

    public String getRequestDate() {
        return requestDate;
    }
}
