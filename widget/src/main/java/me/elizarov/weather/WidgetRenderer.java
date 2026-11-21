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
        WeatherConfig config = WeatherConfig.read(context);
        ForecastProvider<?> provider = config.source.provider;
        WeatherCards cards = provider.cards(context, config, now);
        renderCard(context, views, cards.first, true, lightText, color);
        views.setViewVisibility(R.id.weather_second_card, config.showForecast ? android.view.View.VISIBLE : android.view.View.GONE);
        if (config.showForecast) renderCard(context, views, cards.second, false, lightText, color);
        views.setTextViewText(R.id.appwidget_city, cards.city);
        views.setTextColor(R.id.appwidget_city, color);
        views.setTextViewText(R.id.weather_credit, provider.credit);
        views.setTextColor(R.id.weather_credit, color);
        views.setInt(R.id.weather_settings, "setColorFilter", color);
        views.setInt(R.id.weather_website, "setColorFilter", color);
        views.setOnClickPendingIntent(R.id.weather_forecasts, viewsRefresh(context));
        PendingIntent settings = PendingIntent.getBroadcast(context, lightText ? 3 : 4,
                new Intent(context, SettingsLaunchReceiver.class).putExtra("light_text", lightText),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.weather_settings, settings);
        views.setOnClickPendingIntent(R.id.appwidget_city, settings);
        String url = provider.website(context, config);
        PendingIntent website = url.isEmpty() ? null : PendingIntent.getBroadcast(context, 5,
                new Intent(context, SettingsLaunchReceiver.class).putExtra("url", url),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setFloat(R.id.weather_website, "setAlpha", url.isEmpty() ? 0.4f : 1f);
        views.setOnClickPendingIntent(R.id.weather_website, website);
        views.setOnClickPendingIntent(R.id.weather_credit, website);
        return views;
    }

    private static void renderCard(Context c, RemoteViews views, WeatherCards.Card card,
                                   boolean first, boolean lightText, int color) {
        int temperature = first ? R.id.weather_first_temperature : R.id.weather_second_temperature;
        int caption = first ? R.id.weather_first_caption : R.id.weather_second_caption;
        int image = first ? R.id.weather_first_icon : R.id.weather_second_icon;
        int container = first ? R.id.weather_first_card : R.id.weather_second_card;
        views.setTextViewText(temperature, card.temperatureText());
        views.setImageViewBitmap(caption, CaptionRenderer.render(c, card.caption, color));
        views.setContentDescription(caption, card.caption);
        views.setTextColor(temperature, color);
        int icon = WeatherIcons.resource(c, card.icon, lightText);
        views.setViewVisibility(image, icon == 0 ? android.view.View.INVISIBLE : android.view.View.VISIBLE);
        if (icon != 0) views.setImageViewResource(image, icon);
        views.setContentDescription(container, card.caption + ": " + card.temperatureText() + ". " + card.description);
    }

    private static PendingIntent viewsRefresh(Context context) {
        return PendingIntent.getBroadcast(context, 2, new Intent(context, WeatherWidgetProvider.class).setAction(WeatherRepository.REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
