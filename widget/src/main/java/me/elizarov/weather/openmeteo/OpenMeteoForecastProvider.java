package me.elizarov.weather.openmeteo;

import me.elizarov.weather.HourlyForecastProvider;
import me.elizarov.weather.LocationPolicy;
import me.elizarov.weather.WeatherConfig;

public final class OpenMeteoForecastProvider extends HourlyForecastProvider {
    public static final OpenMeteoForecastProvider INSTANCE = new OpenMeteoForecastProvider();

    private OpenMeteoForecastProvider() {
        super("openmeteo", "Open-Meteo", "Данные Open-Meteo", "");
    }

    @Override
    protected String fetch(WeatherConfig config, LocationPolicy.Coordinates point) throws Exception {
        return OpenMeteoClient.forecast(point);
    }

    @Override
    protected Reading readingAt(String json, long time) throws Exception {
        return OpenMeteoForecast.at(json, time);
    }

    @Override
    protected String pointUrl(LocationPolicy.Coordinates point) {
        return "https://open-meteo.com/en/docs?" + OpenMeteoClient.parameters(point) + "&timezone=auto#api_response";
    }
}
