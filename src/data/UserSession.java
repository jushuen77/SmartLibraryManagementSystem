package data;

import library.model.PatronType;

/**
 * Holds the signed-in library member (student, faculty, or public). Cleared on log out.
 */
public final class UserSession {

    private static String displayName;
    private static String email;
    private static PatronType memberType;

    private UserSession() {
    }

    public static void set(String name, String canonicalEmail, PatronType type) {
        displayName = name;
        email = canonicalEmail;
        memberType = type != null ? type : PatronType.PUBLIC;
    }

    public static void clear() {
        displayName = null;
        email = null;
        memberType = null;
    }

    public static boolean isLoggedIn() {
        return email != null && !email.isBlank();
    }

    public static String getDisplayName() {
        return displayName;
    }

    public static String getEmail() {
        return email;
    }

    /** Student, faculty, or public member; defaults to {@link PatronType#PUBLIC} if unset. */
    public static PatronType getMemberType() {
        return memberType != null ? memberType : PatronType.PUBLIC;
    }
}
