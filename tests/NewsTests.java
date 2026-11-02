package ch.morgenblick.tv;

import java.nio.file.*;
import java.util.*;

public class NewsTests {
  public static void main(String[] args) throws Exception {
    String xml =
        new String(Files.readAllBytes(Paths.get(args[0])), java.nio.charset.StandardCharsets.UTF_8);
    List<String[]> news = NewsCore.parse(xml);
    if (news.size() != 5) throw new AssertionError("Expected 5 headlines");
    if (!news.get(0)[3].startsWith("https://images.tagesschau.de/")
        || !news.get(0)[3].contains("width=384")) throw new AssertionError("Image URL");
    if (news.get(0)[4].isEmpty()) throw new AssertionError("Attribution");
    String minimal = "<rss><channel><item><title>Nur Text</title></item></channel></rss>";
    if (!NewsCore.parse(minimal).get(0)[3].isEmpty()) throw new AssertionError("No image fallback");
    if (!NewsCore.parse("<rss><channel/></rss>").isEmpty()) throw new AssertionError("Empty feed");
    boolean reject = false;
    try {
      NewsCore.parse("<!DOCTYPE rss SYSTEM 'https://example.com/test'><rss/>");
    } catch (Exception e) {
      reject = true;
    }
    if (!reject) throw new AssertionError("DTD");
    System.out.println("6 news/image checks passed");
  }
}
