package ch.morgenblick.tv;

import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.time.*;
import java.util.*;

public final class DayData {
  public static final String DEFAULT_FROM = "8507000", DEFAULT_TO = "8507100";

  public static String weatherUrl(double latitude, double longitude) {
    return "https://api.open-meteo.com/v1/forecast?latitude="
        + latitude
        + "&longitude="
        + longitude
        + "&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,wind_speed_10m_max&timezone=Europe%2FZurich&forecast_days=1";
  }

  public static String trainsUrl(String from, String to) {
    if (!from.matches("[0-9]{7,9}") || !to.matches("[0-9]{7,9}"))
      throw new IllegalArgumentException("Station ID");
    return "https://transport.opendata.ch/v1/connections?from="
        + from
        + "&to="
        + to
        + "&limit=8&transportations%5B%5D=train";
  }

  private static JsonNode json(String s) throws IOException {
    return new ObjectMapper().readTree(s);
  }

  private static String number(JsonNode n, String suffix) {
    return n == null || n.isNull()
        ? "–"
        : String.format(Locale.GERMAN, "%.0f", n.asDouble()) + suffix;
  }

  public static String[] weather(String s, long now) throws Exception {
    JsonNode root = json(s), daily = root.path("daily"), current = root.path("current");
    String today =
        Instant.ofEpochMilli(now).atZone(ScheduleCore.ZONE.toZoneId()).toLocalDate().toString();
    if (!today.equals(daily.path("time").path(0).asText()))
      throw new IOException("Prognose nicht fuer heute");
    if (daily.path("weather_code").path(0).isMissingNode()
        || daily.path("weather_code").path(0).isNull()) throw new IOException("Keine Prognose");
    String condition = description(daily.path("weather_code").path(0).asInt());
    String temperature =
        "Jetzt "
            + number(current.get("temperature_2m"), "°")
            + " · "
            + number(daily.path("temperature_2m_min").get(0), "°")
            + " bis "
            + number(daily.path("temperature_2m_max").get(0), "°");
    String rain = "Regenrisiko " + number(daily.path("precipitation_probability_max").get(0), " %");
    String wind = "Wind bis " + number(daily.path("wind_speed_10m_max").get(0), " km/h");
    String advice =
        RainAdvice.hint(
            daily.path("weather_code").path(0).asInt(),
            daily.path("precipitation_probability_max").path(0).asDouble(-1),
            daily.path("precipitation_sum").path(0).asDouble(-1));
    return new String[] {
      condition, temperature, rain, wind, daily.path("weather_code").path(0).asText(), advice
    };
  }

  public static String description(int code) {
    switch (code) {
      case 0:
        return "Sonnig";
      case 1:
        return "Überwiegend sonnig";
      case 2:
        return "Teilweise bewölkt";
      case 3:
        return "Bedeckt";
      case 45:
      case 48:
        return "Nebel";
      case 51:
      case 53:
      case 55:
        return "Nieselregen";
      case 56:
      case 57:
        return "Gefrierender Nieselregen";
      case 61:
      case 63:
      case 65:
        return "Regen";
      case 66:
      case 67:
        return "Gefrierender Regen";
      case 71:
      case 73:
      case 75:
      case 77:
        return "Schnee";
      case 80:
      case 81:
      case 82:
        return "Regenschauer";
      case 85:
      case 86:
        return "Schneeschauer";
      case 95:
        return "Gewitter";
      case 96:
      case 99:
        return "Gewitter mit Hagel";
      default:
        return "Wechselhaft";
    }
  }

  public static final class Train {
    public long departure, expected, arrival;
    public String line, platform, detail;
  }

  private static long epoch(JsonNode stop, String type) {
    JsonNode n = stop.get(type + "Timestamp");
    if (n != null && !n.isNull()) return n.asLong() * 1000L;
    return iso(stop.path(type).asText());
  }

  private static long iso(String s) {
    try {
      return OffsetDateTime.parse(s).toInstant().toEpochMilli();
    } catch (Exception e) {
      return 0;
    }
  }

  public static List<Train> trains(String s, long now) throws Exception {
    return trains(s, now, DEFAULT_FROM, DEFAULT_TO);
  }

  public static List<Train> trains(String s, long now, String fromId, String toId)
      throws Exception {
    JsonNode root = json(s);
    if (!root.path("connections").isArray()) throw new IOException("Keine Verbindungen");
    List<Train> result = new ArrayList<>();
    for (JsonNode c : root.path("connections")) {
      JsonNode from = c.path("from"), to = c.path("to");
      if (!fromId.equals(from.path("station").path("id").asText())
          || !toId.equals(to.path("station").path("id").asText())) continue;
      Train t = new Train();
      t.departure = epoch(from, "departure");
      t.arrival = epoch(to, "arrival");
      if (t.departure <= 0 || t.arrival <= 0) continue;
      long prognosis = iso(from.path("prognosis").path("departure").asText());
      JsonNode delay = from.get("delay");
      boolean known = delay != null && !delay.isNull();
      t.expected = prognosis > 0 ? prognosis : t.departure + (known ? delay.asLong() * 60000 : 0);
      if (t.expected < now) continue;
      long minutes = Math.round((t.expected - t.departure) / 60000.0);
      StringBuilder line = new StringBuilder();
      for (JsonNode product : c.path("products")) {
        if (line.length() > 0) line.append(" / ");
        line.append(product.asText());
      }
      t.line = line.length() == 0 ? "Zug" : line.toString();
      String platform = from.path("prognosis").path("platform").asText("");
      if (platform.isEmpty()) platform = from.path("platform").asText("");
      t.platform = platform.isEmpty() ? "Gleis offen" : "Gleis " + platform;
      int transfers = c.path("transfers").asInt();
      String change = transfers == 0 ? "direkt" : transfers + "× umsteigen";
      t.detail =
          (minutes > 0
                  ? "+" + minutes + " Min Prognose"
                  : (!known && prognosis == 0 ? "nur Fahrplan" : "Planzeit"))
              + " · "
              + change;
      result.add(t);
    }
    Collections.sort(result, (a, b) -> Long.compare(a.expected, b.expected));
    return result.size() > 4 ? new ArrayList<>(result.subList(0, 4)) : result;
  }
}
