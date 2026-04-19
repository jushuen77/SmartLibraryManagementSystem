package data;

//ISBN checks for books (ISBN-10 or ISBN-13). Hyphens and spaces are ignored.
public final class InputValidation {

    private InputValidation() {
    }

    //Removes spaces and hyphens; uppercases trailing X for ISBN-10. 
    public static String normalizeIsbn(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ' || c == '-') {
                continue;
            }
            if (c == 'x') {
                sb.append('X');
            } else {
                sb.append(c);
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
}
