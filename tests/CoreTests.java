package ch.morgenblick.tv;

import java.time.*;
import java.util.*;

public class CoreTests {
  static void check(boolean b, String msg) {
    if (!b) throw new AssertionError(msg);
  }

  static long instant(String s) {
    return ZonedDateTime.parse(s + "[Europe/Zurich]").toInstant().toEpochMilli();
  }

  static List<CalendarCore.Entry> calendar(String events, String at) throws Exception {
    return CalendarCore.today(
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Morgenblick//Test//DE\r\n"
            + events
            + "END:VCALENDAR\r\n",
        instant(at));
  }

  static String event(String lines) {
    return "BEGIN:VEVENT\r\n" + lines.replace("\n", "\r\n") + "\r\nEND:VEVENT\r\n";
  }

  public static void main(String[] args) throws Exception {
    TimeZone.setDefault(ScheduleCore.ZONE);
    check(
        ScheduleCore.next(instant("2026-11-02T07:19:00+01:00"), 7, 20, 127)
            == instant("2026-11-02T07:20:00+01:00"),
        "today");
    check(
        ScheduleCore.next(instant("2026-11-02T07:20:00+01:00"), 7, 20, 127)
            == instant("2026-11-03T07:20:00+01:00"),
        "no past alarm");
    check(
        ScheduleCore.next(instant("2026-11-06T08:00:00+01:00"), 7, 20, 31)
            == instant("2026-11-09T07:20:00+01:00"),
        "weekdays");
    check(
        ScheduleCore.next(instant("2026-03-28T08:00:00+01:00"), 7, 20, 127)
            == instant("2026-03-29T07:20:00+02:00"),
        "spring DST");
    check(
        ScheduleCore.next(instant("2026-10-24T08:00:00+02:00"), 7, 20, 127)
            == instant("2026-10-25T07:20:00+01:00"),
        "autumn DST");
    String today = "2026-11-02T07:20:00+01:00";
    check(
        calendar(
                    event(
                        "UID:one\n"
                            + "DTSTART:20261102T080000Z\n"
                            + "DTEND:20261102T090000Z\n"
                            + "SUMMARY:Meeting"),
                    today)
                .get(0)
                .start
            == instant("2026-11-02T09:00:00+01:00"),
        "UTC conversion");
    check(
        calendar(
                event(
                    "UID:all\n"
                        + "DTSTART;VALUE=DATE:20261102\n"
                        + "DTEND;VALUE=DATE:20261103\n"
                        + "SUMMARY:Frei"),
                today)
            .get(0)
            .allDay,
        "all-day");
    check(
        calendar(
                event("UID:yesterday\nDTSTART;VALUE=DATE:20261101\nDTEND;VALUE=DATE:20261102"),
                today)
            .isEmpty(),
        "exclusive end");
    String recurring =
        event(
            "UID:series\n"
                + "DTSTART:20261026T080000Z\n"
                + "DTEND:20261026T090000Z\n"
                + "RRULE:FREQ=WEEKLY;BYDAY=MO\n"
                + "SUMMARY:Serie");
    check(calendar(recurring, today).size() == 1, "recurring");
    check(
        calendar(
                recurring.replace("SUMMARY:Serie", "EXDATE:20261102T080000Z\nSUMMARY:Serie"), today)
            .isEmpty(),
        "exdate");
    String exception =
        event(
            "UID:series\n"
                + "RECURRENCE-ID:20261102T080000Z\n"
                + "DTSTART:20261102T100000Z\n"
                + "DTEND:20261102T110000Z\n"
                + "SUMMARY:Verschoben");
    List<CalendarCore.Entry> moved = calendar(recurring + exception, today);
    check(
        moved.size() == 1 && moved.get(0).title.equals("Verschoben"), "override without duplicate");
    check(
        calendar(recurring + exception.replace("SUMMARY:Verschoben", "STATUS:CANCELLED"), today)
            .isEmpty(),
        "cancel override");
    check(
        calendar(event("UID:overnight\nDTSTART:20261101T230000Z\nDTEND:20261102T070000Z"), today)
                .size()
            == 1,
        "overnight overlap");
    check(
        calendar(event("UID:old\nDTSTART:20261101T080000Z\nDTEND:20261101T090000Z"), today)
            .isEmpty(),
        "old not shown");
    boolean rejected = false;
    try {
      CalendarCore.today("<html>Login</html>", instant(today));
    } catch (Exception e) {
      rejected = true;
    }
    check(rejected, "HTML is not empty calendar");
    String allSeries =
        event(
            "UID:allseries\n"
                + "DTSTART;VALUE=DATE:20260322\n"
                + "DTEND;VALUE=DATE:20260323\n"
                + "RRULE:FREQ=WEEKLY;BYDAY=SU\n"
                + "SUMMARY:Sonntag");
    check(calendar(allSeries, "2026-03-30T07:20:00+02:00").isEmpty(), "all-day spring boundary");
    check(calendar(allSeries, "2026-10-25T07:20:00+01:00").size() == 1, "all-day autumn boundary");
    String tzSeries =
        "BEGIN:VTIMEZONE\r\n"
            + "TZID:Europe/Zurich\r\n"
            + "BEGIN:STANDARD\r\n"
            + "DTSTART:19701025T030000\r\n"
            + "RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU\r\n"
            + "TZOFFSETFROM:+0200\r\n"
            + "TZOFFSETTO:+0100\r\n"
            + "END:STANDARD\r\n"
            + "BEGIN:DAYLIGHT\r\n"
            + "DTSTART:19700329T020000\r\n"
            + "RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU\r\n"
            + "TZOFFSETFROM:+0100\r\n"
            + "TZOFFSETTO:+0200\r\n"
            + "END:DAYLIGHT\r\n"
            + "END:VTIMEZONE\r\n"
            + event(
                "UID:tz\n"
                    + "DTSTART;TZID=Europe/Zurich:20260322T090000\n"
                    + "DTEND;TZID=Europe/Zurich:20260322T100000\n"
                    + "RRULE:FREQ=WEEKLY;BYDAY=SU\n"
                    + "SUMMARY:Uhrzeit");
    check(
        calendar(tzSeries, "2026-03-29T07:20:00+02:00").get(0).start
            == instant("2026-03-29T09:00:00+02:00"),
        "recurrence preserves local DST time");
    String many = "";
    for (int n = 20; n >= 1; n--)
      many +=
          event(
              "UID:e"
                  + n
                  + "\nDTSTART:202611"
                  + String.format(java.util.Locale.ROOT, "%02d", n + 2)
                  + "T080000Z\nDTEND:202611"
                  + String.format(java.util.Locale.ROOT, "%02d", n + 2)
                  + "T090000Z\nSUMMARY:Termin "
                  + n);
    String prefix = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Morgenblick//Test//DE\r\n",
        suffix = "END:VCALENDAR\r\n";
    List<CalendarCore.Entry> upcoming =
        CalendarCore.next(prefix + many + suffix, instant(today), 10);
    check(
        upcoming.size() == 10
            && upcoming.get(0).title.equals("Termin 1")
            && upcoming.get(9).title.equals("Termin 10"),
        "next ten sorted across days");
    List<CalendarCore.Entry> seriesNext =
        CalendarCore.next(prefix + recurring + suffix, instant(today), 10);
    check(seriesNext.size() == 10, "infinite recurrence bounded");
    check(
        seriesNext.get(9).start > instant("2026-12-01T00:00:00+01:00"),
        "no arbitrary near-term horizon");
    List<CalendarCore.Entry> ongoing =
        CalendarCore.next(
            prefix
                + event(
                    "UID:running\nDTSTART:20261102T060000Z\nDTEND:20261102T080000Z\nSUMMARY:Laeuft")
                + event(
                    "UID:expired\nDTSTART:20261102T040000Z\nDTEND:20261102T050000Z\nSUMMARY:Vorbei")
                + suffix,
            instant(today),
            10);
    check(
        ongoing.size() == 1 && ongoing.get(0).title.equals("Laeuft"),
        "ongoing included expired excluded");
    List<CalendarCore.Entry> ordering =
        CalendarCore.next(
            prefix
                + event("UID:later\nDTSTART;VALUE=DATE:20261110\nSUMMARY:Spaeter ganztags")
                + event("UID:sooner\nDTSTART:20261103T080000Z\nSUMMARY:Frueher")
                + suffix,
            instant(today),
            10);
    check(ordering.get(0).title.equals("Frueher"), "future all-day chronological ordering");
    check(
        CalendarCore.next(prefix + recurring + exception + suffix, instant(today), 10)
            .get(0)
            .title
            .equals("Verschoben"),
        "next series override");
    check(
        CalendarCore.next(
                prefix + event("UID:cancel\nDTSTART:20261103T080000Z\nSTATUS:CANCELLED") + suffix,
                instant(today),
                10)
            .isEmpty(),
        "next cancelled excluded");
    System.out.println("25 scheduling/calendar checks passed");
  }
}
