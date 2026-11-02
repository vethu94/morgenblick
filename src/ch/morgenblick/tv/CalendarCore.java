package ch.morgenblick.tv;

import biweekly.*;
import biweekly.component.*;
import biweekly.io.TimezoneAssignment;
import biweekly.util.com.google.ical.compat.javautil.DateIterator;
import java.util.*;

public final class CalendarCore {
  public static final class Entry {
    public final long start, end;
    public final boolean allDay;
    public final String title;

    Entry(long s, long e, boolean a, String t) {
      start = s;
      end = e;
      allDay = a;
      title = t;
    }
  }

  public static List<Entry> today(String text, long now) throws Exception {
    Calendar d = Calendar.getInstance(ScheduleCore.ZONE);
    d.setTimeInMillis(now);
    d.set(Calendar.HOUR_OF_DAY, 0);
    d.set(Calendar.MINUTE, 0);
    d.set(Calendar.SECOND, 0);
    d.set(Calendar.MILLISECOND, 0);
    long day = d.getTimeInMillis();
    d.add(Calendar.DAY_OF_MONTH, 1);
    long tomorrow = d.getTimeInMillis();
    return between(text, day, tomorrow, 10000);
  }

  public static List<Entry> next(String text, long now, int limit) throws Exception {
    if (limit < 1 || limit > 100) throw new IllegalArgumentException("Ungueltige Anzahl");
    return between(text, now, Long.MAX_VALUE, limit);
  }

  private static List<Entry> between(String text, long day, long tomorrow, int limit)
      throws Exception {
    if (!text.trim().startsWith("BEGIN:VCALENDAR"))
      throw new IllegalArgumentException("Kein iCalendar");
    ICalendar cal = Biweekly.parse(text).first();
    if (cal == null) throw new IllegalArgumentException("Leerer Kalender");
    List<Entry> result = new ArrayList<>();
    Map<String, Set<Long>> overrides = new HashMap<>();
    for (VEvent e : cal.getEvents())
      if (e.getRecurrenceId() != null) {
        if (e.getRecurrenceId().getRange() != null)
          throw new IllegalArgumentException("Serienaenderung nicht unterstuetzt");
        String uid = e.getUid() == null ? "" : e.getUid().getValue();
        if (!overrides.containsKey(uid)) overrides.put(uid, new HashSet<Long>());
        overrides.get(uid).add(e.getRecurrenceId().getValue().getTime());
      }
    for (VEvent e : cal.getEvents()) {
      if (e.getDateStart() == null || (e.getStatus() != null && e.getStatus().isCancelled()))
        continue;
      long original = e.getDateStart().getValue().getTime();
      boolean all = !e.getDateStart().getValue().hasTime();
      long duration = all ? 86400000L : 0;
      if (e.getDateEnd() != null)
        duration = Math.max(0, e.getDateEnd().getValue().getTime() - original);
      else if (e.getDuration() != null)
        duration = Math.max(0, e.getDuration().getValue().toMillis());
      // Encode DATE durations as civil days, not 24-hour blocks across DST.
      if (all) {
        if (e.getDateEnd() != null) {
          java.time.LocalDate a =
              java.time.Instant.ofEpochMilli(original)
                  .atZone(ScheduleCore.ZONE.toZoneId())
                  .toLocalDate();
          java.time.LocalDate b =
              java.time.Instant.ofEpochMilli(e.getDateEnd().getValue().getTime())
                  .atZone(ScheduleCore.ZONE.toZoneId())
                  .toLocalDate();
          duration = Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(a, b)) * 86400000L;
        }
      }
      String title = e.getSummary() == null ? "Termin (ohne Titel)" : e.getSummary().getValue();
      if (e.getRecurrenceId() != null) {
        add(result, original, duration, all, title, day, tomorrow);
        continue;
      }
      TimeZone tz = ScheduleCore.ZONE;
      TimezoneAssignment assignment = cal.getTimezoneInfo().getTimezone(e.getDateStart());
      if (assignment != null) tz = assignment.getTimeZone();
      else if (e.getDateStart().getParameters().getTimezoneId() == null
          && !cal.getTimezoneInfo().isFloating(e.getDateStart())
          && !all) tz = TimeZone.getTimeZone("UTC");
      if (e.getRecurrenceRule() == null && e.getRecurrenceDates().isEmpty()) {
        add(result, original, duration, all, title, day, tomorrow);
        continue;
      }
      DateIterator it = e.getDateIterator(tz);
      it.advanceTo(new Date(day - duration - (all ? 3600000L : 0)));
      String uid = e.getUid() == null ? "" : e.getUid().getValue();
      Set<Long> except = overrides.get(uid);
      int before = result.size();
      for (int n = 0; it.hasNext() && result.size() - before < limit; n++) {
        if (n > 10000) throw new IllegalArgumentException("Zu viele Serientermine");
        long start = it.next().getTime();
        if (start >= tomorrow) break;
        if (except == null || !except.contains(start))
          add(result, start, duration, all, title, day, tomorrow);
      }
    }
    Collections.sort(result, (a, b) -> Long.compare(a.start, b.start));
    return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
  }

  private static void add(
      List<Entry> list,
      long start,
      long duration,
      boolean all,
      String title,
      long day,
      long tomorrow) {
    long end = start + duration;
    if (all) {
      Calendar c = Calendar.getInstance(ScheduleCore.ZONE);
      c.setTimeInMillis(start);
      c.add(Calendar.DAY_OF_MONTH, (int) (duration / 86400000L));
      end = c.getTimeInMillis();
    }
    if (start < tomorrow && (end > day || (duration == 0 && start >= day)))
      list.add(new Entry(start, end, all, title));
  }
}
