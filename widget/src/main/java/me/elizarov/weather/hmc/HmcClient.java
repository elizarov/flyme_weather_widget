package me.elizarov.weather.hmc;

import me.elizarov.weather.BuildConfig;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class HmcClient {
    private HmcClient() {
    }

    public static String cityPage(String path) throws Exception {
        return request("https://meteoinfo.ru/forecasts" + (path.isEmpty() ? "" : "/" + path), null);
    }

    public static String forecast(String station) throws Exception {
        return request("https://meteoinfo.ru/rss/forecasts/index.php?s=" + station, null);
    }

    public static String nearbyStation(String form) throws Exception {
        return request("https://meteoinfo.ru/hmc-output/forecast/tab_0.php", form);
    }

    private static String request(String address, String body) throws Exception {
        long began = BuildConfig.DEBUG ? android.os.SystemClock.elapsedRealtime() : 0;
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", "WeatherWidget/" + BuildConfig.VERSION_NAME
                + " (personal noncommercial forecast display)");
        connection.setRequestProperty("Accept", "application/rss+xml, application/xml, application/json, text/html");
        try {
            if (body != null) {
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                try (java.io.OutputStream out = connection.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            if (connection.getResponseCode() != 200)
                throw new java.io.IOException("HTTP " + connection.getResponseCode());
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    out.write(buffer, 0, count);
                    if (out.size() > 1048576) throw new java.io.IOException("Response too large");
                }
                return out.toString("UTF-8");
            }
        } finally {
            connection.disconnect();
            if (BuildConfig.DEBUG) {
                android.util.Log.d("WeatherWidget", "HMC " + new URL(address).getPath() + ": "
                        + (android.os.SystemClock.elapsedRealtime() - began) + " ms");
            }
        }
    }
}
