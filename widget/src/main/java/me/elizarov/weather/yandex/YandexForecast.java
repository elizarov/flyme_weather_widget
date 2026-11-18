package me.elizarov.weather.yandex;

import me.elizarov.weather.HourClock;
import me.elizarov.weather.WeatherCondition;
import me.elizarov.weather.HourlyForecastProvider.Reading;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Yandex forecast values selected by absolute hour, including DST boundaries.
 */
final class YandexForecast {
    private static Reading reading(JSONObject point) throws Exception {
        int temperature = point.getInt("temperature");
        WeatherCondition condition = WeatherCondition.fromYandex(point.getString("condition"));
        String icon = point.getString("icon");
        if (temperature < -100 || temperature > 70 || !icon.matches("[A-Za-z0-9_+\\-]+"))
            throw new IllegalArgumentException("Invalid forecast values");
        return new Reading(temperature, condition, icon);
    }

    static Reading at(String json, long now) throws Exception {
        JSONObject data = new JSONObject(json).getJSONObject("data");
        JSONObject weather = data.getJSONObject("weatherByPoint");
        JSONArray days = weather.getJSONObject("forecast").getJSONArray("days");
        for (int d = 0; d < days.length(); d++) {
            JSONArray hours = days.getJSONObject(d).getJSONArray("hours");
            for (int h = 0; h < hours.length(); h++) {
                JSONObject point = hours.getJSONObject(h);
                long time = java.time.OffsetDateTime.parse(point.getString("time")).toInstant().toEpochMilli();
                if (time <= now && now - time < HourClock.HOUR)
                    return reading(point);
            }
        }
        long issued = java.time.OffsetDateTime.parse(data.getString("serverTime")).toInstant().toEpochMilli();
        if (Math.floorDiv(issued, HourClock.HOUR) == Math.floorDiv(now, HourClock.HOUR))
            return reading(weather.getJSONObject("now"));
        return null; // Never label yesterday's observation with the current hour.
    }

}
