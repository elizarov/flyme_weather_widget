package me.elizarov.weather;

import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;

/**
 * A widget click is routed using the same passenger-display options as Flyme.
 */
public final class SettingsLaunchReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent click) {
        ActivityOptions options = ActivityOptions.makeBasic();
        boolean car;
        try {
            context.getPackageManager().getApplicationInfo("com.flyme.auto.launcher", 0);
            car = true;
        } catch (PackageManager.NameNotFoundException unavailable) {
            car = false;
        }
        if (car) options.setLaunchDisplayId(1002);
        else {
            android.util.DisplayMetrics screen = context.getResources().getDisplayMetrics();
            options.setLaunchBounds(new Rect(0, 0, screen.widthPixels, screen.heightPixels));
        }
        Bundle bundle = options.toBundle();
        if (car) {
            bundle.putInt("from_displayid", 1002);
            bundle.putInt("start_windowmode", -2);
        }
        Intent settings = new Intent(context, WeatherSettingsActivity.class)
                .putExtra("light_text", click.getBooleanExtra("light_text", WidgetRenderer.lightText(context)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(settings, bundle);
    }
}
