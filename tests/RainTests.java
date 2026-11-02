package ch.morgenblick.tv;

public class RainTests {
  public static void main(String[] args) {
    if (!RainAdvice.hint(61, 0, 0).startsWith("Schirm")) throw new AssertionError("rain code");
    if (!RainAdvice.hint(3, 60, 0).startsWith("Schirm")) throw new AssertionError("probability");
    if (!RainAdvice.hint(2, 0, 2).startsWith("Schirm")) throw new AssertionError("precipitation");
    if (!RainAdvice.hint(71, 90, 2).startsWith("Schnee")) throw new AssertionError("snow");
    if (RainAdvice.hint(0, 10, 0).startsWith("Schirm")) throw new AssertionError("dry");
    System.out.println("5 rain-advice checks passed");
  }
}
