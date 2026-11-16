package me.elizarov.weather;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;

public final class WeatherRepository {
    public static final String REFRESH = "me.elizarov.weather.REFRESH_WEATHER";
    public static final int JOB = 4819;
    public static final String CHANGED = "me.elizarov.weather.WEATHER_CHANGED";

    private WeatherRepository() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("weather", Context.MODE_PRIVATE);
    }

    static void notifyChanged(Context c) {
        c.sendBroadcast(new android.content.Intent(CHANGED).setPackage(c.getPackageName()));
    }

    static void configurationChanged(Context c) {
        c.getSystemService(JobScheduler.class).cancel(JOB);
        WeatherConditions.remember(prefs(c));
        // A previous city's forecast must never be shown under a new city name.
        prefs(c).edit().remove("xml").remove("conditions").remove("conditions_error").remove("station").remove("error").remove("config_key")
                .putBoolean("loading", true).putLong("next_fetch", 0).putLong("attempted_at", 0).commit();
        schedule(c, true);
        WeatherWidgetProvider.refresh(c);
        notifyChanged(c);
    }

    static boolean schedule(Context c, boolean force) {
        JobScheduler jobs = c.getSystemService(JobScheduler.class);
        long now = System.currentTimeMillis();
        SharedPreferences p = prefs(c);
        if (force && now - p.getLong("attempted_at", 0) < RefreshPolicy.MANUAL_GUARD) return false;
        if (!force && jobs.getPendingJob(JOB) != null) return false;
        android.os.PersistableBundle extras = new android.os.PersistableBundle();
        extras.putBoolean("force", force);
        extras.putLong("request_id", android.os.SystemClock.elapsedRealtimeNanos());
        return jobs.schedule(new JobInfo.Builder(JOB, new ComponentName(c, WeatherUpdateService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(force ? 0 : Math.max(0, p.getLong("next_fetch", 0) - now))
                .setExtras(extras).setPersisted(true).build()) == JobScheduler.RESULT_SUCCESS;
    }

    static WeatherForecast forTime(Context c, LocalDate date, int hour) {
        try {
            return WeatherForecast.forTime(forecasts(c), date, hour);
        } catch (Exception error) {
            return null;
        }
    }

    static java.util.List<WeatherForecast> forecasts(Context c) {
        SharedPreferences p = prefs(c);
        if (p.contains("config_key") && !p.getString("config_key", "").equals(WeatherConfig.read(c).key))
            return java.util.Collections.emptyList();
        try {
            return WeatherForecast.parse(p.getString("xml", ""));
        } catch (Exception error) {
            return java.util.Collections.emptyList();
        }
    }
}
