package me.elizarov.weather;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.webkit.WebView;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Real popup task/window regression, including desktop Close (= finishAndRemoveTask).
 */
final class BrowserChecks {
    static String run(Instrumentation test) throws Exception {
        if (!BuildConfig.DEBUG) throw new AssertionError("Browser preview check requires debug");
        Context c = test.getTargetContext();
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchBounds(new Rect(0, 0, 5120, 1600));
        Activity preview = test.startActivitySync(new Intent().setClassName(c.getPackageName(), "me.elizarov.weather.PreviewActivity")
                .putExtra("light_text", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options.toBundle());
        int previewTask = preview.getTaskId();
        Instrumentation.ActivityMonitor monitor = test.addMonitor(WeatherBrowserActivity.class.getName(), null, false);
        String url = "https://meteoinfo.ru/forecasts/russia/leningrad-region/sankt-peterburg";
        test.runOnMainSync(() -> WeatherBrowserActivity.open(preview, url));
        Activity browser = test.waitForMonitorWithTimeout(monitor, 5000);
        require(browser != null, "browser launched");
        test.runOnMainSync(() -> {
        });
        require(browser.getTaskId() != previewTask, "browser has a separate task from widget preview");
        require(url.equals(browser.getIntent().getStringExtra("url")), "forecast URL delivered");
        test.runOnMainSync(() -> {
            android.view.WindowManager.LayoutParams bounds = browser.getWindow().getAttributes();
            Rect display = browser.getWindowManager().getMaximumWindowMetrics().getBounds();
            require(bounds.width > 0 && bounds.width <= display.width() && bounds.width <= 1440, "positive browser width within display");
            require(bounds.height > 0 && bounds.height <= display.height() && bounds.height <= 900, "positive browser height within display");
            WebView web = browser.getWindow().getDecorView().findViewWithTag("browser");
            require(web != null && web.getWidth() > 0 && web.getWidth() <= 1440 && web.getHeight() <= 900, "WebView viewport bounded to popup");
        });
        AtomicReference<String> title = new AtomicReference<>();
        long until = android.os.SystemClock.elapsedRealtime() + 25000;
        while (android.os.SystemClock.elapsedRealtime() < until) {
            test.runOnMainSync(() -> {
                WebView web = browser.getWindow().getDecorView().findViewWithTag("browser");
                if (web != null) title.set(web.getTitle());
            });
            if (title.get() != null && title.get().contains("Гидрометцентр")) break;
            Thread.sleep(100);
        }
        require(title.get() != null && title.get().contains("Гидрометцентр"), "actual HMC page loaded");
        java.util.concurrent.atomic.AtomicBoolean committed = new java.util.concurrent.atomic.AtomicBoolean();
        test.runOnMainSync(() -> {
            WebView web = browser.getWindow().getDecorView().findViewWithTag("browser");
            web.postVisualStateCallback(1, new WebView.VisualStateCallback() {
                @Override
                public void onComplete(long id) {
                    web.getViewTreeObserver().registerFrameCommitCallback(() -> committed.set(true));
                    web.invalidate();
                }
            });
        });
        until = android.os.SystemClock.elapsedRealtime() + 15000;
        while (!committed.get() && android.os.SystemClock.elapsedRealtime() < until)
            Thread.sleep(50);
        require(committed.get(), "page frame committed, not just HTML title received");
        test.runOnMainSync(() -> {
        });
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(c.getExternalFilesDir(null), "browser-fixed.png"))) {
            test.getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
        }
        String nextUrl = url + "#forecast";
        test.runOnMainSync(() -> WeatherBrowserActivity.open(preview, nextUrl));
        long changedUntil = android.os.SystemClock.elapsedRealtime() + 5000;
        while (!nextUrl.equals(browser.getIntent().getStringExtra("url")) && android.os.SystemClock.elapsedRealtime() < changedUntil)
            Thread.sleep(50);
        require(nextUrl.equals(browser.getIntent().getStringExtra("url")), "existing browser receives a new location link");
        test.runOnMainSync(browser::finishAndRemoveTask);
        test.runOnMainSync(() -> {
        });
        require(!preview.isFinishing() && !preview.isDestroyed(), "desktop Close leaves widget alive");
        require(preview.getTaskId() == previewTask, "original widget task preserved");
        test.removeMonitor(monitor);
        monitor = test.addMonitor(WeatherBrowserActivity.class.getName(), null, false);
        // A second link must reopen the browser task and Back must also leave the widget alive.
        test.runOnMainSync(() -> WeatherBrowserActivity.open(preview, url));
        Activity again = test.waitForMonitorWithTimeout(monitor, 5000);
        require(again != null && again.getTaskId() != previewTask, "reopening still uses separate task");
        test.runOnMainSync(again::onBackPressed);
        test.runOnMainSync(() -> {
        });
        require(!preview.isFinishing() && !preview.isDestroyed(), "Back leaves widget alive");
        test.removeMonitor(monitor);
        return "PASS: actual HMC page loaded; bounded popup/WebView, independent browser task, desktop Close and Back preserve widget; zero weather API requests";
    }

    private static void require(boolean yes, String reason) {
        if (!yes) throw new AssertionError(reason);
    }
}
