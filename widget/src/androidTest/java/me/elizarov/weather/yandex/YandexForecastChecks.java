package me.elizarov.weather.yandex;

import android.content.Context;

import java.time.Instant;

import me.elizarov.weather.LocationPolicy;
import me.elizarov.weather.WeatherCondition;
import me.elizarov.weather.HourlyForecastProvider.Reading;
import me.elizarov.weather.WeatherIcons;

public final class YandexForecastChecks {
    private YandexForecastChecks() {
    }

    public static void run(Context c) throws Exception {
        String json = hourlyFixture();
        long now = Instant.parse("2026-11-17T12:59:59Z").toEpochMilli();
        Reading point = YandexForecast.at(json, now);
        require(point.temperature == 3 && WeatherIcons.resource(c, point.icon, true) != 0 && WeatherIcons.resource(c, point.icon, false) != 0, "current hour and official icons");
        require(YandexForecast.at(json, now + 1000).temperature == 1, "hour boundary selects the next forecast without HTTP");
        require(YandexForecast.at(json, now + 1000).condition == WeatherCondition.LIGHT_SNOW,
                "Yandex parser normalizes the condition code");
        require(WeatherCondition.fromYandex("THUNDERSTORM_WITH_HAIL") == WeatherCondition.THUNDERSTORM,
                "Yandex thunderstorm alias");
        require(WeatherCondition.fromYandex("NEW_CONDITION") == WeatherCondition.UNKNOWN,
                "unknown Yandex condition has a safe fallback");
        require(YandexForecast.at(json, now + 7200000) == null, "expired data is not shown as current");
        require(!YandexClient.query(LocationPolicy.rounded(55.833, 37.617)).contains("55.833"),
                "API query uses rounded coordinates");
        require(!YandexClient.query(LocationPolicy.rounded(55.833, 37.617)).contains("url"),
                "free-tier query omits restricted website field");
        require(YandexForecastProvider.INSTANCE.pointUrl(new LocationPolicy.Coordinates(55.8, 37.6))
                        .equals("https://yandex.ru/pogoda/?lat=55.80000&lon=37.60000"),
                "public forecast page uses the same rounded point");
    }

    public static void checkLiveForecast(Context c, String json) throws Exception {
        Reading current = YandexForecast.at(json, System.currentTimeMillis());
        require(current != null && WeatherIcons.resource(c, current.icon, true) != 0, "Live current-hour forecast and official icon");
    }

    public static String hourlyFixture() {
        return "{\"data\":{\"serverTime\":\"2026-11-17T12:20:00Z\",\"weatherByPoint\":{\"now\":{\"temperature\":2,\"feelsLike\":-2,\"condition\":\"OVERCAST\",\"icon\":\"ovc\"},\"forecast\":{\"days\":[{\"hours\":[{\"time\":\"2026-11-17T12:00:00Z\",\"temperature\":3,\"feelsLike\":-1,\"condition\":\"OVERCAST\",\"icon\":\"ovc\"},{\"time\":\"2026-11-17T13:00:00Z\",\"temperature\":1,\"feelsLike\":-3,\"condition\":\"LIGHT_SNOW\",\"icon\":\"ovc_-sn\"}]}]}}}}";
    }

    public static String overnightFixture() {
        return "{\"data\":{\"serverTime\":\"2026-11-17T20:15:00Z\",\"weatherByPoint\":{\"url\":\"https://yandex.ru/pogoda/moscow\",\"forecast\":{\"days\":[{\"hours\":[{\"time\":\"2026-11-17T20:00:00Z\",\"temperature\":-30,\"condition\":\"OVERCAST\",\"icon\":\"ovc\"},{\"time\":\"2026-11-17T23:00:00Z\",\"temperature\":2,\"condition\":\"CLEAR\",\"icon\":\"skc_n\"}]}]}}}}";
    }

    private static void require(boolean yes, String why) {
        if (!yes) throw new AssertionError(why);
    }
}
