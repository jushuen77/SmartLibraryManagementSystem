package data;

import java.time.LocalDate;

public final class StudyBookingRow {
    public final int id;
    public final String roomName;
    public final LocalDate bookingDate;
    public final String slotLabel;

    public StudyBookingRow(int id, String roomName, LocalDate bookingDate, String slotLabel) {
        this.id = id;
        this.roomName = roomName;
        this.bookingDate = bookingDate;
        this.slotLabel = slotLabel;
    }
}
