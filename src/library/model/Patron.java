package library.model;

import data.Database;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * Lines for a "My profile" screen (member id, contact, type, how many books at once).
     */
    public List<String> profileLines() {
        List<String> lines = new ArrayList<>();
        if (id > 0) {
            lines.add("Library member ID: " + id);
        }
        lines.add("Name on record: " + name);
        lines.add("Email: " + email);
        lines.add("Member type: " + patronType.toDisplayString());
        lines.add("You may borrow up to " + patronType.maxConcurrentLoans() + " physical books at the same time.");
        return lines;
    }

    /** Convenience loader so the class can request its own DB records. */
    public static List<Patron> fetchAllFromDatabase() {
        return Database.getAllPatrons();
    }
}
