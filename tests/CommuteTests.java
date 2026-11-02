package ch.morgenblick.tv;

import java.util.*;

public class CommuteTests {
  static DayData.Train train(long depart, long expected) {
    DayData.Train t = new DayData.Train();
    t.departure = depart;
    t.expected = expected;
    return t;
  }

  static void check(boolean b, String message) {
    if (!b) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    long now = 1000000;
    DayData.Train early = train(now + 5 * 60000, now + 5 * 60000),
        next = train(now + 15 * 60000, now + 20 * 60000);
    CommuteCore.Leave leave = CommuteCore.next(Arrays.asList(next, early), now);
    check(
        leave.train == next && leave.at == now + 5 * 60000,
        "ten-minute walk; earliest feasible plan");
    check(CommuteCore.next(Arrays.asList(early), now) == null, "too soon");
    check(
        CommuteCore.next(Arrays.asList(train(now + 10 * 60000, now + 10 * 60000)), now).at == now,
        "exact leave-now boundary");
    check(
        CommuteCore.next(Arrays.asList(train(now + 9 * 60000, now + 30 * 60000)), now) == null,
        "delay does not extend walking window");
    check(CommuteCore.next(Collections.emptyList(), now) == null, "no trains");
    System.out.println("5 walking-countdown checks passed");
  }
}
