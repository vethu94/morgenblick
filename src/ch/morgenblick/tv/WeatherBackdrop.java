package ch.morgenblick.tv;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Small native vector animation: no videos, textures, downloads or per-frame allocations. */
public final class WeatherBackdrop extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final float[] x = new float[28], y = new float[28], speed = new float[28];
  private int code = -1;
  private boolean active = false;
  private final long origin = SystemClock.elapsedRealtime();
  private final Runnable frame =
      new Runnable() {
        public void run() {
          if (!active) return;
          invalidate();
          postDelayed(this, 66);
        }
      };

  public WeatherBackdrop(Context c) {
    super(c);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    setFocusable(false);
    setClickable(false);
    java.util.Random r = new java.util.Random(27);
    for (int n = 0; n < x.length; n++) {
      x[n] = r.nextFloat();
      y[n] = r.nextFloat();
      speed[n] = .04f + r.nextFloat() * .045f;
    }
  }

  public void setWeatherCode(int value) {
    code = value;
    invalidate();
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    resume();
  }

  @Override
  protected void onDetachedFromWindow() {
    active = false;
    removeCallbacks(frame);
    super.onDetachedFromWindow();
  }

  @Override
  protected void onWindowVisibilityChanged(int visibility) {
    super.onWindowVisibilityChanged(visibility);
    if (visibility == VISIBLE) resume();
    else {
      active = false;
      removeCallbacks(frame);
    }
  }

  private void resume() {
    if (isAttachedToWindow() && getWindowVisibility() == VISIBLE) {
      active = true;
      removeCallbacks(frame);
      post(frame);
    }
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    int w = getWidth(), h = getHeight();
    c.drawColor(Color.rgb(12, 19, 30));
    if (code < 0) return;
    float t = (SystemClock.elapsedRealtime() - origin) / 1000f;
    boolean rain = (code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95;
    boolean snow = (code >= 71 && code <= 77) || code == 85 || code == 86;
    if (code <= 2) {
      float sx = w * .74f, sy = h * .115f, r = w * .038f;
      paint.setColor(Color.argb(35, 255, 207, 128));
      c.drawCircle(sx, sy, r, paint);
      paint.setColor(Color.argb(20, 255, 207, 128));
      c.drawCircle(sx, sy, r * 1.55f, paint);
      paint.setColor(Color.argb(28, 255, 215, 146));
      paint.setStrokeWidth(3f);
      for (int n = 0; n < 10; n++) {
        double a = n * Math.PI / 5 + t * .015;
        c.drawLine(
            sx + (float) Math.cos(a) * r * 1.8f,
            sy + (float) Math.sin(a) * r * 1.8f,
            sx + (float) Math.cos(a) * r * 2.05f,
            sy + (float) Math.sin(a) * r * 2.05f,
            paint);
      }
    }
    if (code != 0) {
      cloud(c, ((t * 2.5f) % (w + 400)) - 200, h * .09f, w * .09f, 35);
      cloud(c, ((w * .46f + t * 1.7f) % (w + 500)) - 150, h * .16f, w * .12f, 26);
      cloud(c, ((w * .85f + t * 1.1f) % (w + 400)) - 100, h * .055f, w * .065f, 30);
    }
    if (rain || snow) {
      paint.setColor(snow ? Color.argb(42, 213, 230, 245) : Color.argb(46, 130, 183, 225));
      paint.setStrokeWidth(2f);
      for (int n = 0; n < x.length; n++) {
        float px = x[n] * w, py = ((y[n] + t * speed[n]) % 1f) * h;
        if (snow) c.drawCircle(px + (float) Math.sin(t * .4 + n) * 12, py, 2.5f, paint);
        else c.drawLine(px, py, px - 6, py + 18, paint);
      }
    }
  }

  private void cloud(Canvas c, float cx, float cy, float size, int alpha) {
    paint.setColor(Color.argb(alpha, 157, 182, 207));
    paint.setStyle(Paint.Style.FILL);
    c.drawRoundRect(cx - size, cy, cx + size, cy + size * .36f, size * .18f, size * .18f, paint);
    c.drawCircle(cx - size * .45f, cy, size * .32f, paint);
    c.drawCircle(cx + size * .1f, cy - size * .12f, size * .48f, paint);
    c.drawCircle(cx + size * .55f, cy + size * .05f, size * .28f, paint);
  }
}
