package me.elizarov.weather.openmeteo;

import me.elizarov.weather.ForecastHttp;
import me.elizarov.weather.HourClock;
import me.elizarov.weather.HourlyForecastProvider;
import me.elizarov.weather.LocationPolicy;
import me.elizarov.weather.WeatherCondition;
import me.elizarov.weather.WeatherConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public final class OpenMeteoForecastProvider extends HourlyForecastProvider {
    public static final OpenMeteoForecastProvider INSTANCE = new OpenMeteoForecastProvider();

    private OpenMeteoForecastProvider() {
        super("openmeteo", "Open-Meteo", "Данные Open-Meteo", "");
    }

    private String parameters(LocationPolicy.Coordinates point) {
        return String.format(Locale.ROOT, "latitude=%.5f&longitude=%.5f", point.latitude, point.longitude)
                + "&hourly=temperature_2m,weather_code,is_day&forecast_hours=48";
    }

    @Override
    protected String fetch(WeatherConfig config, LocationPolicy.Coordinates point) throws Exception {
        URL url = new URL("https://api.open-meteo.com/v1/forecast?" + parameters(point) + "&timeformat=unixtime&timezone=GMT");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept", "application/json");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Open-Meteo forecast unavailable");
            return ForecastHttp.read(connection);
        } finally {
            connection.disconnect();
        }
    }

    @Override
    protected Reading readingAt(String json, long time) throws JSONException {
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

    @Override
    protected String pointUrl(LocationPolicy.Coordinates point) {
        return "https://open-meteo.com/en/docs?" + parameters(point) + "&timezone=auto#api_response";
    }
}
