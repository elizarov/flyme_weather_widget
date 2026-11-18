package me.elizarov.weather.yandex;

import me.elizarov.weather.HourlyForecastProvider;
import me.elizarov.weather.LocationPolicy;
import me.elizarov.weather.WeatherConfig;

import java.util.Locale;

public final class YandexForecastProvider extends HourlyForecastProvider {
    public static final YandexForecastProvider INSTANCE = new YandexForecastProvider();

    private YandexForecastProvider() {
        super("yandex", "Яндекс Погода", "Данные сервиса Яндекс Погода",
                "https://yandex.ru/pogoda/b2b/console/smarthome");
    }

    @Override
    protected String fetch(WeatherConfig config, LocationPolicy.Coordinates point) throws Exception {
        return YandexClient.forecast(config.apiKey, point);
    }

    @Override
    protected Reading readingAt(String json, long time) throws Exception {
        return YandexForecast.at(json, time);
    }

    @Override
    protected String errorReason(Exception error) {
        return error instanceof YandexClient.YandexFailure ? ((YandexClient.YandexFailure) error).reason : "network";
    }

    @Override
    protected String pointUrl(LocationPolicy.Coordinates point) {
        return String.format(Locale.ROOT, "https://yandex.ru/pogoda/?lat=%.5f&lon=%.5f", point.latitude, point.longitude);
    }
}
