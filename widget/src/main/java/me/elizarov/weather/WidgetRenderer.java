package me.elizarov.weather;

import android.app.UiModeManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Renders the locally generated layout with system time and public RSS weather.
 */
public final class WidgetRenderer {
    private WidgetRenderer() {
    }

    public static boolean lightText(Context context) {
        int desktop = Settings.Secure.getInt(context.getContentResolver(), "alive_desktop_preference", 3);
        int primary;
        if (desktop == 3) {
            primary = Settings.Global.getInt(context.getContentResolver(), "wallpaper_recommend_color", Color.BLACK);
        } else {
            UiModeManager modes = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
            primary = modes != null && modes.getNightMode() == UiModeManager.MODE_NIGHT_YES ? Color.WHITE : Color.BLACK;
        }
        return ((Color.red(primary) * 299 + Color.green(primary) * 587 + Color.blue(primary) * 114) / 1000) > 127;
    }

    public static RemoteViews views(Context context, long now, boolean lightText) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.weather);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        views.setTextViewText(R.id.appwidget_data_month, String.format(Locale.US, "%02d", calendar.get(Calendar.MONTH) + 1));
        views.setTextViewText(R.id.appwidgetr_data_day, String.format(Locale.US, "%02d", calendar.get(Calendar.DAY_OF_MONTH)));
        views.setTextViewText(R.id.appwidget_week, context.getResources().getStringArray(R.array.weekdays)[calendar.get(Calendar.DAY_OF_WEEK) - 1]);
        views.setTextViewText(R.id.appwidget_lunar_data, " ");
        views.setTextViewText(R.id.appwidgetr_time,
                new SimpleDateFormat(DateFormat.is24HourFormat(context) ? "HH:mm" : "hh:mm", Locale.getDefault()).format(new Date(now)));
        int color = lightText ? 0xf2ffffff : 0xf2000000;
        for (int id : new int[]{R.id.appwidget_data_month, R.id.appwidgetr_data_day, R.id.appwidget_week, R.id.appwidget_lunar_data, R.id.appwidgetr_time}) {
            views.setTextColor(id, color);
        }
        views.setInt(R.id.appwidget_data_month_divide, "setBackgroundResource",
                lightText ? R.drawable.appwidget_data_divider_light : R.drawable.appwidget_data_divider);
        ForecastWindow window = ForecastWindow.at(now);
        java.util.List<WeatherForecast> forecasts = WeatherRepository.forecasts(context);
        WeatherForecast day = WeatherForecast.forDate(forecasts, window.day);
        WeatherForecast night = WeatherForecast.forDate(forecasts, window.nightEnd);
        String dayText = day == null ? "—" : day.day + "°C";
        String[] range = window.nightRange().split("→", 2);
        String nightText = night == null ? "—" : night.night + "°C";
        views.setTextViewText(R.id.weather_day, dayText);
        views.setTextViewText(R.id.weather_night, nightText);
        // Fit negative temperatures and month boundaries without colliding with the weekday.
        android.text.TextPaint paint = new android.text.TextPaint();
        paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.create("Flyme Sans VF", android.graphics.Typeface.NORMAL), 500, false));
        float size = 48;
        paint.setTextSize(size);
        float width = android.text.Layout.getDesiredWidth("день", paint)
                + android.text.Layout.getDesiredWidth(dayText, paint) + android.text.Layout.getDesiredWidth(nightText, paint);
        size = Math.min(48, 256 * 48 / Math.max(1, width)); // Identical 12 px label/temperature gaps.
        views.setTextViewTextSize(R.id.weather_day, android.util.TypedValue.COMPLEX_UNIT_PX, size);
        views.setTextViewTextSize(R.id.weather_day_label, android.util.TypedValue.COMPLEX_UNIT_PX, size);
        views.setTextViewTextSize(R.id.weather_night, android.util.TypedValue.COMPLEX_UNIT_PX, size);
        views.setTextViewTextSize(R.id.weather_night_label, android.util.TypedValue.COMPLEX_UNIT_PX, size);
        paint.setTextSize(size);
        float labelWidth = (float) Math.ceil(android.text.Layout.getDesiredWidth("ночь", paint));
        views.setImageViewBitmap(R.id.weather_night_dates, NightRangeRenderer.render(context, range[0], range[1], labelWidth, (float) Math.ceil(android.text.Layout.getDesiredWidth(nightText, paint)), color));
        views.setContentDescription(R.id.weather_night_dates, window.nightRange());
        String dayCondition = WeatherConditions.get(context, window.day, false);
        String nightCondition = WeatherConditions.get(context, window.nightEnd, true);
        views.setImageViewResource(R.id.weather_day_icon, day == null ? R.drawable.weather_unknown : WeatherIcons.forCondition(dayCondition, false));
        views.setImageViewResource(R.id.weather_night_icon, night == null ? R.drawable.weather_unknown : WeatherIcons.forCondition(nightCondition, true));
        WeatherConfig config = WeatherConfig.read(context);
        views.setTextViewText(R.id.appwidget_city, day != null ? day.city : night != null ? night.city : config.cityName);
        String details = (day == null ? "Дневной прогноз недоступен." : "Днём " + day.day + " градусов. " + dayCondition + ".")
                + (night == null ? " Ночной прогноз недоступен." : " Ночью " + night.night + " градусов. " + nightCondition + ".")
                + " " + window.description() + ". Гидрометцентр России. Нажмите, чтобы обновить.";
        if (WeatherRepository.prefs(context).contains("error"))
            details += " Сохранённые данные; ошибка обновления.";
        views.setContentDescription(R.id.weather_forecasts, details);
        for (int id : new int[]{R.id.weather_day, R.id.weather_day_label, R.id.weather_night, R.id.weather_night_label,
                R.id.appwidget_city}) {
            views.setTextColor(id, color);
        }
        for (int id : new int[]{R.id.weather_day_icon, R.id.weather_night_icon, R.id.weather_settings}) {
            views.setInt(id, "setColorFilter", color);
        }
        views.setOnClickPendingIntent(R.id.weather_forecasts, PendingIntent.getBroadcast(context, 2,
                new Intent(context, WeatherWidgetProvider.class).setAction(WeatherRepository.REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        PendingIntent settings = PendingIntent.getBroadcast(context, lightText ? 3 : 4,
                new Intent(context, SettingsLaunchReceiver.class).putExtra("light_text", lightText),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.weather_settings, settings);
        views.setOnClickPendingIntent(R.id.appwidget_city, settings);
        return views;
    }
}
