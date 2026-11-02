package ch.morgenblick.tv;

import android.content.*;
import java.net.URI;

// Only system/shell senders holding android.permission.DUMP can configure this receiver.
// No credentials are written to diagnostics, broadcasts or activity Intent data.
public final class ConfigReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context c, Intent i) {
    if (!"ch.morgenblick.tv.SET_CALENDAR".equals(i.getAction())) return;
    String link = i.getStringExtra("calendar");
    try {
      if (link == null || link.length() > 8192) throw new IllegalArgumentException();
      URI u = new URI(link.trim());
      if (!"https".equalsIgnoreCase(u.getScheme())
          || !"calendar.google.com".equalsIgnoreCase(u.getHost())
          || u.getUserInfo() != null) throw new IllegalArgumentException();
      c.getSharedPreferences(Scheduler.PREFS, 0).edit().putString("calendar", link.trim()).apply();
      setResultCode(1);
      setResultData("Kalenderlink gespeichert");
    } catch (Exception e) {
      setResultCode(0);
      setResultData("Kalenderlink nicht gespeichert");
    }
  }
}
