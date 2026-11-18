package me.elizarov.weather;

import android.app.Instrumentation;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.PersistableBundle;

import java.util.Map;

import me.elizarov.weather.yandex.YandexForecastChecks;

/**
 * Exercises the actual JobService cache path; the deliberately invalid test key is never used.
 */
final class CacheJobChecks {
    static String run(Instrumentation test) throws Exception {
        Context c = test.getTargetContext();
        SharedPreferences settings = WeatherConfig.settings(c);
        SharedPreferences yandex = WeatherSource.YANDEX.provider.cache(c);
        SharedPreferences hmc = WeatherSource.HMC.provider.cache(c);
        Map<String, ?> savedSettings = settings.getAll(), savedYandex = yandex.getAll(), savedHmc = hmc.getAll();
        JobScheduler jobs = c.getSystemService(JobScheduler.class);
        jobs.cancel(WeatherRepository.JOB);
        try {
            long now = System.currentTimeMillis();
            settings.edit().clear().putString("source", "yandex").putString("yandex_api_key", "offline-invalid-test-key").commit();
            String key = WeatherConfig.read(c).key;
            LocationPolicy.Coordinates point = LocationPolicy.rounded(55.833, 37.617);
            String target = String.format(java.util.Locale.ROOT, "%.5f,%.5f", point.latitude, point.longitude);
            yandex.edit().clear().putString("config_key", key).putString("forecast", YandexForecastChecks.overnightFixture())
                    .putString("target", target).putLong("fetched_at", now)
                    .putLong("attempted_at", now - 1).putString("attempted_target", target).commit();
            WeatherRepository.schedule(c);
            JobInfo scheduled = jobs.getPendingJob(WeatherRepository.JOB);
            require(scheduled != null && Math.abs(scheduled.getMinLatencyMillis() - (HourClock.next(now) - now)) < 1000, "fresh persistent cache schedules next calendar hour");
            jobs.cancel(WeatherRepository.JOB);
            WeatherRepository.schedule(c);
            require(jobs.getPendingJob(WeatherRepository.JOB).getMinLatencyMillis() > 0, "rescheduling after job removal still uses persisted cache");
            jobs.cancel(WeatherRepository.JOB);
            // Changing auto/manual mode can resolve to the same point. Rebind, without another request.
            yandex.edit().putString("config_key", "old-configuration").remove("error").commit();
            launch(c, jobs);
            waitFor(yandex, "config_key", key);
            require(yandex.getLong("attempted_at", 0) == now - 1, "same point reuses data without reserving any API request");
            require(!yandex.contains("error"), "same-point cache path never reaches network");
            settings.edit().putString("source", "hmc").commit();
            String hmcKey = WeatherConfig.read(c).key;
            String xml;
            try (java.io.InputStream in = test.getContext().getAssets().open("spb.xml")) {
                xml = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            hmc.edit().clear().putString("forecast", xml).putString("target", "27612").putString("config_key", "old-configuration")
                    .putLong("fetched_at", now).putLong("attempted_at", now - 1).putString("attempted_target", "27612").commit();
            jobs.cancel(WeatherRepository.JOB);
            launch(c, jobs);
            waitFor(hmc, "config_key", hmcKey);
            require(hmc.getLong("attempted_at", 0) == now - 1, "same HMC station reuses current-hour RSS without HTTP");
            return "PASS: real JobService reuses same-point/station cache across configuration changes, persists cache between schedules, next-hour job latency; zero HTTP";
        } finally {
            jobs.cancel(WeatherRepository.JOB);
            restore(settings, savedSettings);
            restore(yandex, savedYandex);
            restore(hmc, savedHmc);
        }
    }

    private static void launch(Context c, JobScheduler jobs) {
        PersistableBundle extras = new PersistableBundle();
        extras.putLong("request_id", android.os.SystemClock.elapsedRealtimeNanos());
        require(jobs.schedule(new JobInfo.Builder(WeatherRepository.JOB, new ComponentName(c, WeatherUpdateService.class))
                .setExtras(extras).setOverrideDeadline(0).build()) == JobScheduler.RESULT_SUCCESS, "cache-check job scheduled");
    }

    private static void waitFor(SharedPreferences p, String name, String value) throws Exception {
        long until = android.os.SystemClock.elapsedRealtime() + 8000;
        while (!value.equals(p.getString(name, "")) || p.getBoolean("loading", false)) {
            require(android.os.SystemClock.elapsedRealtime() < until, "cache job completion: " + name);
            Thread.sleep(50);
        }
        // Allow jobFinished/finally to publish its completion before restoring preferences.
        Thread.sleep(100);
    }

    private static void restore(SharedPreferences p, Map<String, ?> values) {
        SharedPreferences.Editor e = p.edit().clear();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) e.putString(entry.getKey(), (String) value);
            else if (value instanceof Boolean) e.putBoolean(entry.getKey(), (Boolean) value);
            else if (value instanceof Long) e.putLong(entry.getKey(), (Long) value);
            else if (value instanceof Integer) e.putInt(entry.getKey(), (Integer) value);
            else if (value instanceof Float) e.putFloat(entry.getKey(), (Float) value);
        }
        e.commit();
    }

    private static void require(boolean yes, String why) {
        if (!yes) throw new AssertionError(why);
    }
}
