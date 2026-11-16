package me.elizarov.weather;

import android.content.BroadcastReceiver;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;

/**
 * Read-only deployment diagnostic: resolve the launcher's effective typed XML.
 */
public final class OverlayProbeReceiver extends BroadcastReceiver {
    @SuppressLint("ResourceType") // These styleable indices belong to the external target APK.
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if ("me.elizarov.weather.WEATHER_STATUS".equals(intent.getAction())) {
                WeatherConfig config = WeatherConfig.read(context);
                JSONObject status = new JSONObject();
                status.put("fallback_station", config.fallbackStation);
                status.put("auto_location", config.automatic);
                status.put("default_city", config.cityName);
                status.put("default_path", config.cityPath);
                android.content.SharedPreferences p = WeatherRepository.prefs(context);
                for (String field : new String[]{"station", "located_station", "mode", "error", "conditions_error"})
                    status.put(field, p.getString(field, ""));
                status.put("fine_location_permission", context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED);
                status.put("background_location_permission", context.checkSelfPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED);
                ForecastWindow window = ForecastWindow.at(System.currentTimeMillis());
                status.put("day_conditions", WeatherConditions.get(context, window.day, false));
                status.put("night_conditions", WeatherConditions.get(context, window.nightEnd, true));
                status.put("fetched_at", p.getLong("fetched_at", 0));
                WeatherForecast forecast = WeatherRepository.forTime(context, java.time.LocalDate.now(), java.time.LocalTime.now().getHour());
                if (forecast != null) {
                    status.put("city", forecast.city);
                    status.put("day", forecast.day);
                    status.put("night", forecast.night);
                }
                setResultCode(0);
                setResultData(status.toString());
                return;
            }
            String pkg = "com.flyme.auto.launcher";
            Resources resources = context.createPackageContext(pkg, 0).getResources();
            int[] attributes = new int[4];
            String[] names = {"displayType", "widgetDisplay", "widgetPackageName", "widgetClassName"};
            for (int i = 0; i < names.length; i++)
                attributes[i] = resources.getIdentifier(names[i], "attr", pkg);
            int displayIndexId = attributes[0], typeIndexId = attributes[1];
            int packageIndexId = attributes[2], classIndexId = attributes[3];
            Arrays.sort(attributes);
            JSONArray entries = new JSONArray();
            int display = -1, type = -1;
            try (XmlResourceParser parser = resources.getXml(resources.getIdentifier("widget_profiles", "xml", pkg))) {
                int event;
                while ((event = parser.next()) != XmlResourceParser.END_DOCUMENT) {
                    if (event != XmlResourceParser.START_TAG) continue;
                    TypedArray values = resources.obtainAttributes(Xml.asAttributeSet(parser), attributes);
                    try {
                        if ("section".equals(parser.getName())) {
                            display = values.getInt(Arrays.binarySearch(attributes, displayIndexId), -1);
                            type = values.getInt(Arrays.binarySearch(attributes, typeIndexId), -1);
                        } else if ("appwidget".equals(parser.getName())) {
                            JSONObject item = new JSONObject();
                            item.put("display", display);
                            item.put("type", type);
                            item.put("package", values.getString(Arrays.binarySearch(attributes, packageIndexId)));
                            item.put("class", values.getString(Arrays.binarySearch(attributes, classIndexId)));
                            entries.put(item);
                        }
                    } finally {
                        values.recycle();
                    }
                }
            }
            setResultCode(0);
            setResultData(entries.toString());
        } catch (Exception error) {
            setResultCode(1);
            setResultData(error.toString());
        }
    }
}
