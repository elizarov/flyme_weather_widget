package me.elizarov.weather;

import android.Manifest;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;

public final class WeatherUpdateService extends JobService {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final class RunningJob {
        volatile boolean cancelled;
        Future<?> future;

        void cancel() {
            cancelled = true;
            if (future != null) future.cancel(true);
        }
    }

    private final java.util.concurrent.ConcurrentMap<Long, RunningJob> running = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public boolean onStartJob(JobParameters params) {
        long request = params.getExtras().getLong("request_id");
        RunningJob run = new RunningJob();
        running.put(request, run);
        run.future = executor.submit(() -> {
            try {
                long now = System.currentTimeMillis();
                WeatherConfig config = WeatherConfig.read(this);
                WeatherRepository.prefs(this).edit().putLong("attempted_at", now).putBoolean("loading", true).apply();
                try {
                    String station = "";
                    boolean located = false;
                    if (config.automatic) {
                        Location location = recentLocation();
                        if (location != null) {
                            try {
                                station = stationForRecentLocation(location);
                                located = true;
                            } catch (Exception error) {
                                Log.i("WeatherWidget", "Station lookup unavailable; using configured fallback");
                            }
                        }
                    }
                    if (station.isEmpty()) station = fallbackStation(config);
                    android.content.SharedPreferences p = WeatherRepository.prefs(this);
                    if (!RefreshPolicy.needsForecast(now, p.getLong("fetched_at", 0), params.getExtras().getBoolean("force")
                                    || p.contains("error") || p.contains("conditions_error")
                                    || !WeatherConditions.complete(p.getString("conditions", "{}"), now),
                            station.equals(p.getString("station", "")), config.key.equals(p.getString("config_key", "")), p.contains("xml"))) {
                        if (run.cancelled || !config.key.equals(WeatherConfig.read(this).key))
                            return;
                        p.edit().putLong("next_fetch", RefreshPolicy.nextCheck(now, config.automatic))
                                .putString("mode", located ? "location" : "configured")
                                .putBoolean("loading", false).remove("error").commit();
                        return;
                    }
                    String xml;
                    try {
                        xml = forecast(station);
                    } catch (Exception error) {
                        if (!located) throw error;
                        station = fallbackStation(config);
                        located = false;
                        xml = forecast(station);
                    }
                    java.util.List<WeatherForecast> forecasts = WeatherForecast.parse(xml);
                    String conditions = WeatherConditions.merge(WeatherConditions.cached(p, station), "{}", forecasts);
                    String conditionsError = "";
                    try {
                        String page = HmcClient.request("https://meteoinfo.ru/forecasts/" + WeatherConditions.pathFromRss(xml), null);
                        conditions = WeatherConditions.merge(conditions, WeatherConditions.parse(page, forecasts).toString(), forecasts);
                        if (!WeatherConditions.complete(conditions, now))
                            conditionsError = "Missing day/night conditions";
                    } catch (Exception error) {
                        conditionsError = error.getClass().getSimpleName() + ": " + error.getMessage();
                        Log.w("WeatherWidget", "Detailed conditions unavailable; keeping station cache, retry in 10 minutes", error);
                    }
                    if (Thread.currentThread().isInterrupted() || run.cancelled || !config.key.equals(WeatherConfig.read(this).key))
                        return;
                    android.content.SharedPreferences.Editor result = p.edit().putString("config_key", config.key).putString("xml", xml).putString("station", station)
                            .putString("conditions", conditions)
                            .putString("conditions_cache_" + station, conditions)
                            .putString("mode", located ? "location" : "configured")
                            .putLong("fetched_at", now).putLong("next_fetch", RefreshPolicy.nextCheck(now, config.automatic, !conditionsError.isEmpty()))
                            .remove("error");
                    if (conditionsError.isEmpty()) result.remove("conditions_error");
                    else result.putString("conditions_error", conditionsError);
                    result.commit();
                    Log.i("WeatherWidget", "HMC forecast updated: station=" + station);
                } catch (Exception error) {
                    if (Thread.currentThread().isInterrupted() || run.cancelled || !config.key.equals(WeatherConfig.read(this).key))
                        return;
                    WeatherRepository.prefs(this).edit().putString("error", error.getClass().getSimpleName())
                            .putLong("next_fetch", now + RefreshPolicy.RETRY).commit();
                    Log.w("WeatherWidget", "Forecast unavailable; preserving last valid cache", error);
                }
                WeatherRepository.prefs(this).edit().putBoolean("loading", false).commit();
            } finally {
                running.remove(request, run);
                jobFinished(params, false);
                // Only an enabled AppWidget keeps periodic weather work scheduled.
                WeatherWidgetProvider.refresh(this);
                WeatherRepository.notifyChanged(this);
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        RunningJob work = running.remove(params.getExtras().getLong("request_id"));
        if (work != null) work.cancel();
        return true;
    }

    @Override
    public void onDestroy() {
        for (RunningJob work : running.values()) work.cancel();
        running.clear();
        executor.shutdownNow();
        super.onDestroy();
    }

    private String fallbackStation(WeatherConfig config) throws Exception {
        if (config.cityPath.isEmpty()) return config.fallbackStation;
        android.content.SharedPreferences p = WeatherRepository.prefs(this);
        if (config.cityPath.equals(p.getString("resolved_path", "")) && System.currentTimeMillis() - p.getLong("resolved_at", 0) < 86400000)
            return p.getString("resolved_station", config.fallbackStation);
        String station = HmcClient.stationForPath(config.cityPath);
        p.edit().putString("resolved_path", config.cityPath).putString("resolved_station", station).putLong("resolved_at", System.currentTimeMillis()).commit();
        return station;
    }

    private Location recentLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return null;
        LocationManager manager = getSystemService(LocationManager.class);
        Location best = null;
        for (String provider : manager.getProviders(true)) {
            try {
                Location l = manager.getLastKnownLocation(provider);
                if (l != null && System.currentTimeMillis() - l.getTime() < 7200000 && !l.isFromMockProvider()
                        && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (
                    SecurityException ignored) { /* A provider can require a different permission. */ }
        }
        return best;
    }

    private String stationForLocation(Location location) throws Exception {
        String body = LocationPolicy.stationRequest(location);
        JSONArray response = new JSONArray(HmcClient.request("https://meteoinfo.ru/hmc-output/forecast/tab_0.php", body));
        Matcher id = Pattern.compile("rss/forecasts/index\\.php\\?s=([0-9]{5})").matcher(response.getJSONArray(8).getString(0));
        if (!id.find())
            throw new IllegalArgumentException("No forecast station in location response");
        return id.group(1);
    }

    private String stationForRecentLocation(Location location) throws Exception {
        android.content.SharedPreferences p = WeatherRepository.prefs(this);
        if (LocationPolicy.canReuseStation(p, location)) return p.getString("located_station", "");
        String station = stationForLocation(location);
        LocationPolicy.rememberStation(p, location, station);
        return station;
    }

    private String forecast(String station) throws Exception {
        String xml = HmcClient.request("https://meteoinfo.ru/rss/forecasts/index.php?s=" + station, null);
        WeatherForecast.parse(xml); // Validate before replacing the last good cache.
        return xml;
    }

}
