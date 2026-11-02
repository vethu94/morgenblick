package ch.morgenblick.tv;

import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.regex.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

public final class NewsCore {
  public static List<String[]> parse(String xml) throws Exception {
    if (xml.toUpperCase(Locale.ROOT).contains("<!DOCTYPE"))
      throw new IOException("DTD nicht erlaubt");
    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
    f.setNamespaceAware(true);
    f.setExpandEntityReferences(false);
    Document d = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    NodeList nodes = d.getElementsByTagName("item");
    List<String[]> result = new ArrayList<>();
    for (int i = 0; i < nodes.getLength() && result.size() < 5; i++) {
      Element item = (Element) nodes.item(i);
      String title = child(item, "title");
      if (title.isEmpty()) continue;
      String link = child(item, "link"), date = child(item, "pubDate"), image = "", credit = "";
      String html = child(item, "encoded");
      Matcher img = Pattern.compile("(?is)<img\\b[^>]*>").matcher(html);
      if (img.find()) {
        Matcher src =
            Pattern.compile("(?is)\\bsrc\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(img.group());
        if (src.find()) image = src.group(1).replace("&amp;", "&");
        Matcher alt =
            Pattern.compile("(?is)\\balt\\s*=\\s*['\"]([^'\"]*)['\"]").matcher(img.group());
        if (alt.find()) {
          credit = alt.group(1).replace("&amp;", "&").replace("&quot;", "\"");
          int pipe = credit.lastIndexOf('|');
          if (pipe >= 0) credit = credit.substring(pipe + 1).trim();
        }
      }
      if (!image.isEmpty()) {
        URI uri = new URI(image);
        if (!"https".equalsIgnoreCase(uri.getScheme())
            || !"images.tagesschau.de".equalsIgnoreCase(uri.getHost())) image = "";
        else {
          image = image.replaceAll("([?&])width=\\d+", "$1width=384");
          if (!image.matches(".*[?&]width=\\d+.*"))
            image += (image.contains("?") ? "&" : "?") + "width=384";
        }
      }
      result.add(new String[] {title, link, date, image, credit});
    }
    return result;
  }

  private static String child(Element parent, String name) {
    for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling())
      if (n instanceof Element) {
        String local = n.getLocalName();
        if (name.equals(local == null ? n.getNodeName() : local)) return n.getTextContent().trim();
      }
    return "";
  }
}
