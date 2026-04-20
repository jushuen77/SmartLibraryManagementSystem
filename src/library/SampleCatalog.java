// Sample starter rows, when the DB is still empty, seed runs once from here. After that the app uses real saved data. So basically fake run one time cuz wanna avoid the empty recorded input
package library;

import library.model.Book;
import library.model.DigitalResource;
import library.model.Journal;
import library.model.Patron;
import library.model.PatronType;
import java.util.ArrayList;
import java.util.List;

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
        Book alchemist = new Book("The Alchemist", "Paulo Coelho", "9780061122415", "Fiction");
        alchemist.setLoanPolicy(14, 3, 1.00);
        list.add(alchemist);
        Book sapiens = new Book("Sapiens", "Yuval Noah Harari", "9780062316097", "History");
        sapiens.setLoanPolicy(21, 3, 1.25);
        list.add(sapiens);
        Book hamlet = new Book("Hamlet", "William Shakespeare", "9780743477123", "Classic");
        hamlet.setLoanPolicy(14, 2, 0.75);
        list.add(hamlet);
        Book beloved = new Book("Beloved", "Toni Morrison", "9781400033416", "Classic fiction");
        beloved.setLoanPolicy(21, 3, 1.00);
        list.add(beloved);
        Book fahrenheit451 = new Book("Fahrenheit 451", "Ray Bradbury", "9781451673319", "Dystopian fiction");
        fahrenheit451.setLoanPolicy(10, 3, 1.25);
        list.add(fahrenheit451);
        Book frankenstein = new Book("Frankenstein", "Mary Shelley", "9780486282114", "Gothic fiction");
        frankenstein.setLoanPolicy(14, 2, 1.00);
        list.add(frankenstein);
        Book dracula = new Book("Dracula", "Bram Stoker", "9780486411095", "Horror");
        dracula.setLoanPolicy(14, 2, 1.00);
        list.add(dracula);
        Book emma = new Book("Emma", "Jane Austen", "9780141439587", "Classic fiction");
        emma.setLoanPolicy(28, 4, 0.50);
        list.add(emma);
        return list;
    }

    public static List<Journal> sampleJournals() {
        List<Journal> list = new ArrayList<>();
        Journal nature = new Journal("Nature Vol. 632", "Nature Publishing Group", "9789000000012", "Science");
        nature.setLoanPolicy(7, 2, 1.50);
        list.add(nature);
        Journal ieeeSw = new Journal("IEEE Software", "IEEE Computer Society", "9789000000029", "Technology");
        ieeeSw.setLoanPolicy(14, 3, 1.25);
        list.add(ieeeSw);
        Journal lancet = new Journal("The Lancet", "Elsevier", "9789000000036", "Medicine");
        lancet.setLoanPolicy(7, 2, 2.00);
        list.add(lancet);
        Journal jmlr = new Journal("JMLR Vol. 25", "JMLR Inc.", "9789000000043", "Computer science");
        jmlr.setLoanPolicy(14, 2, 1.00);
        list.add(jmlr);
        Journal acmCsur = new Journal("ACM Computing Surveys", "ACM", "9789000000050", "Computer science");
        acmCsur.setLoanPolicy(14, 2, 1.25);
        list.add(acmCsur);
        Journal science = new Journal("Science Digest", "AAAS", "9789000000067", "Science");
        science.setLoanPolicy(7, 2, 1.75);
        list.add(science);
        Journal hbr = new Journal("Harvard Business Review", "Harvard Business Publishing", "9789000000074", "Business");
        hbr.setLoanPolicy(21, 4, 1.00);
        list.add(hbr);
        Journal psych = new Journal("Psychological Review", "APA", "9789000000081", "Psychology");
        psych.setLoanPolicy(14, 3, 1.00);
        list.add(psych);
        Journal arch = new Journal("Architectural Review", "EMAP Publishing", "9789000000098", "Architecture");
        arch.setLoanPolicy(21, 3, 0.75);
        list.add(arch);
        Journal chem = new Journal("JACS Highlights", "ACS Publications", "9789000000104", "Chemistry");
        chem.setLoanPolicy(7, 2, 1.50);
        list.add(chem);
        Journal hist = new Journal("Past & Present", "Oxford University Press", "9789000000111", "History");
        hist.setLoanPolicy(28, 4, 0.50);
        list.add(hist);
        Journal nejm = new Journal("NEJM Clinical Updates", "Massachusetts Medical Society", "9789000000128", "Medicine");
        nejm.setLoanPolicy(7, 2, 2.25);
        list.add(nejm);
        Journal bmj = new Journal("The BMJ", "BMJ Publishing Group", "9789000000135", "Medicine");
        bmj.setLoanPolicy(14, 3, 1.50);
        list.add(bmj);
        Journal cacm = new Journal("Communications of the ACM", "ACM", "9789000000142", "Computer science");
        cacm.setLoanPolicy(14, 3, 1.00);
        list.add(cacm);
        Journal prl = new Journal("Physical Review Letters", "American Physical Society", "9789000000159", "Physics");
        prl.setLoanPolicy(7, 2, 1.25);
        list.add(prl);
        Journal ai = new Journal("Artificial Intelligence", "Elsevier", "9789000000166", "Computer science");
        ai.setLoanPolicy(21, 3, 1.00);
        list.add(ai);
        Journal econometrica = new Journal("Econometrica", "Wiley", "9789000000173", "Economics");
        econometrica.setLoanPolicy(21, 4, 1.00);
        list.add(econometrica);
        Journal foreignAffairs = new Journal("Foreign Affairs", "Council on Foreign Relations", "9789000000180", "Political science");
        foreignAffairs.setLoanPolicy(28, 4, 0.75);
        list.add(foreignAffairs);
        return list;
    }

    public static List<DigitalResource> sampleDigitalResources() {
        List<DigitalResource> list = new ArrayList<>();
        DigitalResource hp3 = new DigitalResource(
                "HP: Azkaban (e-book)", "J.K. Rowling", "9781781100509", "Fantasy");
        hp3.setReaderContent(sampleExcerptHpAzkaban());
        list.add(hp3);
        DigitalResource hp5 = new DigitalResource(
                "HP: Phoenix (e-book)", "J.K. Rowling", "9781781101353", "Fantasy");
        hp5.setReaderContent(sampleExcerptHpPhoenix());
        list.add(hp5);
        DigitalResource phm = new DigitalResource("Project Hail Mary", "Andy Weir", "9780593135204", "Science fiction");
        phm.setReaderContent(sampleExcerptProjectHailMary());
        list.add(phm);
        DigitalResource clean = new DigitalResource("Clean Code", "Robert C. Martin", "9780132350884", "Technology");
        clean.setReaderContent(sampleExcerptCleanCode());
        list.add(clean);
        DigitalResource ej = new DigitalResource("Effective Java", "Joshua Bloch", "9780134685991", "Technology");
        ej.setReaderContent(sampleExcerptEffectiveJava());
        list.add(ej);
        return list;
    }

    private static String sampleExcerptHpAzkaban() {
        return """
                --- E-BOOK READER (excerpt) ---
                Chapter 1: Owl Post

                Harry Potter was a highly unusual boy in many ways. For one thing, he hated the summer holidays more than any other time of year. For another, he really wanted to do his homework, but was forced to do it in secret, in the dead of night...

                He missed the castle, with its secret passageways and ghosts, his classes, mail arriving by owl, eating in the Great Hall, sleeping in his four-poster bed in the tower dormitory, visiting the gamekeeper, Hagrid, in his cabin next to the Forbidden Forest...

                [Integrated reader - full text would load from library digital lending.]
                """.strip();
    }

    private static String sampleExcerptHpPhoenix() {
        return """
                --- E-BOOK READER (excerpt) ---
                The hottest day of the summer so far was drawing to a close and a drowsy silence lay over the large, square houses of Privet Drive...

                Harry Potter was lying flat on his back, breathing hard as though he had been running. He had awoken with a start, his scar prickling...

                [Integrated reader - resume reading syncs across devices in a full deployment.]
                """.strip();
    }

    private static String sampleExcerptProjectHailMary() {
        return """
                --- E-BOOK READER (excerpt) ---
                I'm pretty much fucked.

                That's my considered opinion.

                Fucked.

                Six days ago I was in space. Now I'm alone on a ship I don't know how to fly, with no crew, and dwindling supplies...

                [Integrated reader - DRM and loan expiry handled by the library platform.]
                """.strip();
    }

    private static String sampleExcerptCleanCode() {
        return """
                --- E-BOOK READER (excerpt) ---
                You are reading this book for two reasons. First, you are a programmer. Second, you want to be a better programmer...

                The single responsibility principle states that a class should have only one reason to change...

                [Integrated reader -  diagrams and code samples render in the full viewer.]
                """.strip();
    }

    private static String sampleExcerptEffectiveJava() {
        return """
                --- E-BOOK READER (excerpt) ---
                This book is designed to help you make the most effective use of the Java programming language and its fundamental libraries...

                Consider static factory methods instead of constructors when you need more flexible object creation...

                [Integrated reader -  search and annotations supported in production.]
                """.strip();
    }
}
