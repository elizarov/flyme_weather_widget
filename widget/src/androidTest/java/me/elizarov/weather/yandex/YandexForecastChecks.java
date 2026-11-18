package me.elizarov.weather.yandex;

import android.content.Context;

import java.io.IOException;
import java.time.Instant;

import me.elizarov.weather.LocationPolicy;
import me.elizarov.weather.WeatherCondition;
import me.elizarov.weather.WeatherConfig;

public final class YandexForecastChecks {
    private YandexForecastChecks() {
    }

    public static void run(Context c) throws Exception {
        String json = hourlyFixture();
        long now = Instant.parse("2026-11-17T12:59:59Z").toEpochMilli();
        YandexForecast point = YandexForecast.at(json, now);
        require(point.temperature == 3 && point.icon(c, true) != 0 && point.icon(c, false) != 0, "current hour and official icons");
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
        checkStationCache(c, now);
        require(!YandexClient.query(LocationPolicy.rounded(55.833, 37.617)).contains("url"),
                "free-tier query omits restricted website field");
        require(YandexForecastProvider.pointUrl(new LocationPolicy.Coordinates(55.8, 37.6))
                        .equals("https://yandex.ru/pogoda/?lat=55.80000&lon=37.60000"),
                "public forecast page uses the same rounded point");
    }

    private static void checkStationCache(Context context, long now) throws Exception {
        String saintPetersburg = "0-20000-0-26063";
        String result = "{\"stationSearchResults\":[{\"latitude\":59.9666666667,\"longitude\":30.3,"
                + "\"wigosStationIdentifiers\":[{\"wigosStationIdentifier\":\"0-20000-0-26063\",\"primary\":true}]}]}";
        LocationPolicy.Coordinates original = YandexStationCoordinates.parse(result, saintPetersburg);
        require(original.latitude == 59.9666666667 && original.longitude == 30.3, "WMO point from JSON");
        try {
            YandexStationCoordinates.parse(result, "0-20000-0-26067");
            throw new AssertionError("Mismatched WIGOS ID accepted");
        } catch (IOException expected) {
        }
        String name = "test-station-coordinates.db";
        context.deleteDatabase(name);
        long interval = YandexStationCoordinates.RECHECK_INTERVAL;
        LocationPolicy.Coordinates second = new LocationPolicy.Coordinates(59.4333, 29.5);
        LocationPolicy.Coordinates replacement = new LocationPolicy.Coordinates(59.96, 30.29);
        int[] calls = {0};
        YandexStationCoordinates.StationLookup offline = id -> {
            calls[0]++;
            throw new IOException("Offline");
        };
        try {
            try (YandexStationCoordinates db = new YandexStationCoordinates(context, name, id -> {
                calls[0]++;
                if (saintPetersburg.equals(id)) return original;
                require(id.equals("0-20000-0-26067"), "Only station IDs are sent to WMO");
                return second;
            })) {
                require(db.resolve(WeatherConfig.DEFAULT_STATION, now).latitude == 55.833 && calls[0] == 0,
                        "Default Moscow point is available without WMO");
                require(db.resolve("26063", now).latitude == original.latitude, "First station is cached");
                require(db.resolve("26067", now).latitude == second.latitude, "Second station is cached separately");
                require(calls[0] == 2, "Only stations absent from the cache were looked up");
            }
            try (YandexStationCoordinates db = new YandexStationCoordinates(context, name, offline)) {
                require(db.resolve("26063", now + interval - 1).latitude == original.latitude && calls[0] == 2,
                        "Fresh coordinates survive reopening the database");
                require(db.resolve("26063", now + interval).latitude == original.latitude && calls[0] == 3,
                        "After 30 days, one failed recheck preserves the old coordinates");
                require(db.resolve("26063", now + interval + 1).latitude == original.latitude && calls[0] == 3,
                        "A failed recheck is also remembered");
                try {
                    db.resolve("31960", now + interval);
                    throw new AssertionError("Unknown station returned coordinates while offline");
                } catch (IOException expected) {
                }
            }
            try (YandexStationCoordinates db = new YandexStationCoordinates(context, name, id -> replacement)) {
                require(db.resolve("26063", now + 2 * interval).latitude == replacement.latitude,
                        "The next successful recheck replaces the coordinates");
                require(db.resolve("26067", now + 1).latitude == second.latitude,
                        "Refreshing one station keeps the other station's coordinates");
            }
        } finally {
            context.deleteDatabase(name);
        }
    }

    public static void checkCaptions() {
        require(YandexForecastProvider.hourCaption(time("2026-11-18T00:00:00Z"), time("2026-11-17T21:00:00Z")).equals("3:00"), "same local date");
        require(YandexForecastProvider.hourCaption(time("2026-11-17T23:00:00Z"), time("2026-11-17T20:00:00Z")).equals("2:00 +1"), "future card tomorrow");
    }

    public static void checkLiveForecast(Context c, String json) throws Exception {
        YandexForecast current = YandexForecast.at(json, System.currentTimeMillis());
        require(current != null && current.icon(c, true) != 0, "Live current-hour forecast and official icon");
    }

    public static String hourlyFixture() {
        return "{\"data\":{\"serverTime\":\"2026-11-17T12:20:00Z\",\"weatherByPoint\":{\"now\":{\"temperature\":2,\"feelsLike\":-2,\"condition\":\"OVERCAST\",\"icon\":\"ovc\"},\"forecast\":{\"days\":[{\"hours\":[{\"time\":\"2026-11-17T12:00:00Z\",\"temperature\":3,\"feelsLike\":-1,\"condition\":\"OVERCAST\",\"icon\":\"ovc\"},{\"time\":\"2026-11-17T13:00:00Z\",\"temperature\":1,\"feelsLike\":-3,\"condition\":\"LIGHT_SNOW\",\"icon\":\"ovc_-sn\"}]}]}}}}";
    }

    public static String overnightFixture() {
        return "{\"data\":{\"serverTime\":\"2026-11-17T20:15:00Z\",\"weatherByPoint\":{\"url\":\"https://yandex.ru/pogoda/moscow\",\"forecast\":{\"days\":[{\"hours\":[{\"time\":\"2026-11-17T20:00:00Z\",\"temperature\":-30,\"condition\":\"OVERCAST\",\"icon\":\"ovc\"},{\"time\":\"2026-11-17T23:00:00Z\",\"temperature\":2,\"condition\":\"CLEAR\",\"icon\":\"skc_n\"}]}]}}}}";
    }

    private static long time(String instant) {
        return Instant.parse(instant).toEpochMilli();
    }

    private static void require(boolean yes, String why) {
        if (!yes) throw new AssertionError(why);
    }
}
