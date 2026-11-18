package me.elizarov.weather;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;

/**
 * Exercises the real source selector and isolation of location/network settings.
 */
final class SourceChecks {
    static String run(Instrumentation test) throws Exception {
        Context context = test.getTargetContext();
        WeatherConfig before = WeatherConfig.read(context);
        android.app.ActivityOptions options = android.app.ActivityOptions.makeBasic();
        options.setLaunchBounds(new android.graphics.Rect(0, 0, 5120, 1600));
        WeatherSettingsActivity activity = (WeatherSettingsActivity) test.startActivitySync(
                new Intent(context, WeatherSettingsActivity.class).putExtra("light_text", true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options.toBundle());
        for (WeatherSource source : WeatherSource.values()) {
            test.runOnMainSync(() -> activity.getWindow().getDecorView()
                    .findViewWithTag("source_" + source.provider.id).performClick());
            long until = android.os.SystemClock.elapsedRealtime() + 5000;
            while (WeatherConfig.read(context).source != source) {
                if (android.os.SystemClock.elapsedRealtime() > until)
                    throw new AssertionError("Source did not persist");
                Thread.sleep(50);
            }
            test.runOnMainSync(() -> {
                boolean keyShown = activity.getWindow().getDecorView().findViewWithTag("api_key_controls").isShown();
                if (keyShown != source.provider.requiresApiKey()) throw new AssertionError("Wrong key panel for " + source);
            });
            WeatherConfig after = WeatherConfig.read(context);
            if (!after.cityPath.equals(before.cityPath) || !after.cityName.equals(before.cityName) || after.automatic != before.automatic)
                throw new AssertionError("Source switch changed the HMC city or automatic mode");
            if (!source.provider.canFetch(after)
                    && context.getSystemService(android.app.job.JobScheduler.class).getPendingJob(WeatherRepository.JOB) != null)
                throw new AssertionError("API job scheduled without a key");
        }
        test.waitForIdleSync();
        java.util.concurrent.atomic.AtomicBoolean controls = new java.util.concurrent.atomic.AtomicBoolean();
        test.runOnMainSync(() -> controls.set(activity.getWindow().getDecorView().findViewWithTag("api_key_controls").isShown()
                && activity.getWindow().getDecorView().findViewWithTag("location_controls").isShown()
                && activity.getWindow().getDecorView().findViewWithTag("api_key").isShown()));
        if (!controls.get()) throw new AssertionError("Wrong location controls for Yandex source");
        Thread.sleep(300); // Wait for the changed visibility to reach SurfaceFlinger.
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null), "source-settings.png"))) {
            test.getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
        }
        test.runOnMainSync(activity::finish);
        return "PASS: real source selector, persisted source, shared city/automatic mode, API key panel";
    }
}
