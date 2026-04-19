package data;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Terminal layout helpers: centered menus, consistent spacing, and readable status lines.
 */
public final class ConsoleUi {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Text width between the vertical borders of a menu box. */
    private static final int INNER = 54;
    /** Terminal width used for centering the whole UI block. */
    private static final int TERMINAL_WIDTH = detectTerminalWidth();

    private ConsoleUi() {
    }

    private static String repeat(char c, int n) {
        return String.valueOf(c).repeat(Math.max(0, n));
    }

    private static String hbar() {
        return repeat('\u2500', INNER + 2);
    }

    /**
     * Tries to read current Windows console width via `mode con`.
     * Falls back to 120 when unknown.
     */
    private static int detectTerminalWidth() {
        try {
            Process p = new ProcessBuilder("cmd", "/c", "mode con").redirectErrorStream(true).start();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String trimmed = line.trim().toLowerCase();
                    if (trimmed.startsWith("columns:")) {
                        String value = trimmed.substring("columns:".length()).trim();
                        int cols = Integer.parseInt(value);
                        if (cols >= 60) {
                            return cols;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through to default below
        }
        return 120;
    }

    /** Left margin that centers the boxed UI region as a whole. */
    private static String leftPad() {
        int uiWidth = INNER + 6; // borders + side spaces
        int pad = Math.max(0, (TERMINAL_WIDTH - uiWidth) / 2);
        return repeat(' ', pad);
    }

    /** Pads/truncates one line inside a menu panel. */
    private static String panelLine(String text) {
        String t = text == null ? "" : text;
        if (t.length() > INNER) {
            t = t.substring(0, INNER - 3) + "...";
        }
        return leftPad() + "| " + String.format("%-" + INNER + "s", t) + " |";
    }

    public static void blank() {
        System.out.println();
    }

    /** Small welcome strip at startup. */
    public static void welcomeBanner() {
        blank();
        System.out.println(leftPad() + "+" + repeat('-', INNER + 2) + "+");
        System.out.println(panelLine("G4 Smart Library"));
        System.out.println(panelLine("Catalog, loans, fines, study rooms"));
        System.out.println(leftPad() + "+" + repeat('-', INNER + 2) + "+");
        blank();
    }

    /** Top of a numbered menu: title row + separator under it. */
    public static void menuStart(String title) {
        blank();
        System.out.println(leftPad() + "+" + repeat('-', INNER + 2) + "+");
        System.out.println(panelLine(title));
        System.out.println(leftPad() + "+" + repeat('-', INNER + 2) + "+");
    }

    public static void menuOption(int num, String label) {
        System.out.println(panelLine(String.format("%d) %s", num, label)));
    }

    /** Bottom border + choice prompt (call after all menuOption lines). */
    public static void menuEnd() {
        System.out.println(leftPad() + "+" + repeat('-', INNER + 2) + "+");
        choicePrompt();
    }

    /** Re-print the menu choice line after a bad or empty choice (same style as {@link #menuEnd}). */
    public static void choicePrompt() {
        System.out.print(leftPad() + "  Choice: ");
    }

    /** Same as menuEnd but without prompt (e.g. before nested questions). */
    public static void menuEndNoPrompt() {
        System.out.println(leftPad() + "+" + repeat('-', INNER + 2) + "+");
    }

    /** Section title for list pages (books, journals, …). */
    public static void sectionTitle(String title) {
        blank();
        String base = leftPad() + "  ";
        System.out.println(base + "-- " + title + " --");
        System.out.println(base + repeat('-', Math.min(56, title.length() + 8)));
    }

    public static void listItem(String line) {
        System.out.println(leftPad() + "  - " + line);
    }

    /** Numbered row (menus, time slots) without a bullet. */
    public static void numberedLine(int n, String line) {
        System.out.println(leftPad() + String.format("%2d.  %s", n, line));
    }

    public static void muted(String line) {
        System.out.println(leftPad() + "    " + line);
    }

    /** Single-line form field label (keep on same line as user input). */
    public static void fieldPrompt(String label) {
        String prompt = label + ": ";
        System.out.print(leftPad() + "  " + prompt);
    }

    public static void ok(String msg) {
        System.out.println(leftPad() + "  [OK] " + msg);
    }

    public static void warn(String msg) {
        System.out.println(leftPad() + "  ! " + msg);
    }

    public static void info(String msg) {
        System.out.println(leftPad() + "  " + msg);
    }

    /** Pause between screens. */
    public static void pressEnterHint() {
        String prompt = "Press Enter to continue...";
        System.out.println(leftPad() + "  " + prompt);
    }

    /** Framed block title for database report blocks. */
    public static void reportHeader(String title) {
        blank();
        System.out.println(leftPad() + "\u250C" + hbar() + "\u2510");
        System.out.println(panelLine(title));
        System.out.println(leftPad() + "\u2514" + hbar() + "\u2518");
    }

    /** Reader / sub-screen divider. */
    public static void readerPageBar(String title, int page, int total) {
        System.out.println();
        System.out.println(leftPad() + repeat('\u2500', 48));
        System.out.println(leftPad() + title + "  \u2014  page " + page + "/" + total);
        System.out.println(leftPad() + repeat('\u2500', 48));
    }

    public static void readerControlsHint() {
        String prompt = "[n]ext  [p]rev  [q]uit or 0: ";
        System.out.print(leftPad() + "  " + prompt);
    }

    /** Formats a date for display (dd/mm/yyyy). */
    public static String formatDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        return DATE.format(date);
    }
}
