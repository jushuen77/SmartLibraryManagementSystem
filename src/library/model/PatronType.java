package library.model;

public enum PatronType {
    STUDENT,
    FACULTY,
    PUBLIC;

    //Short label for menus and lists
    public String toDisplayString() {
        return switch (this) {
            case STUDENT -> "Student";
            case FACULTY -> "Faculty";
            case PUBLIC -> "Public member";
        };
    }

    // Max physical books this member may have checked out at once.
    public int maxConcurrentLoans() {
        return switch (this) {
            case STUDENT -> 10;
            case FACULTY -> 20;
            case PUBLIC -> 3;
        };
    }

    //basically takes the book catalog loan length and then adjust it to the member type length. so they will get diffrent loan duration
    public int effectiveLoanDays(int bookLoanDays) {
        int base = bookLoanDays < 1 ? 14 : bookLoanDays;
        return switch (this) {
            case STUDENT -> Math.min(base, 21);
            case FACULTY -> Math.min(Math.max(base, 21), 56);
            case PUBLIC -> Math.min(base, 7);
        };
    }
}
