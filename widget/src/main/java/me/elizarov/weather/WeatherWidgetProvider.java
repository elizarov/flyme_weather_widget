package me.elizarov.weather;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

public final class WeatherWidgetProvider extends AppWidgetProvider {
    private static final String TICK = "me.elizarov.weather.UPDATE_MINUTE";

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (WeatherRepository.REFRESH.equals(intent.getAction()))
            WeatherRepository.schedule(context, true);
        // Widget lifecycle, time/date/locale changes, and our explicit minute alarm.
        refresh(context);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        refresh(context);
    }

    @Override
    public void onDisabled(Context context) {
        ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).cancel(tickIntent(context));
        context.getSystemService(android.app.job.JobScheduler.class).cancel(WeatherRepository.JOB);
    }

    private static PendingIntent tickIntent(Context context) {
        return PendingIntent.getBroadcast(context, 0,
                new Intent(context, WeatherWidgetProvider.class).setAction(TICK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, WeatherWidgetProvider.class));
        if (ids.length == 0) return;
        long now = System.currentTimeMillis();
        manager.updateAppWidget(ids, WidgetRenderer.views(context, now, WidgetRenderer.lightText(context)));
        // RTC (not RTC_WAKEUP): do not wake a sleeping car solely to update a clock.
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarms.setExact(AlarmManager.RTC, (now / 60000 + 1) * 60000, tickIntent(context));
        WeatherRepository.schedule(context, false);
    }
}
