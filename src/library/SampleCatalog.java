//just some sample of books, ebooks, journals and etc (Something like a database for the materials)
package library;

import library.model.Book;
import library.model.DigitalResource;
import library.model.Journal;
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
        Book hp1 = new Book("Harry Potter and the Philosopher's Stone", "J.K. Rowling", "9780747532699", "Fantasy");
        hp1.setLoanPolicy(14, 3, 1.00);
        list.add(hp1);
        Book hp2 = new Book("Harry Potter and the Chamber of Secrets", "J.K. Rowling", "9780439064873", "Fantasy");
        hp2.setLoanPolicy(14, 3, 1.00);
        list.add(hp2);
        Book hp4 = new Book("Harry Potter and the Goblet of Fire", "J.K. Rowling", "9780439139601", "Fantasy");
        hp4.setLoanPolicy(14, 2, 1.25);
        list.add(hp4);
        Book hobbit = new Book("The Hobbit", "J.R.R. Tolkien", "9780547928227", "Fantasy");
        hobbit.setLoanPolicy(21, 4, 0.75);
        list.add(hobbit);
        Book lotr = new Book("The Lord of the Rings", "J.R.R. Tolkien", "9780544003415", "Fantasy");
        lotr.setLoanPolicy(21, 2, 1.50);
        list.add(lotr);
        Book nineteen84 = new Book("1984", "George Orwell", "9780451524935", "Dystopian fiction");
        nineteen84.setLoanPolicy(10, 3, 1.25);
        list.add(nineteen84);
        Book dune = new Book("Dune", "Frank Herbert", "9780441172719", "Science fiction");
        dune.setLoanPolicy(14, 2, 1.50);
        list.add(dune);
        Book pride = new Book("Pride and Prejudice", "Jane Austen", "9780141439518", "Classic fiction");
        pride.setLoanPolicy(28, 5, 0.50);
        list.add(pride);
        return list;
    }

    public static List<Journal> sampleJournals() {
        List<Journal> list = new ArrayList<>();
        list.add(new Journal("Nature - Quantum Computing Issue", "Nature Editorial", "0028-0836", "Science"));
        list.add(new Journal("IEEE Software - Secure Coding", "IEEE", "0740-7459", "Technology"));
        list.add(new Journal("The Lancet - Public Health Review", "The Lancet", "0140-6736", "Medicine"));
        return list;
    }

    public static List<DigitalResource> sampleDigitalResources() {
        List<DigitalResource> list = new ArrayList<>();
        list.add(new DigitalResource("Harry Potter and the Prisoner of Azkaban (e-book)", "J.K. Rowling", "9781781100502", "Fantasy"));
        list.add(new DigitalResource("Harry Potter and the Order of the Phoenix (e-book)", "J.K. Rowling", "9781781101353", "Fantasy"));
        list.add(new DigitalResource("Project Hail Mary", "Andy Weir", "9780593135204", "Science fiction"));
        list.add(new DigitalResource("Clean Code", "Robert C. Martin", "9780132350884", "Technology"));
        list.add(new DigitalResource("Effective Java", "Joshua Bloch", "9780134685991", "Technology"));
        return list;
    }
}
