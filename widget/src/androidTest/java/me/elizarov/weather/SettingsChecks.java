package me.elizarov.weather;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

final class SettingsChecks {
    static String run(Instrumentation test) throws Exception {
        Context context = test.getTargetContext();
        android.app.ActivityOptions options = android.app.ActivityOptions.makeBasic();
        options.setLaunchBounds(new android.graphics.Rect(0, 0, context.getResources().getDisplayMetrics().widthPixels,
                context.getResources().getDisplayMetrics().heightPixels));
        WeatherSettingsActivity activity = (WeatherSettingsActivity) test.startActivitySync(
                new Intent(context, WeatherSettingsActivity.class).putExtra("light_text", true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK), options.toBundle());
        waitFor(() -> !LocationCatalog.search(LocationCatalog.snapshot(context), "санкт петербург").isEmpty()
                && !LocationCatalog.search(LocationCatalog.snapshot(context), "москва").isEmpty(), 45000, "initial official catalog entries");
        List<LocationCatalog.Place> all = LocationCatalog.snapshot(context);
        long searchStart = SystemClock.elapsedRealtime();
        require(!LocationCatalog.search(all, "САНКТ-ПЕТЕРБУРГ").isEmpty(), "case and hyphen insensitive search");
        require(!LocationCatalog.search(all, "москва").isEmpty(), "Moscow search");
        long searchMillis = SystemClock.elapsedRealtime() - searchStart;
        AtomicReference<String> version = new AtomicReference<>();
        test.runOnMainSync(() -> version.set(((TextView) activity.getWindow().getDecorView().findViewWithTag("version")).getText().toString()));
        require(version.get().contains(BuildConfig.VERSION_NAME), "real package version in settings");
        select(test, activity, "москва", "russia/moscow-area/moscow");
        require(WeatherConfig.read(context).cityPath.equals("russia/moscow-area/moscow"), "selection persists");
        waitFor(() -> "27612".equals(WeatherRepository.prefs(context).getString("station", ""))
                && WeatherRepository.prefs(context).contains("xml"), 30000, "selected city refresh (Moscow)");
        require(WeatherForecast.parse(WeatherRepository.prefs(context).getString("xml", "")).get(0).city.contains("Москва"), "feed matches selected city");
        // The emulator has no real GPS fix: automatic mode must use the selected fallback.
        test.runOnMainSync(() -> WeatherConfig.automatic(context, true));
        waitFor(() -> "configured".equals(WeatherRepository.prefs(context).getString("mode", ""))
                && WeatherRepository.prefs(context).contains("xml"), 30000, "automatic fallback refresh");
        require(WeatherConfig.read(context).automatic, "automatic preference persists");
        require("27612".equals(WeatherRepository.prefs(context).getString("station", "")), "automatic fallback uses selected city");
        test.runOnMainSync(() -> WeatherConfig.automatic(context, false));
        select(test, activity, "санкт", "russia/leningrad-region/sankt-peterburg");
        waitFor(() -> "26063".equals(WeatherRepository.prefs(context).getString("station", ""))
                && WeatherRepository.prefs(context).contains("xml"), 30000, "selected city refresh (SPB)");
        require(WeatherRepository.prefs(context).getString("config_key", "").equals(WeatherConfig.read(context).key), "old request cannot overwrite new city");
        require(!WeatherConfig.read(context).automatic, "manual location mode restored");
        test.waitForIdleSync();
        test.runOnMainSync(() -> version.set(((TextView) activity.getWindow().getDecorView().findViewWithTag("version")).getText().toString()));
        require(version.get().contains("ID 26063"), "settings show actual selected station ID");
        require(!WeatherConditions.get(context, ForecastWindow.at(System.currentTimeMillis()).nightEnd, true).isEmpty(), "live detailed night conditions");
        if (!LocationCatalog.loading()) {
            LocationCatalog.ensure(context);
            require(!LocationCatalog.loading(), "fresh daily catalog is reused without refresh");
        }
        test.runOnMainSync(() -> {
            ((EditText) activity.getWindow().getDecorView().findViewWithTag("search")).setText("санкт-п");
            activity.getSystemService(android.view.inputmethod.InputMethodManager.class)
                    .hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(), 0);
        });
        test.waitForIdleSync();
        try (java.io.FileOutputStream image = new java.io.FileOutputStream(new java.io.File(context.getCacheDir(), "settings-preview.png"))) {
            android.graphics.Bitmap screenshot = test.getUiAutomation().takeScreenshot();
            require(screenshot != null, "settings screenshot");
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, image);
            screenshot.recycle();
        }
        return "PASS: settings UI, version, live official catalog, name search, persisted selection, immediate Moscow/SPB RSS refresh, auto fallback, config race guard, fresh catalog cache; search " + searchMillis + " ms over " + all.size() + " locations\n";
    }

    private static void select(Instrumentation test, WeatherSettingsActivity activity, String query, String path) {
        test.runOnMainSync(() -> {
            EditText input = activity.getWindow().getDecorView().findViewWithTag("search");
            ListView list = activity.getWindow().getDecorView().findViewWithTag("locations");
            input.setText(query);
            int selected = -1;
            for (int i = 0; i < list.getAdapter().getCount(); i++) {
                if (((LocationCatalog.Place) list.getAdapter().getItem(i)).path.equals(path)) {
                    selected = i;
                    break;
                }
            }
            require(selected >= 0, "requested official city is listed: " + path);
            list.performItemClick(null, selected, selected);
            require(!WeatherRepository.prefs(activity).contains("xml"), "old-city forecast cleared immediately");
        });
    }

    private interface Condition {
        boolean met() throws Exception;
    }

    private static void waitFor(Condition condition, long limit, String reason) throws Exception {
        long until = SystemClock.elapsedRealtime() + limit;
        while (!condition.met()) {
            if (SystemClock.elapsedRealtime() > until)
                throw new AssertionError("Timeout: " + reason);
            SystemClock.sleep(100);
        }
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
}
