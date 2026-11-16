package me.elizarov.weather;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import java.util.List;

/**
 * Settings persist locally; explicit Android properties remain deployment defaults.
 */
public final class WeatherSettingsActivity extends Activity {
    private int ink, muted, surface;
    private TextView selected, permission, searchMessage, versionLabel;
    private String versionName;
    private EditText query;
    private ListView results;
    private Switch automatic;
    private List<LocationCatalog.Place> matches = java.util.Collections.emptyList();
    private boolean updating;
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (WeatherRepository.CHANGED.equals(intent.getAction())) updateControls();
            search();
        }
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        boolean dark = getIntent().getBooleanExtra("light_text", WidgetRenderer.lightText(this));
        ink = dark ? 0xfff2f2f2 : 0xff141414;
        muted = dark ? 0xffaaaaaa : 0xff555555;
        surface = dark ? 0xff292929 : 0xffdddddd;
        FrameLayout root = new FrameLayout(this);
        android.graphics.drawable.GradientDrawable panel = new android.graphics.drawable.GradientDrawable();
        panel.setColor(dark ? 0xff1c1c1c : 0xffeeeeee);
        panel.setCornerRadius(dp(24));
        root.setBackground(panel);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams column = new FrameLayout.LayoutParams(-1, -1);
        column.setMargins(dp(40), dp(28), dp(40), dp(28));
        root.addView(content, column);
        LinearLayout header = row();
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setColorFilter(ink);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setContentDescription("Назад");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(64), dp(64)));
        TextView title = text("Погода", 44, ink);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(64), 1));
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception error) {
            versionName = "—";
        }
        versionLabel = text("", 26, muted);
        versionLabel.setTag("version");
        header.addView(versionLabel);
        content.addView(header);
        gap(content, 20);
        automatic = new Switch(this);
        automatic.setText("Автоматическая локация");
        automatic.setTextSize(36);
        automatic.setTextColor(ink);
        automatic.setTag("automatic");
        automatic.setPadding(dp(24), 0, dp(24), 0);
        content.addView(automatic, new LinearLayout.LayoutParams(-1, dp(80)));
        automatic.setOnCheckedChangeListener((button, checked) -> {
            if (updating) return;
            WeatherConfig.automatic(this, checked);
            updateControls();
            if (checked && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        });
        permission = text("", 28, muted);
        permission.setOnClickListener(v -> openLocationPermissions());
        content.addView(permission);
        gap(content, 22);
        selected = text("", 32, ink);
        selected.setTag("selected");
        content.addView(selected);
        gap(content, 24);
        query = new EditText(this);
        query.setTag("search");
        query.setTextSize(30);
        query.setTextColor(ink);
        query.setHintTextColor(muted);
        query.setHint("Поиск города или населённого пункта");
        query.setSingleLine(true);
        query.setPadding(dp(24), 0, dp(24), 0);
        query.setBackgroundColor(surface);
        content.addView(query, new LinearLayout.LayoutParams(-1, dp(76)));
        searchMessage = text("", 28, muted);
        searchMessage.setVisibility(View.GONE);
        searchMessage.setOnClickListener(v -> {
            LocationCatalog.ensure(this);
            search();
        });
        content.addView(searchMessage);
        results = new ListView(this);
        results.setTag("locations");
        results.setDividerHeight(dp(1));
        content.addView(results, new LinearLayout.LayoutParams(-1, 0, 1));
        results.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return matches.size();
            }

            @Override
            public Object getItem(int position) {
                return matches.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convert, ViewGroup parent) {
                LocationCatalog.Place place = matches.get(position);
                LinearLayout item = new LinearLayout(WeatherSettingsActivity.this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setPadding(dp(24), dp(12), dp(24), dp(12));
                item.addView(text(place.name, 32, ink));
                item.addView(text(place.detail(), 24, muted));
                return item;
            }
        });
        results.setOnItemClickListener((parent, view, position, id) -> {
            LocationCatalog.Place place = matches.get(position);
            WeatherConfig.select(this, place);
            query.setText("");
            query.clearFocus();
            getSystemService(InputMethodManager.class).hideSoftInputFromWindow(query.getWindowToken(), 0);
            updateControls();
        });
        query.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                search();
            }

            @Override
            public void afterTextChanged(Editable e) {
            }
        });
        setContentView(root);
        configurePopup();
        LocationCatalog.ensure(this); // The ONLY automatic catalog-refresh entry point.
        updateControls();
        search();
    }

    private void configurePopup() {
        setFinishOnTouchOutside(true);
        android.util.DisplayMetrics screen = getResources().getDisplayMetrics();
        android.view.Window window = getWindow();
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setElevation(dp(24));
        android.view.WindowManager.LayoutParams position = window.getAttributes();
        position.width = Math.min(dp(1160), screen.widthPixels - dp(80));
        // L946: leave 48 px above the bottom app cards, matching their lower padding.
        position.height = Math.min(dp(900), screen.heightPixels - dp(160));
        position.gravity = Gravity.TOP | Gravity.END;
        position.x = Math.min(dp(512), Math.max(dp(32), (screen.widthPixels - position.width) / 2));
        position.y = Math.min(dp(180), Math.max(dp(24), screen.heightPixels - position.height - dp(40)));
        window.setAttributes(position);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(WeatherRepository.CHANGED);
        filter.addAction(LocationCatalog.CHANGED);
        if (Build.VERSION.SDK_INT >= 33)
            registerReceiver(changes, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(changes, filter);
        updateControls();
        search();
    }

    @Override
    protected void onPause() {
        unregisterReceiver(changes);
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        WeatherRepository.configurationChanged(this);
        updateControls();
    }

    private void openLocationPermissions() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        else
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }

    private void updateControls() {
        WeatherConfig config = WeatherConfig.read(this);
        android.content.SharedPreferences weather = WeatherRepository.prefs(this);
        String station = weather.getString("config_key", "").equals(config.key) ? weather.getString("station", "") : "";
        String source = config.automatic && !station.isEmpty()
                ? ("location".equals(weather.getString("mode", "")) ? " · авто" : " · резерв") : "";
        versionLabel.setText("Версия " + versionName + " · ID " + (station.isEmpty() ? "—" : station) + source);
        updating = true;
        automatic.setChecked(config.automatic);
        updating = false;
        selected.setText((config.automatic ? "Резервный город: " : "Город: ") + config.cityName);
        boolean fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean background = checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        permission.setVisibility(config.automatic && (!fine || !background) ? View.VISIBLE : View.GONE);
        permission.setText(fine ? "Для работы в фоне разрешите геолокацию «Всегда» →" : "Нет разрешения на геолокацию — используется выбранный город. Разрешить →");
    }

    private void search() {
        if (query == null || results == null) return;
        List<LocationCatalog.Place> all = LocationCatalog.snapshot(this);
        matches = LocationCatalog.search(all, query.getText().toString());
        ((BaseAdapter) results.getAdapter()).notifyDataSetChanged();
        boolean searching = !query.getText().toString().trim().isEmpty();
        boolean noResults = matches.isEmpty();
        searchMessage.setVisibility(searching && noResults ? View.VISIBLE : View.GONE);
        searchMessage.setText(LocationCatalog.loading() ? "Загрузка…" : all.isEmpty()
                ? "Не удалось загрузить города. Повторить" : "Ничего не найдено");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setFontFeatureSettings("kern");
        view.setPadding(0, dp(5), 0, dp(5));
        return view;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private void gap(LinearLayout parent, int height) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }
}
