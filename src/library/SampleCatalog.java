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
        Journal nature = new Journal("Nature — Vol. 632, Quantum computing focus", "Nature Publishing Group", "0028-0836", "Science");
        nature.setLoanPolicy(7, 2, 1.50);
        list.add(nature);
        Journal ieeeSw = new Journal("IEEE Software — Secure coding practices", "IEEE Computer Society", "0740-7459", "Technology");
        ieeeSw.setLoanPolicy(14, 3, 1.25);
        list.add(ieeeSw);
        Journal lancet = new Journal("The Lancet — Public health and policy review", "Elsevier", "0140-6736", "Medicine");
        lancet.setLoanPolicy(7, 2, 2.00);
        list.add(lancet);
        Journal jmlr = new Journal("Journal of Machine Learning Research — Vol. 25", "JMLR Inc.", "1532-4435", "Computer science");
        jmlr.setLoanPolicy(14, 2, 1.00);
        list.add(jmlr);
        Journal acmCsur = new Journal("ACM Computing Surveys — Human–computer interaction", "ACM", "0360-0300", "Computer science");
        acmCsur.setLoanPolicy(14, 2, 1.25);
        list.add(acmCsur);
        Journal science = new Journal("Science — Research articles digest", "AAAS", "0036-8075", "Science");
        science.setLoanPolicy(7, 2, 1.75);
        list.add(science);
        Journal hbr = new Journal("Harvard Business Review — Strategy & leadership", "Harvard Business Publishing", "0017-8012", "Business");
        hbr.setLoanPolicy(21, 4, 1.00);
        list.add(hbr);
        Journal psych = new Journal("Psychological Review — Cognitive science monograph", "APA", "0033-295X", "Psychology");
        psych.setLoanPolicy(14, 3, 1.00);
        list.add(psych);
        Journal arch = new Journal("Architectural Review — Sustainable design", "EMAP Publishing", "0003-861X", "Architecture");
        arch.setLoanPolicy(21, 3, 0.75);
        list.add(arch);
        Journal chem = new Journal("Journal of the American Chemical Society — Highlights", "ACS Publications", "0002-7863", "Chemistry");
        chem.setLoanPolicy(7, 2, 1.50);
        list.add(chem);
        Journal hist = new Journal("Past & Present — Social history quarterly", "Oxford University Press", "0031-2746", "History");
        hist.setLoanPolicy(28, 4, 0.50);
        list.add(hist);
        Journal nejm = new Journal("The New England Journal of Medicine — Clinical updates", "Massachusetts Medical Society", "0028-4793", "Medicine");
        nejm.setLoanPolicy(7, 2, 2.25);
        list.add(nejm);
        Journal bmj = new Journal("The BMJ — Evidence-based practice", "BMJ Publishing Group", "1756-1833", "Medicine");
        bmj.setLoanPolicy(14, 3, 1.50);
        list.add(bmj);
        Journal cacm = new Journal("Communications of the ACM — Software engineering trends", "ACM", "0001-0782", "Computer science");
        cacm.setLoanPolicy(14, 3, 1.00);
        list.add(cacm);
        Journal prl = new Journal("Physical Review Letters — Condensed matter highlights", "American Physical Society", "0031-9007", "Physics");
        prl.setLoanPolicy(7, 2, 1.25);
        list.add(prl);
        Journal ai = new Journal("Artificial Intelligence — Planning & reasoning survey", "Elsevier", "0004-3702", "Computer science");
        ai.setLoanPolicy(21, 3, 1.00);
        list.add(ai);
        Journal econometrica = new Journal("Econometrica — Econometrics monograph", "Wiley", "0012-9682", "Economics");
        econometrica.setLoanPolicy(21, 4, 1.00);
        list.add(econometrica);
        Journal foreignAffairs = new Journal("Foreign Affairs — International relations review", "Council on Foreign Relations", "0015-7120", "Political science");
        foreignAffairs.setLoanPolicy(28, 4, 0.75);
        list.add(foreignAffairs);
        return list;
    }

    public static List<DigitalResource> sampleDigitalResources() {
        List<DigitalResource> list = new ArrayList<>();
        DigitalResource hp3 = new DigitalResource(
                "Harry Potter and the Prisoner of Azkaban (e-book)", "J.K. Rowling", "9781781100502", "Fantasy");
        hp3.setReaderContent(sampleExcerptHpAzkaban());
        list.add(hp3);
        DigitalResource hp5 = new DigitalResource(
                "Harry Potter and the Order of the Phoenix (e-book)", "J.K. Rowling", "9781781101353", "Fantasy");
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

                [Integrated reader — full text would load from library digital lending.]
                """.strip();
    }

    private static String sampleExcerptHpPhoenix() {
        return """
                --- E-BOOK READER (excerpt) ---
                The hottest day of the summer so far was drawing to a close and a drowsy silence lay over the large, square houses of Privet Drive...

                Harry Potter was lying flat on his back, breathing hard as though he had been running. He had awoken with a start, his scar prickling...

                [Integrated reader — resume reading syncs across devices in a full deployment.]
                """.strip();
    }

    private static String sampleExcerptProjectHailMary() {
        return """
                --- E-BOOK READER (excerpt) ---
                I'm pretty much fucked.

                That's my considered opinion.

                Fucked.

                Six days ago I was in space. Now I'm alone on a ship I don't know how to fly, with no crew, and dwindling supplies...

                [Integrated reader — DRM and loan expiry handled by the library platform.]
                """.strip();
    }

    private static String sampleExcerptCleanCode() {
        return """
                --- E-BOOK READER (excerpt) ---
                You are reading this book for two reasons. First, you are a programmer. Second, you want to be a better programmer...

                The single responsibility principle states that a class should have only one reason to change...

                [Integrated reader — diagrams and code samples render in the full viewer.]
                """.strip();
    }

    private static String sampleExcerptEffectiveJava() {
        return """
                --- E-BOOK READER (excerpt) ---
                This book is designed to help you make the most effective use of the Java programming language and its fundamental libraries...

                Consider static factory methods instead of constructors when you need more flexible object creation...

                [Integrated reader — search and annotations supported in production.]
                """.strip();
    }
}
