package me.elizarov.weather;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.time.Instant;
import java.util.TimeZone;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Exercises real RemoteViews inflation, fonts, geometry, and timezone boundaries.
 */
public final class WidgetSmoke extends Instrumentation {
    private boolean settings;

    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        settings = args != null && "true".equals(args.getString("settings"));
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            if (settings) {
                result.putString("stream", SettingsChecks.run(this));
                finish(-1, result);
                return;
            }
            android.appwidget.AppWidgetProviderInfo registered = android.appwidget.AppWidgetManager
                    .getInstance(getTargetContext()).getInstalledProviders().stream()
                    .filter(info -> info.provider.equals(new android.content.ComponentName(
                            getTargetContext(), WeatherWidgetProvider.class)))
                    .findFirst().orElse(null);
            require(registered != null, "renamed weather provider is registered in Android");
            require(registered.initialLayout == R.layout.weather, "provider loads the renamed weather layout");
            LocationPolicyChecks.run(getTargetContext());
            SharedPreferences isolatedSettings = getTargetContext().getSharedPreferences("weather_config_test", Context.MODE_PRIVATE);
            Context configContext = new android.content.ContextWrapper(getTargetContext()) {
                @Override
                public SharedPreferences getSharedPreferences(String name, int mode) {
                    return name.equals("settings") ? isolatedSettings : super.getSharedPreferences(name, mode);
                }
            };
            isolatedSettings.edit().clear().commit();
            WeatherConfig defaults = WeatherConfig.read(configContext);
            require(defaults.fallbackStation.equals("27612") && defaults.cityName.equals("Москва")
                    && defaults.cityPath.isEmpty() && !defaults.automatic, "fresh settings default to manual Moscow");
            isolatedSettings.edit().putString("station_property", "26063").putString("auto_property", "1")
                    .putString("city_path", "russia/leningrad-region/sankt-peterburg")
                    .putString("city_name", "Санкт-Петербург").putBoolean("automatic", true).commit();
            WeatherConfig chosen = WeatherConfig.read(configContext);
            require(chosen.automatic && chosen.cityName.equals("Санкт-Петербург")
                            && chosen.cityPath.equals("russia/leningrad-region/sankt-peterburg"),
                    "saved dialog choices survive legacy property snapshots");
            isolatedSettings.edit().clear().commit();
            require(!RefreshPolicy.needsForecast(1000, 900, false, true, true, true), "fresh same-station forecast uses cache");
            require(RefreshPolicy.needsForecast(1000, 900, true, true, true, true), "manual refresh bypasses freshness");
            require(RefreshPolicy.needsForecast(1000, 900, false, false, true, true), "station change refreshes immediately");
            require(RefreshPolicy.needsForecast(3601000, 900, false, true, true, true), "hour-old forecast refreshes");
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            try (java.io.InputStream input = getContext().getAssets().open("spb.xml")) {
                byte[] buffer = new byte[2048];
                int count;
                while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            }
            String sample = bytes.toString("UTF-8");
            List<WeatherForecast> forecasts = WeatherForecast.parse(sample);
            org.json.JSONObject conditions = WeatherConditions.parse(asset("spb-details.html"), forecasts);
            require(conditions.getString("2026-11-17n").contains("без осадков"), "night conditions differ from the rainy daytime RSS summary");
            require(conditions.getString("2026-11-17d").contains("дождь"), "separate daytime conditions");
            require(WeatherIcons.forCondition(conditions.getString("2026-11-17n"), true) == R.drawable.weather_cloud,
                    "dry night must not get tomorrow's daytime rain icon");
            String retained = WeatherConditions.merge(conditions.toString(), "{}", forecasts);
            require(new org.json.JSONObject(retained).getString("2026-11-17n").equals(conditions.getString("2026-11-17n")),
                    "failed detail download must retain valid night conditions");
            String partial = WeatherConditions.merge(retained, "{\"2026-11-16d\":\"Ясно\"}", forecasts);
            require(new org.json.JSONObject(partial).getString("2026-11-16d").equals("Ясно")
                    && new org.json.JSONObject(partial).has("2026-11-17n"), "partial detail response preserves other periods");
            require(RefreshPolicy.nextCheck(1000, false, true) == 601000
                    && RefreshPolicy.nextCheck(1000, true, true) == 601000, "missing conditions retry in 10 minutes in both modes");
            SharedPreferences isolated = getTargetContext().getSharedPreferences("condition_cache_test", Context.MODE_PRIVATE);
            isolated.edit().clear().putString("station", "26063").putString("conditions", conditions.toString()).commit();
            WeatherConditions.remember(isolated);
            isolated.edit().remove("conditions").remove("station").commit();
            require(new org.json.JSONObject(WeatherConditions.cached(isolated, "26063")).has("2026-11-17n"),
                    "mode changes retain per-station conditions");
            require(WeatherConditions.cached(isolated, "27612").equals("{}"), "different cities never borrow each other's icons");
            isolated.edit().clear().commit();
            WeatherForecast spb = WeatherForecast.forDate(forecasts, LocalDate.of(2026, 11, 16));
            require(spb != null && spb.city.equals("Санкт-Петербург"), "RSS station name");
            require(spb.day == 4 && spb.night == 3, "RSS day/night temperatures");
            require(spb.summary(16).equals("днём +4°C · Облачно"), "forecast label, not current observations");
            require(spb.summary(23).equals("ночью +3°C · Облачно"), "night forecast selection");
            require(WeatherForecast.forTime(forecasts, LocalDate.of(2026, 11, 16), 23).night == 2,
                    "evening uses the coming night, not the past night");
            require(WeatherForecast.forDate(forecasts, LocalDate.of(2026, 11, 20)) == null, "expired forecast is not current weather");
            WeatherForecast negative = WeatherForecast.parse(sample.replace("ночью 3°", "ночью -13°").replace("днём 4°", "днём 0°")).get(0);
            require(negative.night == -13 && negative.day == 0, "negative/zero temperatures");
            WeatherForecast nextYear = WeatherForecast.parse(sample.replace("16 ноября", "1 января").replace("16.11.2026", "31.12.2026")).get(0);
            require(nextYear.date.equals(LocalDate.of(2027, 1, 1)), "RSS forecast year rollover");
            boolean malformedRejected = false;
            try {
                WeatherForecast.parse("<rss><channel/></rss>");
            } catch (IllegalArgumentException expected) {
                malformedRejected = true;
            }
            require(malformedRejected, "empty feeds must not overwrite the good cache");
            runOnMainSync(() -> {
                Context context = getTargetContext();
                TimeZone saved = TimeZone.getDefault();
                SharedPreferences prefs = WeatherRepository.prefs(context);
                String savedXml = prefs.getString("xml", null), savedError = prefs.getString("error", null), savedKey = prefs.getString("config_key", null), savedConditions = prefs.getString("conditions", null);
                try {
                    prefs.edit().putString("xml", sample).putString("conditions", conditions.toString()).remove("error").remove("config_key").commit();
                    TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"));
                    require(WeatherConditions.complete(conditions.toString(), Instant.parse("2026-11-16T12:00:00Z").toEpochMilli()),
                            "complete day/night details accepted");
                    require(!WeatherConditions.complete("{}", Instant.parse("2026-11-16T12:00:00Z").toEpochMilli()),
                            "empty conditions cannot be treated as a fresh complete forecast");
                    View daytime = render(context, "2026-11-16T12:14:00Z", true);
                    equal(daytime, R.id.weather_day, "4°C");
                    equal(daytime, R.id.weather_night, "2°C");
                    equal(daytime, R.id.weather_night_label, "ночь");
                    equal(daytime, R.id.weather_day_label, "день");
                    View dates = daytime.findViewById(R.id.weather_night_dates);
                    require(dates.getContentDescription().equals("16→17"), "night dates");
                    View nightLabel = daytime.findViewById(R.id.weather_night_label);
                    require(dates.getTop() >= nightLabel.getBottom(), "dates below night label");
                    View dayLabel = daytime.findViewById(R.id.weather_day_label);
                    View dayTemperature = daytime.findViewById(R.id.weather_day);
                    View nightTemperature = daytime.findViewById(R.id.weather_night);
                    View nightColumn = (View) nightLabel.getParent();
                    int dayGap = dayTemperature.getLeft() - dayLabel.getRight();
                    int nightGap = nightTemperature.getLeft() - nightLabel.getRight();
                    require(dayGap == 12 && nightGap == dayGap, "equal label-to-temperature gaps");
                    View dayIcon = daytime.findViewById(R.id.weather_day_icon);
                    require(nightColumn.getLeft() - dayIcon.getRight() == 24, "compact spacing between forecast sections");
                    rangeInkInside(dates);
                    android.graphics.Bitmap dateBitmap = ((android.graphics.drawable.BitmapDrawable)
                            ((android.widget.ImageView) dates).getDrawable()).getBitmap();
                    require(dateBitmap.getWidth() == dates.getWidth() && dateBitmap.getHeight() == dates.getHeight(),
                            "date bitmap is displayed without scaling");
                    int arrowCenter = Math.round(nightLabel.getLeft() + nightLabel.getWidth() / 2f);
                    require(android.graphics.Color.alpha(dateBitmap.getPixel(arrowCenter, 20)) > 0, "arrow under night label center");
                    View monthEnd = render(context, "2026-11-30T14:26:00Z", true);
                    View monthDates = monthEnd.findViewById(R.id.weather_night_dates);
                    require(monthDates.getContentDescription().equals("30.11→01.12"), "month boundary dates");
                    rangeInkInside(monthDates);
                    equal(daytime, R.id.appwidget_city, "Санкт-Петербург");
                    require(((View) daytime.findViewById(R.id.appwidget_city).getParent()).getVisibility() == View.VISIBLE,
                            "city row visible");
                    require(daytime.findViewById(R.id.weather_settings).getVisibility() == View.VISIBLE, "settings action visible");
                    View group = daytime.findViewById(R.id.weather_forecasts);
                    TextView weekday = daytime.findViewById(R.id.appwidget_week);
                    require(group.getLeft() >= weekday.getRight() + 24 && group.getRight() == 812,
                            "day/night line must not collide with weekday");
                    require(((View) group.getParent()).getTop() == 180, "weather stays below time");
                    View first = render(context, "2026-11-16T18:30:00Z", true);
                    equal(first, R.id.weather_night, "2°C");
                    equal(first, R.id.appwidget_data_month, "11");
                    equal(first, R.id.appwidgetr_data_day, "16");
                    equal(first, R.id.appwidget_week, "Пн");
                    equal(first, R.id.appwidgetr_time, "21:30");
                    View midnight = render(context, "2026-11-16T21:00:00Z", false);
                    equal(midnight, R.id.appwidgetr_data_day, "17");
                    equal(midnight, R.id.appwidget_week, "Вт");
                    equal(midnight, R.id.appwidgetr_time, "00:00");
                    equal(midnight, R.id.weather_day, "5°C");
                    equal(midnight, R.id.weather_night, "2°C");
                    require(midnight.findViewById(R.id.weather_night_dates).getContentDescription().equals("16→17"), "ongoing night after midnight");
                    View dawn = render(context, "2026-11-17T03:00:00Z", true);
                    require(dawn.findViewById(R.id.weather_night_dates).getContentDescription().equals("17→18"), "night rolls at 06:00");
                    require(ForecastWindow.at(Instant.parse("2026-11-30T14:26:00Z").toEpochMilli()).nightRange().equals("30.11→01.12"), "night month boundary");
                    TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
                    View west = render(context, "2027-01-01T02:30:00Z", true);
                    equal(west, R.id.appwidget_data_month, "12");
                    equal(west, R.id.appwidgetr_data_day, "31");
                    equal(west, R.id.appwidget_week, "Чт");
                    TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"));
                    View leap = render(context, "2028-02-28T21:01:00Z", true);
                    equal(leap, R.id.appwidgetr_data_day, "29");
                    TextView digits = first.findViewById(R.id.appwidget_data_month);
                    require(digits.getTextSize() == 130f, "OEM digits must be 130 physical px");
                    require(digits.getLineHeight() == 168, "OEM header line height");
                    require(((TextView) first.findViewById(R.id.appwidget_week)).getTextSize() == 48f, "OEM weekday size");
                    require(first.getMeasuredWidth() == 832 && first.getMeasuredHeight() == 600, "OEM root geometry");
                    require(((TextView) first.findViewById(R.id.appwidgetr_time)).getCurrentTextColor() == 0xf2ffffff, "light theme ink");
                    require(((TextView) midnight.findViewById(R.id.appwidgetr_time)).getCurrentTextColor() == 0xf2000000, "dark theme ink");
                    require(((TextView) midnight.findViewById(R.id.weather_day)).getCurrentTextColor() == 0xf2000000,
                            "temperature unit follows the theme too");
                    equal(leap, R.id.weather_day, "—");

                } finally {
                    TimeZone.setDefault(saved);
                    prefs.edit().putString("xml", savedXml).putString("conditions", savedConditions).putString("error", savedError).putString("config_key", savedKey).commit();
                }
            });
            result.putString("stream", "PASS: RemoteViews, OEM date/weather geometry and typography, day/night icons, city/settings, light/dark, forecast/error states, Android timezone midnight/year/leap boundaries; HMC RSS parsing, periods, expiry, negative/zero temperatures, forecast year rollover; 5 km coordinate grid, coarse-only request form, 10 km movement threshold, no time-based station requery\n");
            finish(-1, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
            finish(1, result);
        }
    }

    private View render(Context context, String instant, boolean light) {
        FrameLayout parent = new FrameLayout(context);
        View view = WidgetRenderer.views(context, Instant.parse(instant).toEpochMilli(), light).apply(context, parent);
        view.measure(View.MeasureSpec.makeMeasureSpec(832, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 832, 600);
        return view;
    }

    private void rangeInkInside(View view) {
        android.graphics.Bitmap bitmap = ((android.graphics.drawable.BitmapDrawable)
                ((android.widget.ImageView) view).getDrawable()).getBitmap();
        int top = bitmap.getHeight(), bottom = -1, left = bitmap.getWidth(), right = -1;
        for (int y = 0; y < bitmap.getHeight(); y++)
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 32) {
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
            }
        require(top >= 4 && bottom < bitmap.getHeight() - 4, "date glyphs have vertical headroom, no clipping");
        require(left > 0 && right < bitmap.getWidth() - 1, "date glyphs fit horizontally");
    }

    private String asset(String name) throws Exception {
        try (java.io.InputStream input = getContext().getAssets().open(name); java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return bytes.toString("UTF-8");
        }
    }

    private void equal(View root, int id, String expected) {
        String actual = ((TextView) root.findViewById(id)).getText().toString();
        require(expected.equals(actual), "expected " + expected + ", received " + actual);
    }

    private void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
