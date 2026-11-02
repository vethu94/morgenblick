package ch.morgenblick.tv;

import android.content.*;

public final class BootReceiver extends BroadcastReceiver {
  public void onReceive(Context c, Intent i) {
    try {
      Scheduler.apply(c);
    } catch (Exception ignored) {
    }
  }
}
