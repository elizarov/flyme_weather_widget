package me.elizarov.weather;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.util.Log;

import java.util.function.BooleanSupplier;

/**
 * One stateless strategy per source. The refresh flow, cache writes and cancellation are shared.
 */
public abstract class ForecastProvider<T extends ForecastProvider.Target> {
    final String id;
    final String title;
    final String credit;
    final String keyRegistrationUrl;

    protected ForecastProvider(String id, String title, String credit, String keyRegistrationUrl) {
        this.id = id;
        this.title = title;
        this.credit = credit;
        this.keyRegistrationUrl = keyRegistrationUrl;
    }

    public static class Target {
        public final String id;
        final Location location;

        public Target(String id, Location location) {
            this.id = id;
            this.location = location;
        }
    }

    protected final SharedPreferences cache(Context context) {
        return context.getSharedPreferences("forecast_" + id, Context.MODE_PRIVATE);
    }

    final boolean requiresApiKey() {
        return !keyRegistrationUrl.isEmpty();
    }

    final String apiKeyPreference() {
        return id + "_api_key";
    }

    final boolean canFetch(WeatherConfig config) {
        return !requiresApiKey() || !config.apiKey.isEmpty();
    }

    protected final boolean matches(SharedPreferences cache, WeatherConfig config) {
        return config.key.equals(cache.getString("config_key", ""));
    }

    protected final String cachedForecast(SharedPreferences cache, WeatherConfig config) {
        return matches(cache, config) ? cache.getString("forecast", "") : "";
    }

    final long lastUpdate(SharedPreferences cache, WeatherConfig config) {
        return matches(cache, config) && cache.contains("forecast") ? cache.getLong("fetched_at", 0) : 0;
    }

    final void clearForecast(Context context) {
        cache(context).edit().remove("forecast").remove("config_key").apply();
    }

    final void update(Context context, WeatherConfig config, Location location, long now,
                      BooleanSupplier isCurrent) {
        if (!canFetch(config) || !isCurrent.getAsBoolean()) return;
        SharedPreferences cache = cache(context);
        cache.edit().putBoolean("loading", true).remove("location_failed").apply();
        try {
            T target = resolveTarget(context, config, location, cache);
            if (!isCurrent.getAsBoolean()) return;
            boolean fresh = RefreshPolicy.hasFreshForecast(cache, target.id, now);
            if (!fresh && !RefreshPolicy.recordAttempt(cache, target.id, System.currentTimeMillis()))
                return;

            SharedPreferences.Editor result = cache.edit().putString("config_key", config.key)
                    .putString("target", target.id)
                    .putString("mode", target.location == null ? "configured" : "location")
                    .remove("error");
            if (!fresh) {
                String forecast = download(config, target, cache, result, now);
                result.putString("forecast", forecast).putLong("fetched_at", System.currentTimeMillis());
            }
            if (!isCurrent.getAsBoolean()) return;
            writeTarget(result, target);
            if (result.commit() && target.location != null && !LocationPolicy.canReuseStation(cache, target.location))
                LocationPolicy.rememberStation(cache, target.location, target.id);
        } catch (Exception error) {
            if (isCurrent.getAsBoolean()) {
                cache.edit().putString("error", errorReason(error)).commit();
                Log.w("WeatherWidget", id + " forecast unavailable; preserving cached forecast");
            }
        } finally {
            cache.edit().putBoolean("loading", false).commit();
        }
    }

    protected void writeTarget(SharedPreferences.Editor result, T target) {
    }

    protected String errorReason(Exception error) {
        return error.getClass().getSimpleName();
    }

    protected abstract T resolveTarget(Context context, WeatherConfig config, Location location,
                                       SharedPreferences cache) throws Exception;

    /**
     * Stage provider-specific metadata in the editor. The shared update commits it with the payload.
     */
    protected abstract String download(WeatherConfig config, T target, SharedPreferences cache,
                                       SharedPreferences.Editor result, long now) throws Exception;

    protected abstract WeatherCards cards(Context context, WeatherConfig config, long now);

    protected abstract String website(Context context, WeatherConfig config);
}
