package data;

import java.time.LocalDate;

// ISBN checks for books; password rules for registration.
public final class InputValidation {

    private InputValidation() {
    }

    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_LENGTH = 72;

    //return null if password is valid else reutnr message 
    public static String validatePassword(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Password cannot be empty.";
        }
        if (raw.length() < PASSWORD_MIN_LENGTH) {
            return "Password must be at least " + PASSWORD_MIN_LENGTH + " characters.";
        }
        if (raw.length() > PASSWORD_MAX_LENGTH) {
            return "Password must be at most " + PASSWORD_MAX_LENGTH + " characters.";
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
            }
            if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        if (!hasLetter) {
            return "Password must include at least one letter (A–Z or a–z).";
        }
        if (!hasDigit) {
            return "Password must include at least one digit (0–9).";
        }
        return null;
    }

    //Removes spaces and hyphens; uppercases trailing X for ISBN-10. 
    public static String normalizeIsbn(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isDigit(c)) {
                sb.append(c);
                continue;
            }
            if (c == 'x' || c == 'X') {
                sb.append('X');
            }
        }
        return sb.toString();
    }

   
    //if user enter valid isbn number then return eles return them error msg.
    public static String validateIsbn(String raw) {
        String s = normalizeIsbn(raw);
        if (s.isEmpty()) {
            return "You did not enter an ISBN. Type the 10- or 13-character code from the book (spaces and hyphens are fine).";
        }
        if (s.length() == 13) {
            if (!allDigits(s)) {
                return "ISBN-13 should be 13 digits only after removing spaces and hyphens. Letters are not allowed here.";
            }
            if (!isbn13ChecksumOk(s)) {
                return "This ISBN-13 fails the checksum (the last digit does not match the rest). Check for a typo.";
            }
            return null;
        }
        if (s.length() == 10) {
            if (!isbn10BodyOk(s)) {
                return "ISBN-10 must be nine digits plus a final digit or X (for example 0471958697).";
            }
            if (!isbn10ChecksumOk(s)) {
                return "This ISBN-10 fails the checksum. Check the digits and the last character (digit or X).";
            }
            return null;
        }
        return "After removing spaces and hyphens, the code must be exactly 10 characters (ISBN-10) or 13 digits (ISBN-13).";
    }

    private static boolean allDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isbn10BodyOk(String s) {
        for (int i = 0; i < 9; i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        char last = s.charAt(9);
        return Character.isDigit(last) || last == 'X';
    }

    private static boolean isbn10ChecksumOk(String s) {
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (s.charAt(i) - '0') * (10 - i);
        }
        char last = s.charAt(9);
        int v = last == 'X' ? 10 : (last - '0');
        sum += v;
        return sum % 11 == 0;
    }

    private static boolean isbn13ChecksumOk(String s) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int d = s.charAt(i) - '0';
            sum += (i % 2 == 0) ? d : 3 * d;
        }
        int check = s.charAt(12) - '0';
        int expected = (10 - (sum % 10)) % 10;
        return check == expected;
    }

    // validate and parse booking date used in study-room flow.
    public static LocalDate parseStudyBookingDate(String raw) {
        LocalDate date = Database.parseFlexibleDate(raw);
        if (date == null || date.isBefore(LocalDate.now())) {
            return null;
        }
        return date;
    }

    // human-friendly validation message for study-room booking date.
    public static String validateStudyBookingDate(String raw) {
        LocalDate parsed = Database.parseFlexibleDate(raw);
        if (parsed == null) {
            return "That date is invalid. Use day/month/year. For example 17/04/2026.";
        }
        if (parsed.isBefore(LocalDate.now())) {
            return "Cannot book a date in the past.";
        }
        return null;
    }
}
