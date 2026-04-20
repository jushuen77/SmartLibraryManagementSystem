package data;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

//terminal layout de formatting
public final class ConsoleUi {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    //Text width between the vertical borders of a menu box. 
    private static final int INNER = 54;
    // Terminal width used for centering the whole UI block.
    private static final int TERMINAL_WIDTH = detectTerminalWidth();

    private ConsoleUi() {
    }

    private static String repeat(char c, int n) {
        return String.valueOf(c).repeat(Math.max(0, n));
    }

    private static String hbar() {
        return repeat('-', INNER + 2);
    }

    //try to read the current window like size dimension. if cannot read thne just use 120
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

    //left padding for the main ui. Using private cuz dont wanna accidentally edit it by accident.
    private static String leftPad() {
        int uiWidth = INNER + 6; // borders + side spaces
        int pad = Math.max(0, (TERMINAL_WIDTH - uiWidth) / 2);
        return repeat(' ', pad);
    }

    //left padding for free-form lines (tables/prompts/status) based on actual content width
    private static String leftPadForWidth(int contentWidth) {
        int pad = Math.max(0, (TERMINAL_WIDTH - Math.max(0, contentWidth)) / 2);
        return repeat(' ', pad);
    }

    // Pads/truncates one line inside a menu panel.
    private static String panelLine(String text) {
        String t = text == null ? "" : text;
        if (t.length() > INNER) {
            t = t.substring(0, INNER - 3) + "...";
        }
        return leftPad() + "| " + String.format("%-" + INNER + "s", t) + " |";
    }

    // Centered text variant for banner/menu titles only.
    private static String panelLineCentered(String text) {
        String t = text == null ? "" : text;
        if (t.length() > INNER) {
            t = t.substring(0, INNER - 3) + "...";
        }
        int left = Math.max(0, (INNER - t.length()) / 2);
        int right = Math.max(0, INNER - t.length() - left);
        return leftPad() + "| " + repeat(' ', left) + t + repeat(' ', right) + " |";
    }

    // Shared bordered title block so all headers match the main menu frame.
    private static void boxedTitle(String title) {
        System.out.println(leftPad() + "+" + hbar() + "+");
        System.out.println(panelLineCentered(title));
        System.out.println(leftPad() + "+" + hbar() + "+");
    }

    public static void blank() {
        System.out.println();
    }

    //clear screen method so the display looks cleaner generally
    public static void clearScreen() {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\u001b[2J\u001b[H");
                System.out.flush();
            }
        } catch (Exception e) {
            for (int i = 0; i < 48; i++) {
                System.out.println();
            }
        }
    }

    //this is to wait for user to press enter then only clear screen der
    public static void pauseThenClear(Scanner scn) {
        pressEnterHint();
        if (scn != null) {
            scn.nextLine();
        }
        clearScreen();
    }

    //welcome banner msg
    public static void welcomeBanner() {
        blank();
        System.out.println(leftPad() + "+" + repeat('-', INNER + 2) + "+");
        System.out.println(panelLineCentered("G4 Smart Library"));
        System.out.println(panelLineCentered("Catalog, Loans, Fines, Study Rooms, etc."));
        System.out.println(leftPad() + "+" + repeat('-', INNER + 2) + "+");
    }

    //Top of a numbered menu: title row + separator under it. 
    public static void menuStart(String title) {
        blank();
        System.out.println(leftPad() + "+" + repeat('-', INNER + 2) + "+");
        System.out.println(panelLineCentered(title));
        System.out.println(leftPad() + "+" + repeat('-', INNER + 2) + "+");
    }

    public static void menuOption(int num, String label) {
        System.out.println(panelLine(String.format("%d) %s", num, label)));
    }

    //Bottom border + choice prompt (call after all menuOption lines). 
    public static void menuEnd() {
        System.out.println(leftPad() + "+" + repeat('-', INNER + 2) + "+");
        choicePrompt();
    }

    //Re-print the menu choice line after a bad or empty choice (same style as {@link #menuEnd}). 
    public static void choicePrompt() {
        System.out.print(leftPad() + "  Enter choice: ");
    }

    //Section title for list pages (books, journals, …). 
    public static void sectionTitle(String title) {
        blank();
        boxedTitle(title);
    }

    public static void listItem(String line) {
        String text = "- " + line;
        System.out.println(leftPadForWidth(text.length()) + text);
    }

    // Numbered row (menus, time slots) without a bullet. 
    public static void numberedLine(int n, String line) {
        String text = String.format("%2d.  %s", n, line);
        System.out.println(leftPadForWidth(text.length()) + text);
    }

    public static void muted(String line) {
        String text = "  " + line;
        System.out.println(leftPadForWidth(text.length()) + text);
    }

    //Single-line form field label (keep on same line as user input). 
    public static void fieldPrompt(String label) {
        String prompt = label + ": ";
        System.out.print(leftPad() + "  " + prompt);
    }

    public static void ok(String msg) {
        String text = "[OK] " + msg;
        System.out.println(leftPadForWidth(text.length()) + text);
    }

    public static void warn(String msg) {
        String text = "! " + msg;
        System.out.println(leftPadForWidth(text.length()) + text);
    }

    public static void info(String msg) {
        System.out.println(leftPadForWidth(msg.length()) + msg);
    }

    //pause between screens. 
    public static void pressEnterHint() {
        String prompt = "Press Enter to continue...";
        System.out.println(leftPadForWidth(prompt.length()) + prompt);
    }

    //Framed block title for database report blocks.
    public static void reportHeader(String title) {
        blank();
        boxedTitle(title);
    }

    //Reader / sub-screen divider. 
    public static void readerPageBar(String title, int page, int total) {
        String text = title + " - page " + page + "/" + total;
        System.out.println();
        boxedTitle(text);
    }

    public static void readerControlsHint() {
        String prompt = "[q]uit: ";
        System.out.print(leftPad() + "  " + prompt);
    }

    // Formats a date for display (dd/mm/yyyy). 
    public static String formatDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        return DATE.format(date);
    }
}
