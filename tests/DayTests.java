package ch.morgenblick.tv;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

public class DayTests {
  static void check(boolean b, String msg) {
    if (!b) throw new AssertionError(msg);
  }

  public static void main(String[] args) throws Exception {
    String weather =
        new String(Files.readAllBytes(Paths.get(args[0])), java.nio.charset.StandardCharsets.UTF_8);
    String trains =
        new String(Files.readAllBytes(Paths.get(args[1])), java.nio.charset.StandardCharsets.UTF_8);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode w = mapper.readTree(weather);
    long day =
        LocalDate.parse(w.path("daily").path("time").get(0).asText())
            .atStartOfDay(ScheduleCore.ZONE.toZoneId())
            .toInstant()
            .toEpochMilli();
    check(
        DayData.weather(weather, day).length == 6, "forecast with animation code and rain advice");
    boolean rejected = false;
    try {
      DayData.weather(weather, day + 172800000);
    } catch (Exception e) {
      rejected = true;
    }
    check(rejected, "stale forecast rejected");
    check(
        DayData.description(65).equals("Regen") && DayData.description(95).equals("Gewitter"),
        "WMO descriptions");
    ObjectNode root = (ObjectNode) mapper.readTree(trains);
    JsonNode first = root.path("connections").get(0);
    long departure = first.path("from").path("departureTimestamp").asLong() * 1000;
    List<DayData.Train> parsed = DayData.trains(trains, departure - 1000);
    check(!parsed.isEmpty() && parsed.size() <= 4, "next trains");
    check(parsed.get(0).platform.startsWith("Gleis "), "platform");
    check(parsed.get(0).expected >= departure, "future departure");
    check(DayData.trains(trains, Long.MAX_VALUE).isEmpty(), "past trains excluded");
    ObjectNode from = (ObjectNode) first.path("from");
    from.putNull("delay");
    ((ObjectNode) from.path("prognosis")).putNull("departure");
    check(
        DayData.trains(root.toString(), departure - 1000).get(0).detail.startsWith("nur Fahrplan"),
        "unknown delay not on time");
    from.put("delay", 3);
    ((ObjectNode) from.path("prognosis")).putNull("departure");
    check(
        DayData.trains(root.toString(), departure - 1000).get(0).detail.startsWith("+3 Min"),
        "delay fallback");
    ((ObjectNode) from.path("station")).put("id", "wrong");
    check(
        DayData.trains(root.toString(), departure - 1000).stream()
            .noneMatch(t -> t.departure == departure),
        "wrong station excluded");
    System.out.println("10 weather/train checks passed");
  }
}
