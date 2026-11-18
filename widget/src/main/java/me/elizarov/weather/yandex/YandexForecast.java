package me.elizarov.weather.yandex;

import me.elizarov.weather.HourClock;
import me.elizarov.weather.WeatherCondition;
import me.elizarov.weather.WeatherIcons;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Yandex forecast values selected by absolute hour, including DST boundaries.
 */
final class YandexForecast {
    final long time;
    final int temperature, feelsLike;
    final WeatherCondition condition;
    final String icon;

    private YandexForecast(JSONObject point, long time) throws Exception {
        this.time = time;
        temperature = point.getInt("temperature");
        feelsLike = point.optInt("feelsLike", temperature);
        condition = WeatherCondition.fromYandex(point.getString("condition"));
        icon = point.getString("icon");
        if (temperature < -100 || temperature > 70 || !icon.matches("[A-Za-z0-9_+\\-]+"))
            throw new IllegalArgumentException("Invalid forecast values");
    }

    static YandexForecast at(String json, long now) throws Exception {
        JSONObject data = new JSONObject(json).getJSONObject("data");
        JSONObject weather = data.getJSONObject("weatherByPoint");
        long hour = Math.floorDiv(now, HourClock.HOUR) * HourClock.HOUR;
        JSONArray days = weather.getJSONObject("forecast").getJSONArray("days");
        for (int d = 0; d < days.length(); d++) {
            JSONArray hours = days.getJSONObject(d).getJSONArray("hours");
            for (int h = 0; h < hours.length(); h++) {
                JSONObject point = hours.getJSONObject(h);
                long time = java.time.OffsetDateTime.parse(point.getString("time")).toInstant().toEpochMilli();
                if (time <= now && now - time < HourClock.HOUR)
                    return new YandexForecast(point, time);
            }
        }
        long issued = java.time.OffsetDateTime.parse(data.getString("serverTime")).toInstant().toEpochMilli();
        if (Math.floorDiv(issued, HourClock.HOUR) == Math.floorDiv(now, HourClock.HOUR))
            return new YandexForecast(weather.getJSONObject("now"), hour);
        return null; // Never label yesterday's observation with the current hour.
    }

    int icon(android.content.Context context, boolean lightText) {
        return WeatherIcons.resource(context, icon, lightText);
    }
}
