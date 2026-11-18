package me.elizarov.weather;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;

import me.elizarov.weather.hmc.HmcForecastChecks;
import me.elizarov.weather.yandex.YandexForecastChecks;

/**
 * Offline checks are the default. The explicit live path performs at most one reserved request.
 */
final class WeatherApiChecks {
    static String local(Instrumentation test) throws Exception {
        Context c = test.getTargetContext();
        LocationPolicyChecks.run(c);
        SharedPreferences isolated = c.getSharedPreferences("api_throttle_test", Context.MODE_PRIVATE);
        isolated.edit().clear().commit();
        long fetched = java.time.Instant.parse("2026-11-17T09:45:00Z").toEpochMilli();
        long boundary = HourClock.next(fetched);
        require(RefreshPolicy.recordAttempt(isolated, "point-a", fetched), "first attempt recorded");
        require(!RefreshPolicy.recordAttempt(isolated, "point-a", fetched + 1), "same-hour retry blocked");
        require(RefreshPolicy.retryDelay(isolated, fetched + 1000) == boundary - fetched - 1000, "wait until calendar hour");
        require(RefreshPolicy.recordAttempt(isolated, "point-b", fetched + 1000), "location change may refresh within hour");
        require(RefreshPolicy.recordAttempt(isolated, "point-b", boundary), "next calendar hour is allowed");
        isolated.edit().clear().commit();
        YandexForecastChecks.run(c);
        HmcForecastChecks.checkConditions();
        HmcForecastChecks.checkLocationForm();
        String json = YandexForecastChecks.hourlyFixture();
        long now = java.time.Instant.parse("2026-11-17T12:59:59Z").toEpochMilli();
        Context isolatedContext = new android.content.ContextWrapper(c) {
            @Override
            public SharedPreferences getSharedPreferences(String name, int mode) {
                return c.getSharedPreferences("api_test_" + name, mode);
            }
        };
        SharedPreferences configPrefs = WeatherConfig.settings(isolatedContext);
        ForecastProvider<?> yandex = WeatherSource.YANDEX.provider;
        ForecastProvider<?> hmc = WeatherSource.HMC.provider;
        SharedPreferences yandexCache = yandex.cache(isolatedContext);
        SharedPreferences hmcCache = hmc.cache(isolatedContext);
        configPrefs.edit().clear().putString("source", yandex.id).commit();
        WeatherConfig config = WeatherConfig.read(isolatedContext);
        require(config.source == WeatherSource.YANDEX && config.source.provider == yandex, "settings resolve to the singleton strategy");
        yandexCache.edit().clear().putString("forecast", json).putString("config_key", config.key).commit();
        require(yandex.cards(isolatedContext, config, now).first.temperature == 3, "matching city uses its saved response");

        hmcCache.edit().clear().commit();
        configPrefs.edit().putString("source", hmc.id).commit();
        config = WeatherConfig.read(isolatedContext);
        require(config.source.provider == hmc && hmc.cachedForecast(hmcCache, config).isEmpty(),
                "different source cannot borrow the response");
        require(!config.source.provider.requiresApiKey() && config.source.provider.canFetch(config), "HMC works without credentials");

        configPrefs.edit().putString("source", yandex.id).commit();
        config = WeatherConfig.read(isolatedContext);
        require(config.source.provider == yandex && yandex.cards(isolatedContext, config, now).first.temperature == 3,
                "switching back reuses the same strategy and forecast");
        require(!yandex.canFetch(config), "Yandex requires an API key");
        configPrefs.edit().putString("city_path", "another-city").commit();
        config = WeatherConfig.read(isolatedContext);
        require(yandex.cards(isolatedContext, config, now).first.temperature == null, "different city cannot borrow old weather");
        require(yandex.website(isolatedContext, config).isEmpty(), "unknown new city never links to the old city's forecast");
        require(RefreshPolicy.recordAttempt(yandexCache, "point-a", now), "Yandex attempt recorded");
        require(RefreshPolicy.recordAttempt(hmcCache, "point-a", now), "attempt tracking is isolated by provider");
        require(!RefreshPolicy.recordAttempt(yandexCache, "point-a", now), "latest attempt blocks same-hour retry");
        configPrefs.edit().clear().commit();
        yandexCache.edit().clear().commit();
        hmcCache.edit().clear().commit();
        return "PASS: offline API parsing/hour boundaries, official light/dark icons, rounded coordinates, calendar-hour cache, 10 km location override and same-hour retry guard, WMO lookup and persistent station cache; zero network requests";
    }

    static String live(Instrumentation test) throws Exception {
        Context c = test.getTargetContext();
        java.io.File keyFile = new java.io.File(c.getFilesDir(), "api-test-key");
        require(keyFile.isFile(), "Supply the key privately to files/api-test-key");
        String key = new String(java.nio.file.Files.readAllBytes(keyFile.toPath()), java.nio.charset.StandardCharsets.UTF_8).trim();
        java.nio.file.Files.delete(keyFile.toPath());
        require(!key.isEmpty(), "Empty test key");
        SharedPreferences p = WeatherSource.YANDEX.provider.cache(c);
        require(RefreshPolicy.retryDelay(p, System.currentTimeMillis()) == 0, "No live request: existing cooldown is still active");
        test.runOnMainSync(() -> {
            WeatherConfig.settings(c).edit().putString("source", "yandex").putString("yandex_api_key", key)
                    .putString("city_path", "").putString("city_name", "Москва").putBoolean("automatic", false).commit();
            WeatherRepository.configurationChanged(c);
        });
        long until = android.os.SystemClock.elapsedRealtime() + 60000;
        while (!p.contains("forecast") && !p.contains("error")) {
            if (android.os.SystemClock.elapsedRealtime() > until)
                throw new AssertionError("Live request timed out; no retry was made");
            Thread.sleep(100);
        }
        require(!p.contains("error"), "Single live request failed: " + p.getString("error", ""));
        YandexForecastChecks.checkLiveForecast(c, p.getString("forecast", ""));
        require(RefreshPolicy.retryDelay(p, System.currentTimeMillis()) > 0, "Further API requests are blocked for the hour");
        // Only the source response (never the key) is exported for subsequent OFFLINE tests.
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(c.getExternalFilesDir(null), "api-response.json"))) {
            out.write(p.getString("forecast", "").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return "PASS: exactly one real Yandex request; Moscow current-hour forecast and official icon; hourly lock active; key not logged";
    }

    private static void require(boolean ok, String reason) {
        if (!ok) throw new AssertionError(reason);
    }
}
