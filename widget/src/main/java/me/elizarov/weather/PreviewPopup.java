package me.elizarov.weather;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.view.Gravity;
import android.view.WindowManager;

final class PreviewPopup {
    private static final String BOUNDED_TASK = "me.elizarov.weather.BOUNDED_PREVIEW_TASK";

    private PreviewPopup() {
    }

    static void setLaunchBounds(Context context, ActivityOptions options, Intent intent, Rect display,
                                int width, int height) {
        boolean emulator = Build.FINGERPRINT.startsWith("generic") || Build.MODEL.startsWith("sdk_");
        if (!BuildConfig.DEBUG || !emulator || context.getResources()
                .getIdentifier("preview_flyme_screen", "drawable", context.getPackageName()) == 0) {
            return;
        }
        // Freeform tasks have an opaque backing surface. Confine it to the popup.
        int taskWidth = Math.min(width, display.width());
        int taskHeight = Math.min(height + 42, display.height()); // Space for the emulator's window caption.
        int right = display.right - Math.min(512, Math.max(0, display.width() - taskWidth));
        int top = display.top + Math.min(180, Math.max(0, display.height() - taskHeight));
        options.setLaunchBounds(new Rect(right - taskWidth, top, right, top + taskHeight));
        intent.putExtra(BOUNDED_TASK, true);
    }

    static boolean layoutInTask(Activity activity, WindowManager.LayoutParams bounds, int width, int height) {
        if (!activity.getIntent().getBooleanExtra(BOUNDED_TASK, false)) return false;
        WindowManager manager = activity.getWindowManager();
        Rect task = manager.getCurrentWindowMetrics().getBounds();
        if (task.width() >= manager.getMaximumWindowMetrics().getBounds().width()) return false;
        bounds.width = Math.min(width, Math.max(1, task.width()));
        bounds.height = Math.min(height, Math.max(1, task.height()));
        bounds.gravity = Gravity.TOP | Gravity.START;
        bounds.x = 0;
        bounds.y = 0;
        return true;
    }
}
