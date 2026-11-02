package ch.morgenblick.tv;

import java.util.Calendar;
import java.util.TimeZone;

public final class ScheduleCore {
  public static final TimeZone ZONE = TimeZone.getTimeZone("Europe/Zurich");

  // Bits 0..6: Monday..Sunday. Only future occurrences, using civil days across DST.
  public static long next(long now, int hour, int minute, int days) {
    if (hour < 0 || hour > 23 || minute < 0 || minute > 59 || (days & 127) == 0)
      throw new IllegalArgumentException("Zeit/Wochentage ungueltig");
    Calendar c = Calendar.getInstance(ZONE);
    c.setTimeInMillis(now);
    c.set(Calendar.HOUR_OF_DAY, hour);
    c.set(Calendar.MINUTE, minute);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    for (int i = 0; i < 8; i++) {
      int bit = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
      if (c.getTimeInMillis() > now && (days & (1 << bit)) != 0) return c.getTimeInMillis();
      c.add(Calendar.DAY_OF_MONTH, 1);
    }
    throw new IllegalArgumentException("Kein naechster Termin");
  }
}
