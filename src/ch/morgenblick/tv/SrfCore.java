package ch.morgenblick.tv;

import java.io.*;
import java.net.URI;
import java.text.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

public final class SrfCore {
  public static final String FEED =
      "https://www.srf.ch/feed/podcast/hd/ff969c14-c5a7-44ab-ab72-14d4c9e427a9.xml";

  public static final class Episode {
    public final String title, url;
    public final long published;

    Episode(String t, String u, long p) {
      title = t;
      url = u;
      published = p;
    }
  }

  public static Episode latest(String xml, long now) throws Exception {
    if (xml.toUpperCase(Locale.ROOT).contains("<!DOCTYPE")) throw new IOException();
    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
    f.setNamespaceAware(true);
    f.setExpandEntityReferences(false);
    Document d = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    Episode latest = null;
    NodeList items = d.getElementsByTagName("item");
    for (int n = 0; n < items.getLength(); n++) {
      Element e = (Element) items.item(n);
      String title = "", url = "", date = "";
      for (Node node = e.getFirstChild(); node != null; node = node.getNextSibling())
        if (node instanceof Element) {
          Element el = (Element) node;
          String name = el.getLocalName() == null ? el.getNodeName() : el.getLocalName();
          if (name.equals("title") && title.isEmpty()) title = el.getTextContent().trim();
          if (name.equals("pubDate")) date = el.getTextContent().trim();
          if (name.equals("enclosure") && el.getAttribute("type").startsWith("video/"))
            url = el.getAttribute("url");
        }
      if (url.isEmpty() || date.isEmpty()) continue;
      URI uri = new URI(url);
      String host = uri.getHost();
      if (!"https".equalsIgnoreCase(uri.getScheme())
          || host == null
          || !(host.equalsIgnoreCase("srf.ch")
              || host.toLowerCase(Locale.ROOT).endsWith(".srf.ch"))) continue;
      long published;
      try {
        published =
            new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).parse(date).getTime();
      } catch (Exception badDate) {
        continue;
      }
      if (published <= now && (latest == null || published > latest.published))
        latest = new Episode(title, url, published);
    }
    if (latest == null) throw new IOException("Keine verfuegbare SRF-Ausgabe");
    return latest;
  }
}
