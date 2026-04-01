package library.model;

/** Library patron aligned with the {@code patrons} table (set {@code id} after insert). */
public class Patron {

    private int id;
    private final String name;
    private final String email;
    private final PatronType patronType;

    public Patron(String name, String email, PatronType patronType) {
        this(0, name, email, patronType);
    }

    public Patron(int id, String name, String email, PatronType patronType) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.patronType = patronType;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public PatronType getPatronType() {
        return patronType;
    }

    @Override
    public String toString() {
        return "Patron{id=" + id + ", name='" + name + "', email='" + email + "', type=" + patronType + "}";
    }
}
