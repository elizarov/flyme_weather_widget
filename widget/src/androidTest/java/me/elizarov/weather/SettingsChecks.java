package me.elizarov.weather;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Checks the actual dialog; a local catalog fixture avoids network access.
 */
final class SettingsChecks {
    static String run(Instrumentation test) throws Exception {
        Context context = test.getTargetContext();
        android.app.ActivityOptions options = android.app.ActivityOptions.makeBasic();
        options.setLaunchBounds(new android.graphics.Rect(0, 0, 5120, 1600));
        WeatherSettingsActivity activity = (WeatherSettingsActivity) test.startActivitySync(
                new Intent(context, WeatherSettingsActivity.class).putExtra("light_text", true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options.toBundle());
        test.waitForIdleSync();
        test.runOnMainSync(() -> {
            View root = activity.getWindow().getDecorView();
            TextView label = root.findViewWithTag("source_label");
            LinearLayout source = root.findViewWithTag("source");
            require(label.getHeight() == source.getHeight(), "source label and choices share vertical bounds");
            require(source.getLeft() - label.getRight() >= 36, "larger source-selector gap");
            Button hmc = source.findViewWithTag("source_hmc");
            Button openMeteo = source.findViewWithTag("source_openmeteo");
            Button yandex = source.findViewWithTag("source_yandex");
            require(hmc.getText().toString().equals("Гидрометцентр\nдень/ночь"), "HMC caption");
            require(yandex.getText().toString().equals("Яндекс Погода\nпо часам"), "Yandex caption");
            require(openMeteo.getText().toString().equals("Open-Meteo\nпо часам"), "Open-Meteo caption");
            require(source.getChildAt(0) == hmc && source.getChildAt(1) == openMeteo && source.getChildAt(2) == yandex,
                    "HMC, Open-Meteo, Yandex in order");
            for (WeatherSource candidate : WeatherSource.values()) {
                Button choice = source.findViewWithTag("source_" + candidate.provider.id);
                require(Math.abs(choice.getWidth() - hmc.getWidth()) <= 1 && choice.getLineCount() == 2,
                        "equal two-line buttons");
                require(choice.isSelected() == (WeatherConfig.read(context).source == candidate), "saved source selected");
            }
            require(root.findViewWithTag("version") == null, "version removed from header");
            TextView title = root.findViewWithTag("settings_title");
            View close = root.findViewWithTag("settings_close");
            require("Закрыть".contentEquals(close.getContentDescription()), "close button");
            View header = (View) title.getParent();
            require(Math.abs(title.getLeft() + title.getRight() - header.getWidth()) <= 1,
                    "heading centered in popup");
            root.findViewWithTag("about").performClick();
        });
        test.waitForIdleSync();
        AccessibilityNodeInfo popup = null;
        long until = android.os.SystemClock.elapsedRealtime() + 5000;
        while (android.os.SystemClock.elapsedRealtime() < until) {
            popup = test.getUiAutomation().getRootInActiveWindow();
            if (popup != null && !popup.findAccessibilityNodeInfosByText("Версия " + BuildConfig.VERSION_NAME).isEmpty())
                break;
            Thread.sleep(100);
        }
        require(popup != null, "About popup accessibility root");
        require(!popup.findAccessibilityNodeInfosByText("Версия " + BuildConfig.VERSION_NAME).isEmpty(), "version in About popup");
        require(!popup.findAccessibilityNodeInfosByText("Последнее обновление прогноза").isEmpty(), "last refresh in About popup");
        require(!popup.findAccessibilityNodeInfosByText("Открытый исходный код").isEmpty(), "source code caption");
        require(!popup.findAccessibilityNodeInfosByText("https://github.com/elizarov/flyme_weather_widget").isEmpty(), "GitHub link");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null), "about.png"))) {
            test.getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
        }
        java.util.concurrent.BlockingQueue<Intent> launches = new java.util.concurrent.LinkedBlockingQueue<>();
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor() {
            @Override
            public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                launches.offer(intent);
                return new Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null);
            }
        };
        test.addMonitor(monitor);
        List<AccessibilityNodeInfo> links = popup.findAccessibilityNodeInfosByText("https://github.com/elizarov/flyme_weather_widget");
        AccessibilityNodeInfo link = links.get(0);
        while (link != null && !link.isClickable()) link = link.getParent();
        require(link != null && link.performAction(AccessibilityNodeInfo.ACTION_CLICK), "GitHub link is clickable");
        Intent launch = launches.poll(5, java.util.concurrent.TimeUnit.SECONDS);
        require(launch != null && Intent.ACTION_VIEW.equals(launch.getAction())
                && "https://github.com/elizarov/flyme_weather_widget".equals(launch.getDataString())
                && launch.getComponent() == null && (launch.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0,
                "repository link launches an external browser task");
        test.removeMonitor(monitor);
        test.runOnMainSync(activity::finish);
        return "PASS: centered source row/gap, header Info, About version/update/source-code, clickable GitHub browser link";
    }

    private static void require(boolean yes, String reason) {
        if (!yes) throw new AssertionError(reason);
    }
}
