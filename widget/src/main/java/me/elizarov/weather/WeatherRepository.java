package me.elizarov.weather;

import me.elizarov.weather.hmc.catalog.HmcLocationCatalog;
import me.elizarov.weather.hmc.forecast.HmcForecastProvider;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.PersistableBundle;
import android.os.SystemClock;

public final class WeatherRepository {
    public static final String REFRESH = "me.elizarov.weather.REFRESH_WEATHER";
    public static final int JOB = 4819;
    public static final String CHANGED = "me.elizarov.weather.WEATHER_CHANGED";

    private WeatherRepository() {
    }

    static void ensureLocationCatalog(Context context) {
        HmcLocationCatalog.ensure(context, HmcForecastProvider.INSTANCE::cachedCityPath);
    }

    static void notifyChanged(Context context) {
        context.sendBroadcast(new Intent(CHANGED).setPackage(context.getPackageName()));
    }

    static void configurationChanged(Context context) {
        context.getSystemService(JobScheduler.class).cancel(JOB);
        WeatherConfig config = WeatherConfig.read(context);
        config.source.provider.cache(context).edit().remove("error").apply();
        schedule(context);
        WeatherWidgetProvider.refresh(context);
        notifyChanged(context);
    }

    static boolean schedule(Context context) {
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        WeatherConfig config = WeatherConfig.read(context);
        ForecastProvider<?> provider = config.source.provider;
        if (!provider.canFetch(config)) {
            jobs.cancel(JOB);
            return false;
        }

        long now = System.currentTimeMillis();
        SharedPreferences cache = provider.cache(context);
        boolean fresh = HourClock.same(provider.lastUpdate(cache, config), now);
        boolean checked = checkedThisHour(cache, config, now);
        boolean due = (!fresh && !checked) || locationChanged(cache, config, now, context);
        long delay = due ? 0 : HourClock.next(now) - now;
        JobInfo pending = jobs.getPendingJob(JOB);
        if (pending != null) {
            if (delay > 0 || pending.getMinLatencyMillis() == 0) return false;
            // A persisted future job can be replaced when the hour changes or the car moves.
            jobs.cancel(JOB);
        }
        PersistableBundle extras = new PersistableBundle();
        extras.putLong("request_id", SystemClock.elapsedRealtimeNanos());
        return jobs.schedule(new JobInfo.Builder(JOB, new ComponentName(context, WeatherUpdateService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(delay)
                .setExtras(extras).setPersisted(true).build()) == JobScheduler.RESULT_SUCCESS;
    }

    private static boolean checkedThisHour(SharedPreferences cache, WeatherConfig config, long now) {
        return cache.getLong("checked_hour", -1) == HourClock.start(now)
                && config.key.equals(cache.getString("checked_config", ""));
    }

    private static boolean locationChanged(SharedPreferences cache, WeatherConfig config, long now, Context context) {
        if (!config.automatic) return false;
        Location location = LocationPolicy.recentLocation(context);
        if (location == null || LocationPolicy.canReuseStation(cache, location)) return false;
        // A failed attempt must not become a request every minute while parked.
        boolean failed = cache.contains("error") || cache.getBoolean("location_failed", false);
        if (failed && checkedThisHour(cache, config, now) && cache.contains("checked_lat")) {
            float[] distance = new float[1];
            Location.distanceBetween(location.getLatitude(), location.getLongitude(),
                    Double.longBitsToDouble(cache.getLong("checked_lat", 0)),
                    Double.longBitsToDouble(cache.getLong("checked_lon", 0)), distance);
            if (distance[0] < RefreshPolicy.MOVED_METERS) return false;
        }
        return true;
    }

    static void checked(SharedPreferences cache, WeatherConfig config, long now, Location location) {
        SharedPreferences.Editor edit = cache.edit().putLong("checked_hour", HourClock.start(now))
                .putString("checked_config", config.key);
        if (location == null) edit.remove("checked_lat").remove("checked_lon");
        else edit.putLong("checked_lat", Double.doubleToLongBits(location.getLatitude()))
                .putLong("checked_lon", Double.doubleToLongBits(location.getLongitude()));
        edit.commit();
    }

    static long lastUpdate(Context context) {
        WeatherConfig config = WeatherConfig.read(context);
        ForecastProvider<?> provider = config.source.provider;
        return provider.lastUpdate(provider.cache(context), config);
    }
}
