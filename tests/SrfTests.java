package ch.morgenblick.tv;

import java.nio.file.*;

public class SrfTests {
  public static void main(String[] args) throws Exception {
    String real =
        new String(Files.readAllBytes(Paths.get(args[0])), java.nio.charset.StandardCharsets.UTF_8);
    SrfCore.Episode episode = SrfCore.latest(real, Long.MAX_VALUE);
    if (!episode.url.startsWith("https://download-media.srf.ch/") || episode.title.isEmpty())
      throw new AssertionError("Official episode");
    String head = "<rss><channel>", tail = "</channel></rss>";
    String item =
        "<item><title>Ausgabe</title><pubDate>Mon, 02 Nov 2026 06:00:00 +0100</pubDate><enclosure"
            + " type='video/mp4' url='https://download-media.srf.ch/test.mp4'/></item>";
    if (!SrfCore.latest(head + item + tail, Long.MAX_VALUE).title.equals("Ausgabe"))
      throw new AssertionError("Title");
    boolean rejected = false;
    try {
      SrfCore.latest(head + item + tail, 0);
    } catch (Exception e) {
      rejected = true;
    }
    if (!rejected) throw new AssertionError("Future episode");
    rejected = false;
    try {
      SrfCore.latest(
          head + item.replace("download-media.srf.ch", "example.com") + tail, Long.MAX_VALUE);
    } catch (Exception e) {
      rejected = true;
    }
    if (!rejected) throw new AssertionError("External media host");
    rejected = false;
    try {
      SrfCore.latest(head + item.replace("https:", "http:") + tail, Long.MAX_VALUE);
    } catch (Exception e) {
      rejected = true;
    }
    if (!rejected) throw new AssertionError("Cleartext");
    System.out.println("5 SRF video checks passed");
  }
}
