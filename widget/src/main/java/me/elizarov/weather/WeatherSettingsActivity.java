package me.elizarov.weather;

import me.elizarov.weather.hmc.HmcLocationCatalog;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.net.Uri;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.TypefaceSpan;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import java.util.EnumMap;
import java.util.List;

/**
 * Weather source, API key and location settings persist locally.
 */
public final class WeatherSettingsActivity extends Activity {
    static final int POPUP_WIDTH_DP = 1160;
    static final int POPUP_HEIGHT_DP = 900;
    private static final int ACCENT = 0xff45aea1;
    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private int ink, muted, panelColor, surface, activeSurface;
    private TextView selected, permission, searchMessage;
    private String versionName;
    private EditText query;
    private ListView results;
    private Switch automatic;
    private final EnumMap<WeatherSource, Button> sourceButtons = new EnumMap<>(WeatherSource.class);
    private LinearLayout locationControls, keyControls;
    private EditText apiKey;
    private WeatherSource keyEditorSource;
    private List<HmcLocationCatalog.HmcPlace> matches = java.util.Collections.emptyList();
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
        ink = dark ? 0xf2ffffff : 0xf2000000;
        muted = dark ? 0xb3ffffff : 0xb3000000;
        panelColor = dark ? 0xff2f343c : 0xffcdd7e2;
        surface = dark ? 0xff20252c : 0xffe3e9f0;
        activeSurface = dark ? 0xff5b6370 : Color.WHITE;
        FrameLayout root = new FrameLayout(this);
        root.setBackground(shape(panelColor, 12));
        root.setFocusableInTouchMode(true);
        root.setClipToOutline(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams column = new FrameLayout.LayoutParams(-1, -1);
        column.setMargins(dp(64), dp(32), dp(64), dp(32));
        root.addView(content, column);
        LinearLayout header = row();
        ImageButton back = new ImageButton(this);
        back.setTag("settings_back");
        back.setPadding(0, 0, 0, 0);
        back.setImageResource(R.drawable.ic_back);
        back.setColorFilter(ink);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setContentDescription("Назад");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(64), dp(64)));
        TextView title = text("Погода", 48, ink);
        title.setTypeface(MEDIUM);
        title.setTag("settings_title");
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titlePosition = new LinearLayout.LayoutParams(0, dp(64), 1);
        titlePosition.setMarginStart(dp(20));
        header.addView(title, titlePosition);
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception error) {
            versionName = "—";
        }
        ImageButton about = new ImageButton(this);
        about.setTag("about");
        about.setImageResource(R.drawable.ic_info);
        about.setColorFilter(ink);
        about.setPadding(dp(14), dp(14), dp(14), dp(14));
        about.setBackgroundColor(Color.TRANSPARENT);
        about.setContentDescription("О приложении");
        about.setOnClickListener(v -> showAbout());
        header.addView(about, new LinearLayout.LayoutParams(dp(64), dp(64)));
        header.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            android.graphics.Rect letters = new android.graphics.Rect();
            String heading = title.getText().toString();
            title.getPaint().getTextBounds(heading, 0, heading.length(), letters);
            float center = title.getTop() + title.getBaseline() + (letters.top + letters.bottom) / 2f;
            back.setTranslationY(center - back.getTop() - back.getHeight() / 2f);
            about.setTranslationY(center - about.getTop() - about.getHeight() / 2f);
        });
        content.addView(header);
        gap(content, 32);
        LinearLayout sourceRow = row();
        TextView sourceLabel = text("Источник", 34, ink);
        sourceLabel.setTag("source_label");
        sourceLabel.setGravity(Gravity.CENTER_VERTICAL);
        sourceRow.addView(sourceLabel, new LinearLayout.LayoutParams(-2, -1));
        LinearLayout choices = row();
        choices.setTag("source");
        for (WeatherSource source : WeatherSource.values()) {
            Button choice = sourceButton(source);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -1, 1);
            if (choices.getChildCount() != 0) size.setMarginStart(dp(24));
            choices.addView(choice, size);
            sourceButtons.put(source, choice);
        }
        LinearLayout.LayoutParams sourcePosition = new LinearLayout.LayoutParams(0, -1, 1);
        sourcePosition.setMarginStart(dp(48));
        sourceRow.addView(choices, sourcePosition);
        content.addView(sourceRow, new LinearLayout.LayoutParams(-1, dp(112)));
        keyControls = new LinearLayout(this);
        keyControls.setTag("api_key_controls");
        keyControls.setOrientation(LinearLayout.VERTICAL);
        content.addView(keyControls);
        gap(keyControls, 24);
        apiKey = new EditText(this);
        apiKey.setTag("api_key");
        apiKey.setTextSize(28);
        apiKey.setTextColor(ink);
        apiKey.setHintTextColor(muted);
        apiKey.setHint("Личный API-ключ");
        apiKey.setSingleLine(true);
        apiKey.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        apiKey.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                | android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        apiKey.setAutofillHints((String[]) null);
        apiKey.setPadding(dp(24), 0, dp(24), 0);
        apiKey.setBackground(shape(surface, 4));
        keyControls.addView(apiKey, new LinearLayout.LayoutParams(-1, dp(72)));
        LinearLayout keyActions = row();
        gap(keyControls, 12);
        Button save = new Button(this);
        save.setBackground(actionBackground(false));
        save.setStateListAnimator(null);
        save.setTextColor(ink);
        save.setPadding(dp(24), 0, dp(24), 0);
        save.setText("Сохранить ключ");
        save.setTextSize(26);
        save.setAllCaps(false);
        save.setOnClickListener(v -> {
            String value = apiKey.getText().toString().trim();
            if (!value.isEmpty() && !value.matches("[A-Za-z0-9_-]{8,256}")) {
                apiKey.setError("Проверьте формат ключа");
                return;
            }
            WeatherConfig.apiKey(this, value);
            apiKey.clearFocus();
            getSystemService(InputMethodManager.class).hideSoftInputFromWindow(apiKey.getWindowToken(), 0);
            Toast.makeText(this, "Ключ сохранён", Toast.LENGTH_SHORT).show();
        });
        keyActions.addView(save, new LinearLayout.LayoutParams(-2, dp(60)));
        TextView getKey = text("Получить личный ключ ↗", 26, ink);
        getKey.setPadding(dp(28), 0, 0, 0);
        getKey.setOnClickListener(v -> WeatherBrowserActivity.open(this, WeatherConfig.read(this).source.provider.keyRegistrationUrl));
        getKey.setGravity(Gravity.CENTER_VERTICAL);
        keyActions.addView(getKey, new LinearLayout.LayoutParams(-2, dp(60)));
        keyControls.addView(keyActions);
        locationControls = new LinearLayout(this);
        locationControls.setTag("location_controls");
        locationControls.setOrientation(LinearLayout.VERTICAL);
        content.addView(locationControls, new LinearLayout.LayoutParams(-1, 0, 1));
        content = locationControls;
        gap(content, 24);
        automatic = new Switch(this);
        styleSwitch();
        automatic.setText("Автоматическая локация");
        automatic.setTextSize(34);
        automatic.setTextColor(ink);
        automatic.setTag("automatic");
        automatic.setPadding(0, 0, 0, 0);
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
        gap(content, 12);
        selected = text("", 32, ink);
        selected.setTag("selected");
        content.addView(selected);
        gap(content, 12);
        query = new EditText(this);
        query.setTag("search");
        query.setTextSize(30);
        query.setTextColor(ink);
        query.setHintTextColor(muted);
        query.setHint("Поиск города или населённого пункта");
        query.setSingleLine(true);
        query.setPadding(dp(24), 0, dp(24), 0);
        query.setBackground(shape(surface, 4));
        content.addView(query, new LinearLayout.LayoutParams(-1, dp(76)));
        searchMessage = text("", 28, muted);
        searchMessage.setVisibility(View.GONE);
        searchMessage.setOnClickListener(v -> {
            HmcLocationCatalog.ensure(this);
            search();
        });
        content.addView(searchMessage);
        results = new ListView(this);
        results.setTag("locations");
        results.setDivider(new android.graphics.drawable.ColorDrawable((ink & 0x00ffffff) | 0x19000000));
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
                HmcLocationCatalog.HmcPlace place = matches.get(position);
                LinearLayout item = new LinearLayout(WeatherSettingsActivity.this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setPadding(dp(24), dp(12), dp(24), dp(12));
                item.addView(text(place.name, 32, ink));
                item.addView(text(place.detail(), 24, muted));
                return item;
            }
        });
        results.setOnItemClickListener((parent, view, position, id) -> {
            HmcLocationCatalog.HmcPlace place = matches.get(position);
            WeatherConfig.select(this, place.path, place.name);
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
        HmcLocationCatalog.ensure(this);
        updateControls();
        search();
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(color);
        result.setCornerRadius(dp(radius));
        return result;
    }

    private RippleDrawable actionBackground(boolean selectable) {
        StateListDrawable states = new StateListDrawable();
        if (selectable) {
            states.addState(new int[]{android.R.attr.state_selected}, shape(activeSurface, 4));
        }
        states.addState(new int[0], shape(surface, 4));
        return new RippleDrawable(ColorStateList.valueOf(0x4445aea1), states, null);
    }

    private Button sourceButton(WeatherSource source) {
        Button button = new Button(this);
        button.setTag("source_" + source.provider.id);
        button.setAllCaps(false);
        button.setSingleLine(false);
        button.setLetterSpacing(0);
        button.setTypeface(Typeface.DEFAULT);
        button.setTextSize(30);
        button.setTextColor(ink);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setStateListAnimator(null);
        button.setBackground(actionBackground(true));
        String title = source.provider.title;
        SpannableString label = new SpannableString(title + "\n" + source.periodCaption);
        int flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        label.setSpan(new TypefaceSpan(MEDIUM), 0, title.length(), flags);
        label.setSpan(new RelativeSizeSpan(26f / 30f), title.length() + 1, label.length(), flags);
        label.setSpan(new ForegroundColorSpan(muted), title.length() + 1, label.length(), flags);
        button.setText(label);
        button.setOnClickListener(v -> {
            if (button.isSelected()) return;
            WeatherConfig.source(this, source);
            HmcLocationCatalog.ensure(this);
            updateControls();
        });
        return button;
    }

    private void styleSwitch() {
        GradientDrawable thumb = shape(Color.WHITE, 2);
        thumb.setSize(dp(30), dp(42));
        automatic.setShowText(false);
        automatic.setThumbTintList(null);
        automatic.setTrackTintList(null);
        LayerDrawable paddedThumb = new LayerDrawable(new Drawable[]{thumb});
        int inset = dp(8);
        paddedThumb.setLayerInset(0, inset, inset, inset, inset);
        automatic.setThumbDrawable(paddedThumb);
        StateListDrawable track = new StateListDrawable();
        track.addState(new int[]{android.R.attr.state_checked}, shape(ACCENT, 4));
        track.addState(new int[0], shape(0xff68717d, 4));
        automatic.setTrackDrawable(track);
        automatic.setSwitchMinWidth(dp(96));
        automatic.setSplitTrack(false);
    }

    private void showAbout() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout column = new LinearLayout(this);
        column.setTag("about_popup");
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.setPadding(dp(36), dp(32), dp(36), dp(32));
        column.setBackground(shape(panelColor, 12));
        TextView version = text("Версия " + versionName, 32, ink);
        version.setTag("about_version");
        version.setGravity(Gravity.CENTER);
        column.addView(version, new LinearLayout.LayoutParams(-1, -2));
        gap(column, 16);
        long updated = WeatherRepository.lastUpdate(this);
        String stamp = updated == 0 ? "—" : new java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.ROOT)
                .format(new java.util.Date(updated));
        TextView update = text("Последнее обновление прогноза " + stamp, 27, muted);
        update.setTag("about_updated");
        update.setGravity(Gravity.CENTER);
        column.addView(update, new LinearLayout.LayoutParams(-1, -2));
        gap(column, 30);
        TextView open = text("Открытый исходный код", 30, ink);
        open.setGravity(Gravity.CENTER);
        column.addView(open, new LinearLayout.LayoutParams(-1, -2));
        gap(column, 12);
        LinearLayout link = row();
        link.setTag("github_link");
        link.setGravity(Gravity.CENTER);
        link.setContentDescription("Исходный код на GitHub");
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_github);
        logo.setColorFilter(ink);
        link.addView(logo, new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView url = text("https://github.com/elizarov/flyme_weather_widget", 24, ink);
        url.setPaintFlags(url.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        LinearLayout.LayoutParams urlPosition = new LinearLayout.LayoutParams(-2, -2);
        urlPosition.setMarginStart(dp(12));
        link.addView(url, urlPosition);
        link.setOnClickListener(v -> WeatherBrowserActivity.open(this, "https://github.com/elizarov/flyme_weather_widget"));
        column.addView(link, new LinearLayout.LayoutParams(-1, dp(64)));
        dialog.setContentView(column);
        dialog.setCanceledOnTouchOutside(true);
        // Shade only this passenger settings window, never the driver's display.
        View settingsPanel = ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        settingsPanel.setForeground(new android.graphics.drawable.ColorDrawable(0x66000000));
        dialog.setOnDismissListener(ignored -> settingsPanel.setForeground(null));
        android.view.Window window = dialog.getWindow();
        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        android.view.WindowManager.LayoutParams anchor = getWindow().getAttributes();
        android.view.WindowManager.LayoutParams position = window.getAttributes();
        position.width = Math.min(dp(1000), anchor.width - dp(64));
        position.height = dp(390);
        position.gravity = Gravity.TOP | Gravity.END;
        position.x = anchor.x + (anchor.width - position.width) / 2;
        position.y = anchor.y + (anchor.height - position.height) / 2;
        window.setAttributes(position);
        dialog.show();
    }

    private void configurePopup() {
        setFinishOnTouchOutside(true);
        android.graphics.Rect screen = getWindowManager().getMaximumWindowMetrics().getBounds();
        android.view.Window window = getWindow();
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setElevation(dp(24));
        android.view.WindowManager.LayoutParams position = window.getAttributes();
        if (PreviewPopup.layoutInTask(this, position, dp(POPUP_WIDTH_DP), dp(POPUP_HEIGHT_DP))) {
            window.setAttributes(position);
            return;
        }
        position.width = Math.min(dp(POPUP_WIDTH_DP), Math.max(1, screen.width() - dp(80)));
        // L946: leave 48 px above the bottom app cards, matching their lower padding.
        position.height = Math.min(dp(POPUP_HEIGHT_DP), Math.max(1, screen.height() - dp(160)));
        position.gravity = Gravity.TOP | Gravity.END;
        position.x = Math.min(dp(512), Math.max(dp(32), (screen.width() - position.width) / 2));
        position.y = Math.min(dp(180), Math.max(dp(24), screen.height() - position.height - dp(40)));
        window.setAttributes(position);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(WeatherRepository.CHANGED);
        filter.addAction(HmcLocationCatalog.CHANGED);
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
        if (automatic == null) return;
        WeatherConfig config = WeatherConfig.read(this);
        updating = true;
        sourceButtons.forEach((source, button) -> button.setSelected(source == config.source));
        automatic.setChecked(config.automatic);
        updating = false;
        boolean usesKey = config.source.provider.requiresApiKey();
        keyControls.setVisibility(usesKey ? View.VISIBLE : View.GONE);
        if (usesKey && keyEditorSource != config.source) {
            keyEditorSource = config.source;
            apiKey.setText(config.apiKey);
        }
        selected.setText((config.automatic ? "Резервный город: " : "Город: ") + config.cityName);
        boolean fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean background = checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        permission.setVisibility(config.automatic && (!fine || !background) ? View.VISIBLE : View.GONE);
        permission.setText(fine ? "Для работы в фоне разрешите геолокацию «Всегда» →" : "Нет разрешения на геолокацию — используется выбранный город. Разрешить →");
    }

    private void search() {
        if (query == null || results == null) return;
        List<HmcLocationCatalog.HmcPlace> all = HmcLocationCatalog.snapshot(this);
        matches = HmcLocationCatalog.search(all, query.getText().toString());
        ((BaseAdapter) results.getAdapter()).notifyDataSetChanged();
        boolean searching = !query.getText().toString().trim().isEmpty();
        boolean noResults = matches.isEmpty();
        searchMessage.setVisibility(searching && noResults ? View.VISIBLE : View.GONE);
        searchMessage.setText(HmcLocationCatalog.loading() ? "Загрузка…" : all.isEmpty()
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
