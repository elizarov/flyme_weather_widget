package me.elizarov.weather.hmc.forecast;

import me.elizarov.weather.ForecastProvider;
import me.elizarov.weather.hmc.HmcClient;
import me.elizarov.weather.hmc.catalog.HmcLocationCatalog;
import me.elizarov.weather.LocationPolicy;
import me.elizarov.weather.WeatherCards;
import me.elizarov.weather.WeatherCondition;
import me.elizarov.weather.WeatherConfig;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.util.Log;

import org.json.JSONArray;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HmcForecastProvider extends ForecastProvider<ForecastProvider.Target> {
    private static final String FORECASTS = "https://meteoinfo.ru/forecasts/";
    private static final Pattern STATION = Pattern.compile("rss/forecasts/index\\.php\\?s=([0-9]{5})");

    public static final HmcForecastProvider INSTANCE = new HmcForecastProvider();

    private HmcForecastProvider() {
        super("hmc", "Гидрометцентр", "Данные Гидрометцентра", "");
    }

    static String stationRequest(Location location) {
        LocationPolicy.Coordinates point = LocationPolicy.rounded(location.getLatitude(), location.getLongitude());
        return "lang=ru-RU&id_city=russia%2Fmoscow-area%2Fmoscow&need_loc=1&has_db=1&lstan=0"
                + String.format(java.util.Locale.ROOT, "&lat_loc=%.5f&lon_loc=%.5f", point.latitude, point.longitude);
    }

    @Override
    protected Target resolveTarget(Context context, WeatherConfig config, Location location,
                                   SharedPreferences cache) throws Exception {
        if (location != null) {
            try {
                if (LocationPolicy.canReuseStation(cache, location))
                    return new Target(cache.getString("located_station", ""), location);
                String body = stationRequest(location);
                JSONArray response = new JSONArray(HmcClient.nearbyStation(body));
                Matcher match = STATION.matcher(response.getJSONArray(8).getString(0));
                if (!match.find())
                    throw new IllegalArgumentException("No forecast station in location response");
                String station = match.group(1);
                // Keep a successful station lookup even if the subsequent forecast fails.
                LocationPolicy.rememberStation(cache, location, station);
                return new Target(station, location);
            } catch (Exception error) {
                if (Thread.currentThread().isInterrupted()) throw error;
                cache.edit().putBoolean("location_failed", true).commit();
                Log.i("WeatherWidget", "Station lookup unavailable; using configured fallback");
            }
        }
        return new Target(HmcLocationCatalog.fallbackStation(context, config), null);
    }

    @Override
    protected String download(WeatherConfig config, Target target, SharedPreferences cache,
                              SharedPreferences.Editor result, long now) throws Exception {
        String xml = HmcClient.forecast(target.id);
        List<HmcForecast> forecasts = HmcForecast.parse(xml);
        String conditions = HmcConditions.merge(HmcConditions.cached(cache, target.id), "{}", forecasts);
        String conditionsError = "";
        try {
            String page = HmcClient.cityPage(HmcConditions.pathFromRss(xml));
            conditions = HmcConditions.merge(conditions, HmcConditions.parse(page, forecasts).toString(), forecasts);
            if (!HmcConditions.complete(conditions, now))
                conditionsError = "Missing day/night conditions";
        } catch (Exception error) {
            if (Thread.currentThread().isInterrupted()) throw error;
            conditionsError = error.getClass().getSimpleName() + ": " + error.getMessage();
            Log.w("WeatherWidget", "Detailed conditions unavailable; keeping station cache until next hourly update");
        }
        result.putString("conditions", conditions).putString("conditions_cache_" + target.id, conditions);
        if (conditionsError.isEmpty()) result.remove("conditions_error");
        else result.putString("conditions_error", conditionsError);
        return xml;
    }

    static List<HmcForecast> parseCached(String xml) {
        try {
            return HmcForecast.parse(xml);
        } catch (Exception error) {
            return Collections.emptyList();
        }
    }

    @Override
    protected WeatherCards cards(Context context, WeatherConfig config, long now) {
        SharedPreferences cache = cache(context);
        List<HmcForecast> forecasts = parseCached(cachedForecast(cache, config));
        HmcForecastWindow window = HmcForecastWindow.at(now);
        HmcForecast first = HmcForecast.forDate(forecasts, window.first.date);
        HmcForecast second = HmcForecast.forDate(forecasts, window.second.date);
        return new WeatherCards(card(cache, window.first, first), card(cache, window.second, second),
                first != null ? first.city : second != null ? second.city : config.cityName);
    }

    private WeatherCards.Card card(SharedPreferences cache, HmcForecastWindow.HmcPeriod period, HmcForecast forecast) {
        if (forecast == null) return new WeatherCards.Card(null, period.caption, "", "");
        String condition = HmcConditions.get(cache, period.date, period.night);
        return new WeatherCards.Card(period.night ? forecast.night : forecast.day, period.caption,
                WeatherCondition.fromHmc(condition).icon(period.night), condition);
    }

    @Override
    protected String website(Context context, WeatherConfig config) {
        return forecastUrl(cachedForecast(cache(context), config), config.cityPath);
    }

    public String cachedCityPath(Context context) {
        try {
            return HmcConditions.pathFromRss(cache(context).getString("forecast", ""));
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    static String forecastUrl(String xml, String cityPath) {
        try {
            return FORECASTS + HmcConditions.pathFromRss(xml);
        } catch (IllegalArgumentException ignored) {
            return FORECASTS + (cityPath.isEmpty() ? "russia/moscow-area/moscow" : cityPath);
        }
    }
}
