package me.elizarov.weather;

import me.elizarov.weather.hmc.catalog.HmcLocationCatalog;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Coordinate-based providers share location selection, station lookup and the two hourly cards. */
public abstract class HourlyForecastProvider extends ForecastProvider<HourlyForecastProvider.Point> {
    protected HourlyForecastProvider(String id, String title, String credit, String keyRegistrationUrl) {
        super(id, title, credit, keyRegistrationUrl);
    }

    static final class Point extends Target {
        final LocationPolicy.Coordinates coordinates;
        final String city;

        Point(LocationPolicy.Coordinates coordinates, String city, Location location) {
            super(String.format(Locale.ROOT, "%.5f,%.5f", coordinates.latitude, coordinates.longitude), location);
            this.coordinates = coordinates;
            this.city = city;
        }
    }

    @Override
    protected final Point resolveTarget(Context context, WeatherConfig config, Location location,
                                        SharedPreferences cache) throws Exception {
        if (location == null) {
            String station;
            try {
                station = HmcLocationCatalog.fallbackStation(context, config);
            } catch (IOException | IllegalArgumentException error) {
                station = HmcLocationCatalog.cachedStation(context, config);
                if (station.isEmpty() || Thread.currentThread().isInterrupted()) throw error;
            }
            return new Point(StationCoordinates.get(context, station), config.cityName, null);
        }
        LocationPolicy.Coordinates point;
        if (LocationPolicy.canReuseStation(cache, location)) {
            point = new LocationPolicy.Coordinates(Double.longBitsToDouble(cache.getLong("grid_lat", 0)),
                    Double.longBitsToDouble(cache.getLong("grid_lon", 0)));
        } else {
            point = LocationPolicy.rounded(location.getLatitude(), location.getLongitude());
        }
        return new Point(point, "Рядом с вами", location);
    }

    @Override
    protected final String download(WeatherConfig config, Point point, SharedPreferences cache,
                                    SharedPreferences.Editor result, long now) throws Exception {
        String json = fetch(config, point.coordinates);
        if (readingAt(json, now) == null) throw new IOException("Missing forecast for current hour");
        result.putString("url", pointUrl(point.coordinates));
        return json;
    }

    protected abstract String fetch(WeatherConfig config, LocationPolicy.Coordinates point) throws Exception;
    protected abstract Reading readingAt(String json, long time) throws Exception;
    protected abstract String pointUrl(LocationPolicy.Coordinates point);

    @Override
    protected final void writeTarget(SharedPreferences.Editor result, Point point) {
        result.putString("city", point.city);
    }

    @Override
    protected final WeatherCards cards(Context context, WeatherConfig config, long now) {
        SharedPreferences cache = cache(context);
        String json = cachedForecast(cache, config);
        String city = matches(cache, config) ? cache.getString("city", config.cityName) : config.cityName;
        return new WeatherCards(card(json, now, now), card(json, now + config.forecastHours * HourClock.HOUR, now), city);
    }

    public static final class Reading {
        public final int temperature;
        public final WeatherCondition condition;
        public final String icon;

        public Reading(int temperature, WeatherCondition condition, String icon) {
            this.temperature = temperature;
            this.condition = condition;
            this.icon = icon;
        }
    }

    private WeatherCards.Card card(String json, long time, long now) {
        String caption = hourCaption(time, now);
        try {
            Reading forecast = json.isEmpty() ? null : readingAt(json, time);
            if (forecast != null)
                return new WeatherCards.Card(forecast.temperature, caption, forecast.icon, forecast.condition.description);
        } catch (Exception ignored) {
        }
        return new WeatherCards.Card(null, caption, "", "Прогноз недоступен");
    }

    static String hourCaption(long time, long now) {
        ZonedDateTime value = HourClock.at(time);
        long days = ChronoUnit.DAYS.between(HourClock.at(now).toLocalDate(), value.toLocalDate());
        return String.format(Locale.ROOT, "%d:00", value.getHour()) + (days > 0 ? " +" + days : "");
    }

    @Override
    protected final String website(Context context, WeatherConfig config) {
        SharedPreferences cache = cache(context);
        if (matches(cache, config)) {
            String saved = cache.getString("url", "");
            if (!saved.isEmpty()) return saved;
        }
        LocationPolicy.Coordinates point = StationCoordinates.cached(context, HmcLocationCatalog.cachedStation(context, config));
        return point == null ? "" : pointUrl(point);
    }

}
