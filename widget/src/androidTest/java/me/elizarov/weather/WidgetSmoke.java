package me.elizarov.weather;

import android.app.Instrumentation;
import android.os.Bundle;

/**
 * Offline instrumented checks use isolated preferences and never need an API key.
 */
public final class WidgetSmoke extends Instrumentation {
    private Bundle arguments;

    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        arguments = args == null ? new Bundle() : args;
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            String message;
            if (arguments.containsKey("api"))
                message = "live".equals(arguments.getString("api")) ? WeatherApiChecks.live(this) : WeatherApiChecks.local(this);
            else if ("true".equals(arguments.getString("browser")))
                message = BrowserChecks.run(this);
            else if ("true".equals(arguments.getString("cache")))
                message = CacheJobChecks.run(this);
            else if ("true".equals(arguments.getString("sources")))
                message = SourceChecks.run(this);
            else if ("true".equals(arguments.getString("settings")))
                message = SettingsChecks.run(this);
            else {
                WeatherCardChecks.run(this);
                message = "PASS: RemoteViews, identical two-card layout, OEM clock/date typography, 08:00/20:00 periods, midnight/month/year/timezone boundaries, RSS parsing/conditions, official icons, hourly persistent cache and same-hour retry guard; zero HTTP";
            }
            result.putString("stream", message);
            finish(-1, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
            finish(1, result);
        }
    }
}
