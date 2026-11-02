package ch.morgenblick.tv;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import org.xmlpull.v1.*;

public final class MainActivity extends Activity {
  private static final int BG = Color.rgb(12, 19, 30),
      CARD = Color.rgb(22, 33, 48),
      WHITE = Color.rgb(239, 244, 250),
      MUTED = Color.rgb(166, 184, 204),
      GOLD = Color.rgb(255, 208, 133);
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final ExecutorService workers = Executors.newFixedThreadPool(2);
  private android.content.SharedPreferences prefs;
  private LinearLayout root;
  private int generation = 0;
  private WeatherBackdrop backdrop;
  private int weatherCode = -1;
  private TextView commute;
  private List<DayData.Train> latestTrains = Collections.emptyList();
  private long trainsFetched = 0;
  private TextView plan, clock;
  private boolean settings = false, watchingVideo = false;
  private long opened;
  private final Runnable close = () -> finish();
  private final Runnable tick =
      new Runnable() {
        public void run() {
          if (clock != null) {
            clock.setText(format("HH:mm", System.currentTimeMillis()));
          }
          updateCommute();
          handler.postDelayed(this, 1000);
        }
      };
  private final Runnable refresh =
      new Runnable() {
        public void run() {
          if (!settings) dashboard();
          handler.postDelayed(this, 120000);
        }
      };

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    TimeZone.setDefault(ScheduleCore.ZONE);
    prefs = getSharedPreferences(Scheduler.PREFS, 0);
    setTurnScreenOn(true);
    setShowWhenLocked(true);
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    opened = System.currentTimeMillis();
    dashboard();
    Scheduler.apply(this);
    handler.post(tick);
    handler.postDelayed(refresh, 120000);
    handler.postDelayed(close, 300000);
  }

  @Override
  protected void onNewIntent(Intent i) {
    super.onNewIntent(i);
    setIntent(i);
    opened = System.currentTimeMillis();
    handler.removeCallbacks(close);
    handler.postDelayed(close, 300000);
    Scheduler.apply(this);
    dashboard();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (watchingVideo) {
      watchingVideo = false;
      handler.removeCallbacks(close);
      handler.postDelayed(close, 300000);
    }
    if (!settings && plan != null) plan.setText(Scheduler.apply(this));
  }

  @Override
  protected void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    workers.shutdownNow();
    super.onDestroy();
  }

  @Override
  public void onBackPressed() {
    if (settings) dashboard();
    else super.onBackPressed();
  }

  private int dp(float n) {
    return (int) (n * getResources().getDisplayMetrics().density + .5f);
  }

  private String format(String pattern, long time) {
    SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.GERMAN);
    f.setTimeZone(ScheduleCore.ZONE);
    return f.format(new Date(time));
  }

  private TextView text(String value, int size, int color) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setPadding(0, dp(4), 0, dp(4));
    return t;
  }

  private void base() {
    generation++;
    root = new LinearLayout(this);
    root.setOrientation(1);
    root.setPadding(dp(38), dp(22), dp(38), dp(18));
    root.setBackgroundColor(Color.TRANSPARENT);
    FrameLayout layers = new FrameLayout(this);
    backdrop = new WeatherBackdrop(this);
    backdrop.setWeatherCode(weatherCode);
    layers.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
    layers.addView(root, new FrameLayout.LayoutParams(-1, -1));
    setContentView(layers);
    clock = null;
    plan = null;
    commute = null;
    latestTrains = Collections.emptyList();
    trainsFetched = 0;
  }

  private Button button(String label, Runnable click) {
    Button b = new Button(this);
    b.setText(label);
    b.setTextSize(14);
    b.setAllCaps(false);
    b.setTextColor(WHITE);
    b.setPadding(dp(10), 0, dp(10), 0);
    b.setOnClickListener(v -> click.run());
    return b;
  }

  private LinearLayout panel(String title) {
    LinearLayout p = new LinearLayout(this);
    p.setOrientation(1);
    p.setPadding(dp(20), dp(12), dp(20), dp(12));
    GradientDrawable g = new GradientDrawable();
    g.setColor(CARD);
    g.setCornerRadius(dp(18));
    p.setBackground(g);
    TextView h = text(title, 22, GOLD);
    h.setTypeface(null, Typeface.BOLD);
    p.addView(h);
    return p;
  }

  private void dashboard() {
    settings = false;
    base();
    int version = generation;
    LinearLayout top = new LinearLayout(this);
    top.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout greeting = new LinearLayout(this);
    greeting.setOrientation(1);
    TextView hello = text("Guten Morgen", 32, WHITE);
    hello.setTypeface(null, Typeface.BOLD);
    greeting.addView(hello);
    greeting.addView(
        text(
            format("EEEE, d. MMMM yyyy", System.currentTimeMillis()) + "  ·  Europe/Zurich",
            15,
            MUTED));
    top.addView(greeting, new LinearLayout.LayoutParams(0, -2, 1));
    clock = text(format("HH:mm", System.currentTimeMillis()), 42, GOLD);
    top.addView(clock);
    top.addView(button("Einstellungen", () -> settingsPage()));
    root.addView(top);
    LinearLayout media = new LinearLayout(this);
    media.setGravity(Gravity.CENTER_VERTICAL);
    root.addView(media);
    final SrfCore.Episode[] selected = {null};
    TextView episodeLabel = text("SRF-Ausgabe wird gesucht …", 12, MUTED);
    Button play =
        button(
            "▶ SRF Tagesschau",
            () -> {
              SrfCore.Episode e = selected[0];
              if (e == null) {
                Toast.makeText(
                        this,
                        "SRF-Ausgabe noch nicht verfügbar. Bitte kurz warten.",
                        Toast.LENGTH_SHORT)
                    .show();
                return;
              }
              handler.removeCallbacks(close);
              watchingVideo = true;
              startActivity(
                  new Intent(this, VideoActivity.class)
                      .putExtra("url", e.url)
                      .putExtra("title", e.title));
            });
    media.addView(play);
    LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, -2, 1);
    ep.leftMargin = dp(12);
    media.addView(episodeLabel, ep);
    workers.execute(
        () -> {
          try {
            SrfCore.Episode e = SrfCore.latest(fetch(SrfCore.FEED), System.currentTimeMillis());
            handler.post(
                () -> {
                  if (version != generation || isFinishing()) return;
                  selected[0] = e;
                  episodeLabel.setText(
                      "Letzte verfügbare Ausgabe: "
                          + format("dd.MM.yyyy HH:mm", e.published)
                          + " · SRF · Start nur mit Play");
                });
          } catch (Exception e) {
            handler.post(
                () -> {
                  if (version == generation)
                    episodeLabel.setText("SRF-Ausgabe gerade nicht erreichbar");
                });
          }
        });
    LinearLayout columns = new LinearLayout(this);
    LinearLayout.LayoutParams columnArea = new LinearLayout.LayoutParams(-1, 0, 1);
    columnArea.topMargin = dp(15);
    root.addView(columns, columnArea);
    LinearLayout news = panel("Weltgeschehen"), calendar = panel("Nächste 10 Termine");
    ScrollView ns = new ScrollView(this);
    ns.setFillViewport(true);
    ns.addView(news);
    LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -1, 1.05f);
    np.rightMargin = dp(10);
    columns.addView(ns, np);
    ScrollView cs = new ScrollView(this);
    cs.setFillViewport(true);
    cs.addView(calendar);
    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -1, 1);
    cp.rightMargin = dp(10);
    columns.addView(cs, cp);
    LinearLayout dayPanel = panel(prefs.getString("weather_name", "Bern") + " heute");
    dayPanel.setPadding(dp(15), dp(12), dp(15), dp(12));
    ScrollView dayScroll = new ScrollView(this);
    dayScroll.setFillViewport(true);
    dayScroll.addView(dayPanel);
    columns.addView(dayScroll, new LinearLayout.LayoutParams(0, -1, 1));
    TextView weatherBody = text("Tagesprognose wird geladen …", 16, WHITE);
    dayPanel.addView(weatherBody);
    TextView rainAdvice = text("", 15, GOLD);
    dayPanel.addView(rainAdvice);
    TextView weatherSource = text("Wetter: Open-Meteo", 10, MUTED);
    dayPanel.addView(weatherSource);
    TextView trainHeading =
        text(
            prefs.getString("from_name", "Bern") + " → " + prefs.getString("to_name", "Thun"),
            20,
            GOLD);
    trainHeading.setTypeface(null, Typeface.BOLD);
    LinearLayout.LayoutParams thp = new LinearLayout.LayoutParams(-1, -2);
    thp.topMargin = dp(12);
    dayPanel.addView(trainHeading, thp);
    commute = text("Fußweg und Verbindungen werden geladen …", 15, GOLD);
    dayPanel.addView(commute);
    LinearLayout trains = new LinearLayout(this);
    trains.setOrientation(1);
    dayPanel.addView(trains);
    trains.addView(text("Verbindungen werden geladen …", 15, MUTED));
    workers.execute(
        () -> {
          try {
            String[] forecast =
                DayData.weather(
                    fetch(
                        DayData.weatherUrl(
                            Double.parseDouble(prefs.getString("latitude", "46.94887")),
                            Double.parseDouble(prefs.getString("longitude", "7.44744")))),
                    System.currentTimeMillis());
            handler.post(
                () -> {
                  if (version != generation || isFinishing()) return;
                  weatherCode = Integer.parseInt(forecast[4]);
                  backdrop.setWeatherCode(weatherCode);
                  weatherBody.setText(
                      forecast[0] + "\n" + forecast[1] + "\n" + forecast[2] + "\n" + forecast[3]);
                  rainAdvice.setText(forecast[5]);
                  weatherSource.setText(
                      "Prognose: Open-Meteo · Stand "
                          + format("HH:mm", System.currentTimeMillis()));
                });
          } catch (Exception | LinkageError e) {
            handler.post(
                () -> {
                  if (version == generation)
                    weatherBody.setText("Tagesprognose gerade nicht verfügbar");
                });
          }
        });
    loadTrains(trains, version);
    TextView newsBody = text("Schlagzeilen werden geladen …", 19, MUTED);
    news.addView(newsBody);
    TextView calBody = text("Termine werden geladen …", 19, MUTED);
    calendar.addView(calBody);
    plan = text(Scheduler.apply(this), 13, MUTED);
    root.addView(plan);
    root.addView(
        text(
            "Schließt nach 5 Minuten · Zurück zum Beenden · Nachrichten: tagesschau.de/Ausland",
            12,
            MUTED));
    workers.execute(
        () -> {
          try {
            String xml = fetch("https://www.tagesschau.de/ausland/index~rss2.xml");
            List<String[]> items = NewsCore.parse(xml);
            handler.post(
                () -> {
                  if (version != generation || isFinishing()) return;
                  news.removeView(newsBody);
                  if (items.isEmpty()) news.addView(text("Keine Schlagzeilen im Feed.", 18, MUTED));
                  for (String[] item : items) {
                    LinearLayout row = new LinearLayout(this);
                    row.setGravity(Gravity.TOP);
                    row.setPadding(dp(5), dp(6), dp(5), dp(6));
                    row.setFocusable(true);
                    row.setClickable(true);
                    row.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                    android.graphics.drawable.StateListDrawable selector =
                        new android.graphics.drawable.StateListDrawable();
                    GradientDrawable focused = new GradientDrawable();
                    focused.setColor(Color.rgb(32, 48, 66));
                    focused.setCornerRadius(dp(10));
                    focused.setStroke(dp(2), GOLD);
                    selector.addState(new int[] {android.R.attr.state_focused}, focused);
                    selector.addState(
                        new int[] {},
                        new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                    row.setBackground(selector);
                    row.setContentDescription(item[0] + ". OK öffnet den Artikel.");
                    row.setOnClickListener(v -> openArticle(item[1]));
                    LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
                    rp.bottomMargin = dp(15);
                    news.addView(row, rp);
                    if (!item[3].isEmpty()) {
                      LinearLayout picture = new LinearLayout(this);
                      picture.setOrientation(1);
                      LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(72), -2);
                      pp.rightMargin = dp(8);
                      row.addView(picture, pp);
                      ImageView iv = new ImageView(this);
                      iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                      iv.setBackgroundColor(BG);
                      iv.setContentDescription("Vorschaubild zur Meldung");
                      picture.addView(iv, new LinearLayout.LayoutParams(dp(72), dp(44)));
                      TextView attribution =
                          text(item[4].isEmpty() ? "Bild: tagesschau.de" : item[4], 9, MUTED);
                      attribution.setMaxLines(2);
                      picture.addView(attribution);
                      workers.execute(
                          () -> {
                            try {
                              Bitmap bitmap = fetchImage(item[3]);
                              handler.post(
                                  () -> {
                                    if (version == generation && !isFinishing())
                                      iv.setImageBitmap(bitmap);
                                    else if (bitmap != null) bitmap.recycle();
                                  });
                            } catch (Exception ignored) {
                              handler.post(
                                  () -> {
                                    if (version == generation) {
                                      iv.setVisibility(View.GONE);
                                      attribution.setText("Bild nicht verfügbar");
                                    }
                                  });
                            }
                          });
                    }
                    LinearLayout words = new LinearLayout(this);
                    words.setOrientation(1);
                    row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
                    TextView t = text(item[0], 18, WHITE);
                    t.setMaxLines(4);
                    words.addView(t);
                    String published = item[2];
                    try {
                      Date dt =
                          new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
                              .parse(published);
                      published = format("dd.MM. HH:mm", dt.getTime());
                    } catch (Exception ignored) {
                    }
                    words.addView(text(published + " · OK: Artikel", 12, MUTED));
                  }
                  news.addView(
                      text(
                          "Abgerufen um " + format("HH:mm", System.currentTimeMillis()),
                          12,
                          MUTED));
                });
          } catch (Exception e) {
            handler.post(
                () -> {
                  if (version == generation)
                    newsBody.setText(
                        "Nachrichten gerade nicht erreichbar. Keine alten Meldungen als aktuell"
                            + " angezeigt.");
                });
          }
        });
    String url = prefs.getString("calendar", "");
    if (url.isEmpty()) {
      calBody.setText(
          "Google-Kalender noch nicht verbunden.\n\n"
              + "Unter Einstellungen den privaten ICS-Link eintragen. Ohne Link werden keine"
              + " Termine abgerufen.");
    } else
      workers.execute(
          () -> {
            try {
              String ics = fetch(url);
              List<CalendarCore.Entry> entries =
                  CalendarCore.next(ics, System.currentTimeMillis(), 10);
              handler.post(
                  () -> {
                    if (version != generation || isFinishing()) return;
                    calendar.removeView(calBody);
                    if (entries.isEmpty())
                      calendar.addView(
                          text("Keine bevorstehenden Termine im verbundenen Kalender.", 20, WHITE));
                    int shown = 0;
                    for (CalendarCore.Entry e : entries) {
                      if (shown++ >= 10) break;
                      String time =
                          format("EEE, dd.MM.yyyy", e.start)
                              + " · "
                              + (e.allDay
                                  ? "Ganztägig"
                                  : format("HH:mm", e.start)
                                      + (e.start < System.currentTimeMillis() ? " · läuft" : ""));
                      LinearLayout bubble = new LinearLayout(this);
                      bubble.setOrientation(1);
                      bubble.setPadding(dp(14), dp(8), dp(14), dp(10));
                      GradientDrawable bubbleStyle = new GradientDrawable();
                      bubbleStyle.setColor(Color.rgb(32, 48, 66));
                      bubbleStyle.setCornerRadius(dp(14));
                      bubbleStyle.setStroke(dp(1), Color.rgb(49, 68, 87));
                      bubble.setBackground(bubbleStyle);
                      LinearLayout.LayoutParams bubbleParams =
                          new LinearLayout.LayoutParams(-1, -2);
                      bubbleParams.topMargin = dp(7);
                      bubbleParams.bottomMargin = dp(5);
                      calendar.addView(bubble, bubbleParams);
                      bubble.addView(text(time, 14, GOLD));
                      TextView t = text(e.title, 20, WHITE);
                      t.setMaxLines(3);
                      bubble.addView(t);
                    }
                    calendar.addView(
                        text(
                            "Google Kalender · Abgerufen "
                                + format("HH:mm", System.currentTimeMillis()),
                            12,
                            MUTED));
                  });
            } catch (Exception | LinkageError e) {
              handler.post(
                  () -> {
                    if (version == generation)
                      calBody.setText(
                          "Kalender konnte nicht sicher gelesen werden. Verbindung/ICS-Link prüfen."
                              + " Das bedeutet nicht, dass du keine Termine hast.");
                  });
            }
          });
  }

  private void openArticle(String address) {
    try {
      URI uri = new URI(address);
      String host = uri.getHost();
      if (!"https".equalsIgnoreCase(uri.getScheme())
          || host == null
          || !(host.equalsIgnoreCase("tagesschau.de")
              || host.toLowerCase(Locale.ROOT).endsWith(".tagesschau.de")))
        throw new IllegalArgumentException();
      Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(address));
      startActivity(intent);
      handler.removeCallbacks(close);
      watchingVideo = true;
    } catch (android.content.ActivityNotFoundException e) {
      Toast.makeText(
              this,
              "Kein TV-Browser gefunden. Bitte zuerst einen Browser installieren.",
              Toast.LENGTH_LONG)
          .show();
    } catch (Exception e) {
      Toast.makeText(
              this, "Dieser Nachrichtenlink konnte nicht geöffnet werden.", Toast.LENGTH_LONG)
          .show();
    }
  }

  private void updateCommute() {
    if (commute == null) return;
    long now = System.currentTimeMillis();
    int walk = prefs.getInt("walk_minutes", 10);
    if (trainsFetched == 0) {
      commute.setText(walk + " Min Fußweg · Zugdaten noch nicht verfügbar");
      return;
    }
    if (now - trainsFetched > 90000) {
      commute.setText("Zugzeiten werden aktualisiert …");
      return;
    }
    CommuteCore.Leave leave = CommuteCore.next(latestTrains, now, walk);
    if (leave == null) {
      commute.setText(
          "Für diese Abfahrten reichen "
              + walk
              + " Min Fußweg nicht mehr. Neue Verbindungen folgen.");
      return;
    }
    long remaining = leave.at - now;
    long minutes = (long) Math.ceil(remaining / 60000.0);
    String countdown = remaining <= 60000 ? "Jetzt losgehen" : "In " + minutes + " Min losgehen";
    commute.setText(
        countdown
            + "\n"
            + leave.train.line
            + " ab "
            + format("HH:mm", leave.train.departure)
            + " · los bis "
            + format("HH:mm", leave.at)
            + "\n"
            + walk
            + " Min Fußweg · ohne Puffer");
  }

  private void loadTrains(LinearLayout target, int version) {
    if (version != generation || isFinishing() || isDestroyed()) return;
    workers.execute(
        () -> {
          try {
            List<DayData.Train> connections =
                DayData.trains(
                    fetch(
                        DayData.trainsUrl(
                            prefs.getString("from_id", DayData.DEFAULT_FROM),
                            prefs.getString("to_id", DayData.DEFAULT_TO))),
                    System.currentTimeMillis(),
                    prefs.getString("from_id", DayData.DEFAULT_FROM),
                    prefs.getString("to_id", DayData.DEFAULT_TO));
            handler.post(
                () -> {
                  if (version != generation || isFinishing()) return;
                  latestTrains = connections;
                  trainsFetched = System.currentTimeMillis();
                  updateCommute();
                  target.removeAllViews();
                  if (connections.isEmpty())
                    target.addView(text("Keine kommenden Verbindungen gemeldet.", 15, MUTED));
                  for (DayData.Train train : connections) {
                    LinearLayout item = new LinearLayout(this);
                    item.setOrientation(1);
                    item.setPadding(dp(8), dp(5), dp(8), dp(6));
                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(Color.rgb(32, 48, 66));
                    bg.setCornerRadius(dp(10));
                    item.setBackground(bg);
                    LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2);
                    ip.bottomMargin = dp(7);
                    target.addView(item, ip);
                    TextView departure =
                        text(format("HH:mm", train.departure) + "  " + train.line, 20, WHITE);
                    departure.setTypeface(null, Typeface.BOLD);
                    item.addView(departure);
                    item.addView(
                        text(
                            train.platform + " · an " + format("HH:mm", train.arrival), 14, MUTED));
                    item.addView(
                        text(train.detail, 12, train.expected > train.departure ? GOLD : MUTED));
                  }
                  target.addView(
                      text(
                          "transport.opendata.ch · Stand "
                              + format("HH:mm", System.currentTimeMillis()),
                          10,
                          MUTED));
                });
          } catch (Exception | LinkageError e) {
            handler.post(
                () -> {
                  if (version == generation) {
                    latestTrains = Collections.emptyList();
                    trainsFetched = 0;
                    updateCommute();
                    target.removeAllViews();
                    target.addView(
                        text(
                            "Zugverbindungen gerade nicht verfügbar. Keine alten Abfahrten"
                                + " anzeigen.",
                            15,
                            MUTED));
                  }
                });
          }
        });
    handler.postDelayed(
        () -> {
          if (version == generation && !settings && !watchingVideo) loadTrains(target, version);
        },
        60000);
  }

  private EditText field(LinearLayout form, String label, String value) {
    form.addView(text(label, 15, MUTED));
    EditText edit = new EditText(this);
    edit.setSingleLine(true);
    edit.setTextColor(WHITE);
    edit.setTextSize(17);
    edit.setText(value);
    form.addView(edit);
    return edit;
  }

  private void settingsPage() {
    settings = true;
    base();
    root.addView(text("Morgenblick einrichten", 28, WHITE));
    ScrollView scroll = new ScrollView(this);
    LinearLayout form = new LinearLayout(this);
    form.setOrientation(1);
    scroll.addView(form);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    CheckBox enabled = new CheckBox(this);
    enabled.setText("TV morgens automatisch öffnen");
    enabled.setTextColor(WHITE);
    enabled.setTextSize(19);
    enabled.setChecked(prefs.getBoolean("enabled", false));
    form.addView(enabled);
    form.addView(text("Startzeit (Europe/Zurich, z. B. 07:20)", 17, MUTED));
    EditText time = new EditText(this);
    time.setSingleLine(true);
    time.setTextColor(WHITE);
    time.setText(
        String.format(
            Locale.ROOT, "%02d:%02d", prefs.getInt("hour", 7), prefs.getInt("minute", 20)));
    time.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
    form.addView(time);
    LinearLayout daysRow = new LinearLayout(this);
    CheckBox[] days = new CheckBox[7];
    String[] names = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
    for (int n = 0; n < 7; n++) {
      days[n] = new CheckBox(this);
      days[n].setText(names[n]);
      days[n].setTextColor(WHITE);
      days[n].setChecked((prefs.getInt("days", 127) & (1 << n)) != 0);
      daysRow.addView(days[n]);
    }
    form.addView(daysRow);
    form.addView(text("Privater Google-Kalender-Link (optional)", 17, MUTED));
    EditText calendar = new EditText(this);
    calendar.setTextColor(WHITE);
    calendar.setTextSize(15);
    calendar.setSingleLine(true);
    calendar.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    calendar.setText(prefs.getString("calendar", ""));
    calendar.setHint("https://calendar.google.com/calendar/ical/…/private-…/basic.ics");
    form.addView(calendar);
    form.addView(
        text(
            "Google Kalender im PC-Browser → Einstellungen → gewünschten Kalender wählen → Kalender"
                + " integrieren → Privatadresse im iCal-Format. Kalender nicht öffentlich machen."
                + " Wer den Link kennt, kann die freigegebenen Termine lesen. Der Link bleibt nur"
                + " im privaten App-Speicher; keine Cloud-Sicherung. Ohne Kalenderlink funktioniert"
                + " der Nachrichtenbildschirm trotzdem.",
            15,
            MUTED));
    form.addView(text("Wetterort und Koordinaten", 19, GOLD));
    EditText city = field(form, "Ort", prefs.getString("weather_name", "Bern"));
    EditText latitude = field(form, "Breitengrad", prefs.getString("latitude", "46.94887"));
    EditText longitude = field(form, "Längengrad", prefs.getString("longitude", "7.44744"));
    form.addView(text("Zugstrecke (Schweizer Stationsnummern)", 19, GOLD));
    EditText fromName = field(form, "Abfahrtsbahnhof", prefs.getString("from_name", "Bern"));
    EditText fromId =
        field(form, "Stationsnummer ab", prefs.getString("from_id", DayData.DEFAULT_FROM));
    EditText toName = field(form, "Zielbahnhof", prefs.getString("to_name", "Thun"));
    EditText toId =
        field(form, "Stationsnummer Ziel", prefs.getString("to_id", DayData.DEFAULT_TO));
    EditText walking =
        field(
            form, "Fußweg zum Bahnhof (Minuten)", String.valueOf(prefs.getInt("walk_minutes", 10)));
    form.addView(
        button(
            "Speichern",
            () -> {
              try {
                String[] parts = time.getText().toString().trim().split(":");
                if (parts.length != 2) throw new Exception();
                int hour = Integer.parseInt(parts[0]), minute = Integer.parseInt(parts[1]);
                int mask = 0;
                for (int n = 0; n < 7; n++) if (days[n].isChecked()) mask |= 1 << n;
                ScheduleCore.next(System.currentTimeMillis(), hour, minute, mask);
                String url = calendar.getText().toString().trim();
                if (!url.isEmpty()) {
                  URI u = new URI(url);
                  if (!"https".equalsIgnoreCase(u.getScheme())
                      || u.getHost() == null
                      || u.getUserInfo() != null) throw new Exception();
                }
                double lat = Double.parseDouble(latitude.getText().toString().trim()),
                    lon = Double.parseDouble(longitude.getText().toString().trim());
                int walk = Integer.parseInt(walking.getText().toString().trim());
                String fi = fromId.getText().toString().trim(),
                    ti = toId.getText().toString().trim();
                if (!Double.isFinite(lat)
                    || !Double.isFinite(lon)
                    || Math.abs(lat) > 90
                    || Math.abs(lon) > 180
                    || walk < 1
                    || walk > 120
                    || !fi.matches("[0-9]{7,9}")
                    || !ti.matches("[0-9]{7,9}")
                    || city.getText().toString().trim().isEmpty()
                    || fromName.getText().toString().trim().isEmpty()
                    || toName.getText().toString().trim().isEmpty()) throw new Exception();
                prefs
                    .edit()
                    .putString("weather_name", city.getText().toString().trim())
                    .putString("latitude", String.valueOf(lat))
                    .putString("longitude", String.valueOf(lon))
                    .putString("from_name", fromName.getText().toString().trim())
                    .putString("from_id", fi)
                    .putString("to_name", toName.getText().toString().trim())
                    .putString("to_id", ti)
                    .putInt("walk_minutes", walk)
                    .putBoolean("enabled", enabled.isChecked())
                    .putInt("hour", hour)
                    .putInt("minute", minute)
                    .putInt("days", mask)
                    .putString("calendar", url)
                    .apply();
                String status = Scheduler.apply(this);
                dashboard();
                Toast.makeText(this, status, Toast.LENGTH_LONG).show();
              } catch (Exception e) {
                Toast.makeText(
                        this,
                        "Bitte Zeit, Wochentage, Kalenderlink, Koordinaten, Stationsnummern und"
                            + " Gehzeit prüfen.",
                        Toast.LENGTH_LONG)
                    .show();
              }
            }));
    form.addView(
        button(
            "Berechtigung für Weckzeiten öffnen",
            () -> {
              if (Build.VERSION.SDK_INT >= 31) {
                try {
                  startActivity(
                      new Intent(
                          Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                          Uri.parse("package:" + getPackageName())));
                } catch (Exception e) {
                  Toast.makeText(
                          this,
                          "Diese TV-Software bietet keine Freigabe an. Automatisches Wecken ist"
                              + " noch nicht verfügbar.",
                          Toast.LENGTH_LONG)
                      .show();
                }
              }
            }));
    form.addView(
        button(
            "Einmaliger Wecktest in 90 Sekunden",
            () -> {
              if (Scheduler.test(this))
                new AlertDialog.Builder(this)
                    .setTitle("Wecktest geplant")
                    .setMessage(
                        "Schalte den TV jetzt mit der Fernbedienung in Standby. Morgenblick soll in"
                            + " 90 Sekunden öffnen. Funktioniert das nicht, ist das selbstständige"
                            + " Wecken auf dieser TV-Software noch nicht bestätigt. Der Test ändert"
                            + " die tägliche Planung nicht.")
                    .setPositiveButton("Verstanden", null)
                    .show();
              else
                Toast.makeText(
                        this,
                        "Bitte zuerst die Berechtigung für Weckzeiten erlauben.",
                        Toast.LENGTH_LONG)
                    .show();
            }));
    form.addView(
        text(
            "Die App kann Standby-Wecken anfordern, aber keinen vom Strom getrennten TV starten."
                + " Manche Philips-Energiesparmodi verhindern App-Wecker. Bildschirm und Kalender"
                + " bleiben höchstens fünf Minuten sichtbar. Androids Bildschirmschoner und"
                + " OLED-Schutz werden nicht abgeschaltet.",
            15,
            MUTED));
    form.addView(
        button(
            "Kalenderlink entfernen",
            () -> {
              prefs.edit().remove("calendar").apply();
              calendar.setText("");
              Toast.makeText(this, "Kalenderlink auf dem TV entfernt.", Toast.LENGTH_SHORT).show();
            }));
    form.addView(button("Zurück", () -> dashboard()));
  }

  static String fetch(String address) throws Exception {
    URL url = new URL(address);
    if (!url.getProtocol().equals("https")) throw new IOException();
    for (int redirect = 0; redirect < 4; redirect++) {
      HttpURLConnection c = (HttpURLConnection) url.openConnection();
      c.setConnectTimeout(8000);
      c.setReadTimeout(8000);
      c.setInstanceFollowRedirects(false);
      c.setRequestProperty("User-Agent", "Morgenblick/1.0 Personal-TV-Dashboard");
      try {
        int code = c.getResponseCode();
        if (code >= 300 && code < 400) {
          String location = c.getHeaderField("Location");
          if (location == null) throw new IOException();
          url = new URL(url, location);
          if (!url.getProtocol().equals("https")) throw new IOException();
          continue;
        }
        if (code != 200) throw new IOException();
        try (InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream()) {
          byte[] b = new byte[8192];
          int n;
          while ((n = in.read(b)) != -1) {
            out.write(b, 0, n);
            if (out.size() > 4 * 1024 * 1024 || Thread.currentThread().isInterrupted())
              throw new IOException();
          }
          return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        }
      } finally {
        c.disconnect();
      }
    }
    throw new IOException();
  }

  static Bitmap fetchImage(String address) throws Exception {
    URL url = new URL(address);
    if (!"https".equals(url.getProtocol())
        || !"images.tagesschau.de".equalsIgnoreCase(url.getHost())) throw new IOException();
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    c.setConnectTimeout(5000);
    c.setReadTimeout(5000);
    c.setInstanceFollowRedirects(false);
    try {
      if (c.getResponseCode() != 200) throw new IOException();
      try (InputStream in = c.getInputStream();
          ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) != -1) {
          out.write(chunk, 0, n);
          if (out.size() > 1024 * 1024 || Thread.currentThread().isInterrupted())
            throw new IOException();
        }
        byte[] bytes = out.toByteArray();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException();
        int width = options.outWidth, height = options.outHeight;
        options.inSampleSize = 1;
        while (width / options.inSampleSize > 640 || height / options.inSampleSize > 480)
          options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (b == null) throw new IOException();
        return b;
      }
    } finally {
      c.disconnect();
    }
  }
}
