package ch.morgenblick.tv;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;

public final class VideoActivity extends Activity {
  private VideoView video;
  private TextView status;
  private boolean ready = false;

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    String url = getIntent().getStringExtra("url");
    try {
      java.net.URI u = new java.net.URI(url);
      if (!"https".equalsIgnoreCase(u.getScheme())
          || u.getHost() == null
          || !u.getHost().endsWith(".srf.ch")) throw new Exception();
    } catch (Exception e) {
      finish();
      return;
    }
    FrameLayout root = new FrameLayout(this);
    root.setBackgroundColor(Color.BLACK);
    setContentView(root);
    video = new VideoView(this);
    FrameLayout.LayoutParams vp = new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER);
    root.addView(video, vp);
    status = new TextView(this);
    status.setTextColor(Color.WHITE);
    status.setTextSize(20);
    status.setPadding(32, 24, 32, 24);
    status.setBackgroundColor(0xAA101B2B);
    status.setText("SRF Tagesschau wird geladen …");
    root.addView(status, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
    MediaController controls = new MediaController(this);
    controls.setAnchorView(video);
    video.setMediaController(controls);
    video.setOnPreparedListener(
        player -> {
          ready = true;
          status.setVisibility(View.GONE);
          video.start();
          controls.show(5000);
        });
    video.setOnErrorListener(
        (player, what, extra) -> {
          ready = false;
          status.setVisibility(View.VISIBLE);
          status.setText(
              "SRF-Video konnte nicht abgespielt werden. Zurück bringt dich zum Morgenblick.\n"
                  + "Quelle: SRF · Die Ausgabe kann vorübergehend nicht verfügbar sein.");
          return true;
        });
    video.setOnCompletionListener(player -> finish());
    video.setVideoURI(Uri.parse(url));
    video.requestFocus();
  }

  @Override
  public boolean onKeyDown(int key, android.view.KeyEvent event) {
    if (ready
        && (key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            || key == KeyEvent.KEYCODE_DPAD_CENTER
            || key == KeyEvent.KEYCODE_ENTER)) {
      if (video.isPlaying()) video.pause();
      else video.start();
      return true;
    }
    return super.onKeyDown(key, event);
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (video != null && video.isPlaying()) video.pause();
  }

  @Override
  protected void onDestroy() {
    if (video != null) video.stopPlayback();
    super.onDestroy();
  }
}
