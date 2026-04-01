package library;

import library.model.Book;
import library.model.EBook;
import library.model.Patron;
import library.model.PatronType;
import java.util.ArrayList;
import java.util.List;

/** Example patrons and titles (Harry Potter and others) for seeding the database. */
public final class SampleCatalog {

    private SampleCatalog() {
    }

    public static List<Patron> samplePatrons() {
        List<Patron> list = new ArrayList<>();
        list.add(new Patron("Hermione Granger", "hermione.granger@hogwarts.example", PatronType.STUDENT));
        list.add(new Patron("Harry Potter", "harry.potter@hogwarts.example", PatronType.STUDENT));
        list.add(new Patron("Minerva McGonagall", "mcgonagall@hogwarts.example", PatronType.FACULTY));
        list.add(new Patron("Remus Lupin", "r.lupin@hogwarts.example", PatronType.FACULTY));
        list.add(new Patron("Molly Weasley", "molly.weasley@public.lib", PatronType.PUBLIC));
        return list;
    }

    public static List<Book> sampleBooks() {
        List<Book> list = new ArrayList<>();
        list.add(new Book("Harry Potter and the Philosopher's Stone", "J.K. Rowling", "9780747532699", "Fantasy"));
        list.add(new Book("Harry Potter and the Chamber of Secrets", "J.K. Rowling", "9780439064873", "Fantasy"));
        list.add(new Book("Harry Potter and the Goblet of Fire", "J.K. Rowling", "9780439139601", "Fantasy"));
        list.add(new Book("The Hobbit", "J.R.R. Tolkien", "9780547928227", "Fantasy"));
        list.add(new Book("The Lord of the Rings", "J.R.R. Tolkien", "9780544003415", "Fantasy"));
        list.add(new Book("1984", "George Orwell", "9780451524935", "Dystopian fiction"));
        list.add(new Book("Dune", "Frank Herbert", "9780441172719", "Science fiction"));
        list.add(new Book("Pride and Prejudice", "Jane Austen", "9780141439518", "Classic fiction"));
        return list;
    }

    public static List<EBook> sampleEBooks() {
        List<EBook> list = new ArrayList<>();
        list.add(new EBook("Harry Potter and the Prisoner of Azkaban (e-book)", "J.K. Rowling", "9781781100502", "Fantasy"));
        list.add(new EBook("Harry Potter and the Order of the Phoenix (e-book)", "J.K. Rowling", "9781781101353", "Fantasy"));
        list.add(new EBook("Project Hail Mary", "Andy Weir", "9780593135204", "Science fiction"));
        list.add(new EBook("Clean Code", "Robert C. Martin", "9780132350884", "Technology"));
        list.add(new EBook("Effective Java", "Joshua Bloch", "9780134685991", "Technology"));
        return list;
    }
}
