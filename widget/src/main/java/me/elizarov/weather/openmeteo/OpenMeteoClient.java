package me.elizarov.weather.openmeteo;

import me.elizarov.weather.ForecastHttp;
import me.elizarov.weather.LocationPolicy;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

final class OpenMeteoClient {
    static String parameters(LocationPolicy.Coordinates point) {
        return String.format(Locale.ROOT, "latitude=%.5f&longitude=%.5f", point.latitude, point.longitude)
                + "&hourly=temperature_2m,weather_code,is_day&forecast_hours=48";
    }

    static String forecast(LocationPolicy.Coordinates point) throws IOException, InterruptedException {
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
}
