package me.elizarov.weather;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Local full-display preview; uses the same RemoteViews as the real widget.
 */
public final class PreviewActivity extends Activity {
    private final android.os.Handler clock = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable minute = new Runnable() {
        @Override
        public void run() {
            render();
            scheduleMinute();
        }
    };
    private final android.content.BroadcastReceiver changes = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context c, android.content.Intent i) {
            render();
        }
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894);
    }

    @Override
    protected void onResume() {
        super.onResume();
        android.content.IntentFilter filter = new android.content.IntentFilter(WeatherRepository.CHANGED);
        if (Build.VERSION.SDK_INT >= 33)
            registerReceiver(changes, filter, android.content.Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(changes, filter);
        render();
        scheduleMinute();
    }

    private void scheduleMinute() {
        clock.removeCallbacks(minute);
        if (!getIntent().hasExtra("time_millis"))
            clock.postDelayed(minute, 60000 - System.currentTimeMillis() % 60000);
    }

    @Override
    protected void onPause() {
        clock.removeCallbacks(minute);
        unregisterReceiver(changes);
        super.onPause();
    }

    private void render() {
        boolean light = getIntent().getBooleanExtra("light_text", true);
        long now = getIntent().getLongExtra("time_millis", System.currentTimeMillis());
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(light ? Color.rgb(28, 28, 28) : Color.rgb(230, 230, 230));
        View widget = WidgetRenderer.views(this, now, light).apply(this, root);
        // Generic emulator has no OEM system font family. Preview only: use the
        // optional local font. The real widget uses OEM family names.
        if (Build.FINGERPRINT.startsWith("generic") || Build.MODEL.startsWith("sdk_")) {
            int font = getResources().getIdentifier("lynkco_display", "font", getPackageName());
            if (font != 0) {
                for (int id : new int[]{R.id.appwidget_data_month, R.id.appwidgetr_data_day, R.id.appwidgetr_time}) {
                    ((TextView) widget.findViewById(id)).setTypeface(getResources().getFont(font));
                }
            }
        }
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(832, 600, Gravity.TOP | Gravity.END);
        position.topMargin = 527 + 50; // Host margin plus OEM widget's own margin.
        position.setMarginEnd(432 + 80);
        root.addView(widget, position);
        setContentView(root);
    }
}
