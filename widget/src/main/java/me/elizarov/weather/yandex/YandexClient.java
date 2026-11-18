package me.elizarov.weather.yandex;

import me.elizarov.weather.LocationPolicy;
import me.elizarov.weather.ForecastHttp;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class YandexClient {
    static final String ENDPOINT = "https://api.weather.yandex.ru/graphql/query";

    static final class YandexFailure extends java.io.IOException {
        final String reason;

        YandexFailure(String reason) {
            super(reason);
            this.reason = reason;
        }
    }

    static String query(LocationPolicy.Coordinates point) {
        String fields = "temperature(unit: CELSIUS) feelsLike(unit: CELSIUS) condition icon(format: CODE)";
        return String.format(java.util.Locale.ROOT,
                "{serverTime weatherByPoint(request:{lat:%.5f,lon:%.5f},language:RU){now{%s} forecast{days(limit:2){hours{time %s}}}}}",
                point.latitude, point.longitude, fields, fields);
    }

    static String forecast(String key, LocationPolicy.Coordinates point) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(false);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        c.setRequestProperty("X-Yandex-Weather-Key", key);
        try {
            try (java.io.OutputStream out = c.getOutputStream()) {
                out.write(new JSONObject().put("query", query(point)).toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = c.getResponseCode();
            if (status != 200)
                throw new YandexFailure(status == 401 || status == 403 ? "auth" : status == 429 ? "quota" : "network");
            String result = ForecastHttp.read(c);
            if (new JSONObject(result).optJSONArray("errors") != null)
                throw new YandexFailure("api");
            return result;
        } finally {
            c.disconnect();
        }
    }
}
