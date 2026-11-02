package ch.morgenblick.tv;

import android.app.*;
import android.content.*;

public final class Scheduler {
  public static final String PREFS = "morning";

  static PendingIntent operation(Context c, boolean test) {
    Intent i = new Intent(c, MainActivity.class).setAction(test ? "morning.TEST" : "morning.WAKE");
    i.putExtra("scheduled", true);
    return PendingIntent.getActivity(
        c, test ? 2 : 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  public static String apply(Context c) {
    AlarmManager a = c.getSystemService(AlarmManager.class);
    a.cancel(operation(c, false));
    android.content.SharedPreferences p = c.getSharedPreferences(PREFS, 0);
    if (!p.getBoolean("enabled", false)) return "Automatischer Start ist aus";
    if (android.os.Build.VERSION.SDK_INT >= 31 && !a.canScheduleExactAlarms())
      return "Berechtigung fuer Weckzeiten fehlt";
    long next =
        ScheduleCore.next(
            System.currentTimeMillis(),
            p.getInt("hour", 7),
            p.getInt("minute", 20),
            p.getInt("days", 127));
    try {
      a.setAlarmClock(
          new AlarmManager.AlarmClockInfo(next, operation(c, false)), operation(c, false));
    } catch (SecurityException e) {
      return "Berechtigung fuer Weckzeiten fehlt";
    }
    java.text.SimpleDateFormat f =
        new java.text.SimpleDateFormat("EEE, dd.MM. HH:mm", java.util.Locale.GERMAN);
    f.setTimeZone(ScheduleCore.ZONE);
    return "Naechster Start: " + f.format(new java.util.Date(next));
  }

  public static boolean test(Context c) {
    AlarmManager a = c.getSystemService(AlarmManager.class);
    if (android.os.Build.VERSION.SDK_INT >= 31 && !a.canScheduleExactAlarms()) return false;
    long at = System.currentTimeMillis() + 90000;
    try {
      a.setAlarmClock(new AlarmManager.AlarmClockInfo(at, operation(c, true)), operation(c, true));
      return true;
    } catch (SecurityException e) {
      return false;
    }
  }
}
