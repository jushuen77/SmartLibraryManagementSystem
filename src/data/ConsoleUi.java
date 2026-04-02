package data;

/**
 * Terminal layout helpers: box-drawn menus, consistent spacing, simple status lines.
 * Uses UTF-8 box characters; run the JVM with UTF-8 console (e.g. Windows Terminal) for best results.
 */
public final class ConsoleUi {

    /** Text width between the vertical borders of a menu box. */
    private static final int INNER = 42;

    private ConsoleUi() {
    }

    private static String repeat(char c, int n) {
        return String.valueOf(c).repeat(Math.max(0, n));
    }

    private static String hbar() {
        return repeat('\u2500', INNER + 2);
    }

    /** Pads/truncates one line inside a menu panel. */
    private static String panelLine(String text) {
        String t = text == null ? "" : text;
        if (t.length() > INNER) {
            t = t.substring(0, INNER - 3) + "...";
        }
        return "  \u2502 " + String.format("%-" + INNER + "s", t) + " \u2502";
    }

    public static void blank() {
        System.out.println();
    }

    /** Small welcome strip at startup. */
    public static void welcomeBanner() {
        blank();
        System.out.println("  \u256D" + repeat('\u2500', INNER + 2) + "\u256E");
        System.out.println(panelLine("  G4 Smart Library"));
        System.out.println(panelLine("  Catalog  \u00B7  Loans  \u00B7  Fines  \u00B7  Study rooms"));
        System.out.println("  \u2570" + repeat('\u2500', INNER + 2) + "\u256F");
        blank();
    }

    /** Top of a numbered menu: title row + separator under it. */
    public static void menuStart(String title) {
        blank();
        System.out.println("  \u250C" + hbar() + "\u2510");
        System.out.println(panelLine(title));
        System.out.println("  \u251C" + hbar() + "\u2524");
    }

    public static void menuOption(int num, String label) {
        System.out.println(panelLine("[" + num + "] " + label));
    }

    /** Bottom border + choice prompt (call after all menuOption lines). */
    public static void menuEnd() {
        System.out.println("  \u2514" + hbar() + "\u2518");
        System.out.print("  \u2192 Choice: ");
    }

    /** Same as menuEnd but without prompt (e.g. before nested questions). */
    public static void menuEndNoPrompt() {
        System.out.println("  \u2514" + hbar() + "\u2518");
    }

    /** Section title for list pages (books, journals, …). */
    public static void sectionTitle(String title) {
        blank();
        System.out.println("  \u2500\u2500 " + title + " ");
        System.out.println("  " + repeat('\u2500', Math.min(56, title.length() + 8)));
    }

    public static void listItem(String line) {
        System.out.println("    \u2022 " + line);
    }

    /** Numbered row (menus, time slots) without a bullet. */
    public static void numberedLine(int n, String line) {
        System.out.println(String.format("    %2d.  %s", n, line));
    }

    public static void muted(String line) {
        System.out.println("      " + line);
    }

    /** Single-line form field label (keep on same line as user input). */
    public static void fieldPrompt(String label) {
        System.out.print("  \u203A " + label + " ");
    }

    public static void ok(String msg) {
        System.out.println("  \u2713 " + msg);
    }

    public static void warn(String msg) {
        System.out.println("  ! " + msg);
    }

    public static void info(String msg) {
        System.out.println("  i " + msg);
    }

    /** Pause between screens. */
    public static void pressEnterHint() {
        System.out.println("  \u00B7  Press Enter to continue...");
    }

    /** Framed block title for database report blocks. */
    public static void reportHeader(String title) {
        blank();
        System.out.println("  \u250C" + hbar() + "\u2510");
        System.out.println(panelLine(title));
        System.out.println("  \u2514" + hbar() + "\u2518");
    }

    /** Reader / sub-screen divider. */
    public static void readerPageBar(String title, int page, int total) {
        System.out.println();
        System.out.println("  " + repeat('\u2500', 48));
        System.out.println("  " + title + "  \u2014  page " + page + "/" + total);
        System.out.println("  " + repeat('\u2500', 48));
    }

    public static void readerControlsHint() {
        System.out.print("  \u2192 [n]ext  [p]rev  [q]uit: ");
    }
}
