package me.elizarov.weather;

import me.elizarov.weather.hmc.HmcForecastProvider;
import me.elizarov.weather.openmeteo.OpenMeteoForecastProvider;
import me.elizarov.weather.yandex.YandexForecastProvider;

import java.util.HashMap;
import java.util.Map;

enum WeatherSource {
    HMC(HmcForecastProvider.INSTANCE, "день/ночь"),
    OPEN_METEO(OpenMeteoForecastProvider.INSTANCE, "по часам"),
    YANDEX(YandexForecastProvider.INSTANCE, "по часам");

    private static final Map<String, WeatherSource> BY_ID = new HashMap<>();

    static {
        for (WeatherSource source : values()) BY_ID.put(source.provider.id, source);
    }

    final ForecastProvider<?> provider;

    final String periodCaption;

    WeatherSource(ForecastProvider<?> provider, String periodCaption) {
        this.periodCaption = periodCaption;
        this.provider = provider;
    }

    static WeatherSource fromId(String id) {
        return BY_ID.getOrDefault(id, HMC);
    }
}
