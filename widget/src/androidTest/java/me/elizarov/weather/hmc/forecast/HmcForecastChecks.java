package me.elizarov.weather.hmc.forecast;

import android.location.Location;

import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import me.elizarov.weather.LocationPolicy;
import me.elizarov.weather.WeatherCondition;

public final class HmcForecastChecks {
    private HmcForecastChecks() {
    }

    public static String parseAndCheck(String rss, String details) throws Exception {
        List<HmcForecast> forecasts = HmcForecast.parse(rss);
        JSONObject conditions = HmcConditions.parse(details, forecasts);
        HmcForecast first = HmcForecast.forDate(forecasts, LocalDate.of(2026, 11, 16));
        require(first != null && first.day == 4 && first.night == 3, "RSS temperatures");
        require(HmcForecast.parse(rss.replace("ночью 3°", "ночью -13°")).get(0).night == -13, "negative temperature");
        require(HmcForecast.parse(rss.replace("16 ноября", "1 января").replace("16.11.2026", "31.12.2026")).get(0).date.equals(LocalDate.of(2027, 1, 1)), "RSS year rollover");
        boolean rejected = false;
        try {
            HmcForecast.parse("<rss><channel/></rss>");
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "empty RSS rejected");
        String merged = HmcConditions.merge(conditions.toString(), "{}", forecasts);
        require(new JSONObject(merged).has("2026-11-17n"), "failed detail download retains night icon");
        require(HmcForecastProvider.forecastUrl(rss, "").equals("https://meteoinfo.ru/forecasts/" + HmcConditions.pathFromRss(rss)), "HMC local webpage");
        return conditions.toString();
    }

    public static void checkConditions() {
        require(WeatherCondition.fromHmc("Дождь, местами снег") == WeatherCondition.SLEET
                        && WeatherCondition.fromHmc("Снег с дождём") == WeatherCondition.SLEET,
                "mixed weather takes priority over individual precipitation in either order");
        require(WeatherCondition.fromHmc("Снег с дождём, местами грозы") == WeatherCondition.THUNDERSTORM,
                "thunderstorm takes priority over precipitation");
        require(WeatherCondition.fromHmc("Малооблачно").icon(true).equals("bkn_n"),
                "compound cloud description precedes general cloudiness, with a night icon");
        require(WeatherCondition.fromHmc("").icon(false).isEmpty(), "unknown HMC condition has a safe fallback");
    }

    public static void checkLocationForm() {
        LocationPolicy.Coordinates centre = LocationPolicy.rounded(59.934321, 30.335123);
        Location first = location(centre.latitude + 0.001, centre.longitude + 0.001);
        Location second = location(centre.latitude - 0.001, centre.longitude - 0.001);
        String form = HmcForecastProvider.stationRequest(first);
        require(form.equals(HmcForecastProvider.stationRequest(second)), "nearby precise points produce identical request coordinates");
        require(!form.contains(Double.toString(first.getLatitude()))
                && !form.contains(Double.toString(first.getLongitude())), "request never contains the original precise values");
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            require(form.equals(HmcForecastProvider.stationRequest(first)), "form coordinates always use a decimal point");
        } finally {
            Locale.setDefault(original);
        }
    }

    public static void checkPeriods() {
        periods("2026-11-17T04:59:59Z", "ночь 16→17", "день");
        periods("2026-11-17T05:00:00Z", "день", "ночь 17→18");
        periods("2026-11-17T16:59:59Z", "день", "ночь 17→18");
        periods("2026-11-17T17:00:00Z", "ночь 17→18", "день 18");
        periods("2026-11-17T21:00:00Z", "ночь 17→18", "день");
        periods("2026-12-31T18:00:00Z", "ночь 31→1", "день 1");
    }

    private static void periods(String instant, String first, String second) {
        HmcForecastWindow window = HmcForecastWindow.at(Instant.parse(instant).toEpochMilli());
        require(window.first.caption.equals(first) && window.second.caption.equals(second), "period order at " + instant);
    }

    private static Location location(double latitude, double longitude) {
        Location location = new Location("test");
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        return location;
    }

    private static void require(boolean yes, String why) {
        if (!yes) throw new AssertionError(why);
    }
}
