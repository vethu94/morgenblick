package ch.morgenblick.tv;

public final class RainAdvice {
  public static String hint(int code, double probability, double millimeters) {
    boolean rainy = (code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95;
    boolean snowy = (code >= 71 && code <= 77) || code == 85 || code == 86;
    if (snowy) return "Schnee in der Tagesprognose – warm und wetterfest anziehen.";
    if (rainy || probability >= 40 || millimeters >= 0.2)
      return "Schirm mitnehmen – Regen ist heute möglich.";
    return "Heute kein deutlicher Regenhinweis in der Prognose.";
  }
}
