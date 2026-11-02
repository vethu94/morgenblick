package ch.morgenblick.tv;

import java.util.*;

public final class CommuteCore {
  public static final int WALK_MINUTES = 10;

  public static final class Leave {
    public final DayData.Train train;
    public final long at;

    Leave(DayData.Train t, long a) {
      train = t;
      at = a;
    }
  }

  public static Leave next(List<DayData.Train> trains, long now) {
    return next(trains, now, WALK_MINUTES);
  }

  public static Leave next(List<DayData.Train> trains, long now, int walkMinutes) {
    if (walkMinutes < 1 || walkMinutes > 120) throw new IllegalArgumentException("Walking time");
    DayData.Train best = null;
    for (DayData.Train train : trains) {
      long leave = train.departure - walkMinutes * 60000L;
      if (leave < now) continue;
      if (best == null || train.departure < best.departure) best = train;
    }
    return best == null ? null : new Leave(best, best.departure - walkMinutes * 60000L);
  }
}
