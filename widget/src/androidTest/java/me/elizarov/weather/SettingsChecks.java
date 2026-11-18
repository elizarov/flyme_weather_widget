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
            Button yandex = source.findViewWithTag("source_yandex");
            require(hmc.getText().toString().equals("Гидрометцентр\nдень/ночь"), "HMC caption");
            require(yandex.getText().toString().equals("Яндекс Погода\nпо часам"), "Yandex caption");
            require(Math.abs(hmc.getWidth() - yandex.getWidth()) <= 1
                    && hmc.getLineCount() == 2 && yandex.getLineCount() == 2, "equal two-line buttons");
            require(hmc.isSelected() == (WeatherConfig.read(context).source == WeatherSource.HMC)
                    && hmc.isSelected() != yandex.isSelected(), "saved source selected");
            require(root.findViewWithTag("version") == null, "version removed from header");
            TextView title = root.findViewWithTag("settings_title");
            View back = root.findViewWithTag("settings_back");
            android.graphics.Rect ink = new android.graphics.Rect();
            title.getPaint().getTextBounds(title.getText().toString(), 0, title.length(), ink);
            float center = title.getTop() + title.getBaseline() + (ink.top + ink.bottom) / 2f;
            require(Math.abs(back.getY() + back.getHeight() / 2f - center) < 0.6f, "back arrow centered on heading ink");
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
        android.app.Instrumentation.ActivityMonitor monitor = test.addMonitor(WeatherBrowserActivity.class.getName(), null, false);
        List<AccessibilityNodeInfo> links = popup.findAccessibilityNodeInfosByText("https://github.com/elizarov/flyme_weather_widget");
        AccessibilityNodeInfo link = links.get(0);
        while (link != null && !link.isClickable()) link = link.getParent();
        require(link != null && link.performAction(AccessibilityNodeInfo.ACTION_CLICK), "GitHub link is clickable");
        android.app.Activity browser = test.waitForMonitorWithTimeout(monitor, 5000);
        require(browser != null && "https://github.com/elizarov/flyme_weather_widget".equals(browser.getIntent().getStringExtra("url")), "link opens browser at repository URL");
        test.runOnMainSync(browser::finish);
        test.removeMonitor(monitor);
        test.runOnMainSync(activity::finish);
        return "PASS: centered source row/gap, header Info, About version/update/source-code, clickable GitHub browser link";
    }

    private static void require(boolean yes, String reason) {
        if (!yes) throw new AssertionError(reason);
    }
}
