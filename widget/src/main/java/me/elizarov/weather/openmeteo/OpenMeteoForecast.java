package me.elizarov.weather.openmeteo;

import me.elizarov.weather.HourClock;
import me.elizarov.weather.HourlyForecastProvider.Reading;
import me.elizarov.weather.WeatherCondition;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class OpenMeteoForecast {
    static Reading at(String json, long time) throws JSONException {
        JSONObject hourly = new JSONObject(json).getJSONObject("hourly");
        JSONArray times = hourly.getJSONArray("time");
        long seconds = Math.floorDiv(time, 1000);
        for (int i = 0; i < times.length(); i++) {
            long offset = seconds - times.getLong(i);
            if (offset < 0 || offset >= HourClock.HOUR / 1000) continue;
            double temperature = hourly.getJSONArray("temperature_2m").optDouble(i);
            if (!Double.isFinite(temperature) || temperature < -100 || temperature > 70) return null;
            WeatherCondition condition = WeatherCondition.fromWmo(hourly.getJSONArray("weather_code").optInt(i, -1));
            boolean night = hourly.getJSONArray("is_day").getInt(i) == 0;
            return new Reading((int) Math.round(temperature), condition, condition.icon(night));
        }
        return null;
    }
}
