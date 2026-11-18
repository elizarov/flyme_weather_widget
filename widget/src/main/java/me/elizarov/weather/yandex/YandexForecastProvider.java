package me.elizarov.weather.yandex;

import me.elizarov.weather.ForecastProvider;
import me.elizarov.weather.HourClock;
import me.elizarov.weather.LocationPolicy;
import me.elizarov.weather.WeatherCards;
import me.elizarov.weather.WeatherConfig;
import me.elizarov.weather.hmc.HmcLocationCatalog;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public final class YandexForecastProvider extends ForecastProvider<YandexForecastProvider.YandexPoint> {
    public static final YandexForecastProvider INSTANCE = new YandexForecastProvider();

    private YandexForecastProvider() {
        super("yandex", "Яндекс Погода", "Данные сервиса Яндекс Погода",
                "https://yandex.ru/pogoda/b2b/console/smarthome");
    }

    static final class YandexPoint extends Target {
        final LocationPolicy.Coordinates coordinates;
        final String city;

        YandexPoint(LocationPolicy.Coordinates coordinates, String city, Location location) {
            super(String.format(Locale.ROOT, "%.5f,%.5f", coordinates.latitude, coordinates.longitude), location);
            this.coordinates = coordinates;
            this.city = city;
        }
    }

    @Override
    protected YandexPoint resolveTarget(Context context, WeatherConfig config, Location location,
                                        SharedPreferences cache) throws Exception {
        if (location == null) {
            String station;
            try {
                station = HmcLocationCatalog.fallbackStation(context, config);
            } catch (IOException | IllegalArgumentException error) {
                station = HmcLocationCatalog.cachedStation(context, config);
                if (station.isEmpty() || Thread.currentThread().isInterrupted()) throw error;
            }
            return new YandexPoint(YandexStationCoordinates.get(context, station), config.cityName, null);
        }
        LocationPolicy.Coordinates point;
        if (LocationPolicy.canReuseStation(cache, location)) {
            point = new LocationPolicy.Coordinates(Double.longBitsToDouble(cache.getLong("grid_lat", 0)),
                    Double.longBitsToDouble(cache.getLong("grid_lon", 0)));
        } else {
            point = LocationPolicy.rounded(location.getLatitude(), location.getLongitude());
        }
        return new YandexPoint(point, "Рядом с вами", location);
    }

    @Override
    protected String download(WeatherConfig config, YandexPoint point, SharedPreferences cache,
                              SharedPreferences.Editor result, long now) throws Exception {
        String json = YandexClient.forecast(config.apiKey, point.coordinates);
        result.putString("url", pointUrl(point.coordinates));
        return json;
    }

    @Override
    protected void writeTarget(SharedPreferences.Editor result, YandexPoint point) {
        result.putString("city", point.city);
    }

    @Override
    protected String errorReason(Exception error) {
        return error instanceof YandexClient.YandexFailure ? ((YandexClient.YandexFailure) error).reason : "network";
    }

    @Override
    protected WeatherCards cards(Context context, WeatherConfig config, long now) {
        SharedPreferences cache = cache(context);
        String json = cachedForecast(cache, config);
        String city = matches(cache, config) ? cache.getString("city", config.cityName) : config.cityName;
        return new WeatherCards(card(json, now, now), card(json, now + 3 * HourClock.HOUR, now), city);
    }

    private WeatherCards.Card card(String json, long time, long now) {
        String caption = hourCaption(time, now);
        try {
            YandexForecast forecast = YandexForecast.at(json, time);
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
    protected String website(Context context, WeatherConfig config) {
        SharedPreferences cache = cache(context);
        if (matches(cache, config)) {
            String saved = cache.getString("url", "");
            if (!saved.isEmpty()) return saved;
        }
        LocationPolicy.Coordinates point = YandexStationCoordinates.cached(context, HmcLocationCatalog.cachedStation(context, config));
        return point == null ? "" : pointUrl(point);
    }

    static String pointUrl(LocationPolicy.Coordinates point) {
        return String.format(Locale.ROOT, "https://yandex.ru/pogoda/?lat=%.5f&lon=%.5f", point.latitude, point.longitude);
    }
}
