package me.elizarov.weather;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.time.Instant;
import java.util.TimeZone;

import me.elizarov.weather.hmc.HmcForecastChecks;
import me.elizarov.weather.yandex.YandexForecastChecks;

final class WeatherCardChecks {
    static long time(String value) {
        return Instant.parse(value).toEpochMilli();
    }

    static void require(boolean yes, String why) {
        if (!yes) throw new AssertionError(why);
    }

    static void run(Instrumentation test) throws Exception {
        Context base = test.getTargetContext();
        Context c = new android.content.ContextWrapper(base) {
            @Override
            public SharedPreferences getSharedPreferences(String name, int mode) {
                return base.getSharedPreferences("cards_test_" + name, mode);
            }
        };
        WeatherConfig.settings(c).edit().clear().commit();
        SharedPreferences hmc = WeatherSource.HMC.provider.cache(c);
        SharedPreferences yandexCache = WeatherSource.YANDEX.provider.cache(c);
        hmc.edit().clear().commit();
        yandexCache.edit().clear().commit();
        require(WeatherConfig.read(c).fallbackStation.equals("27612"), "default station");
        String rss = asset(test, "spb.xml");
        String conditions = HmcForecastChecks.parseAndCheck(rss, asset(test, "spb-details.html"));
        hmc.edit().putString("forecast", rss).putString("config_key", WeatherConfig.read(c).key)
                .putString("target", "26063").putString("conditions", conditions).commit();
        TimeZone saved = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"));
            HmcForecastChecks.checkPeriods();
            require(HourlyForecastProvider.hourCaption(time("2026-11-18T00:00:00Z"), time("2026-11-17T21:00:00Z")).equals("3:00"), "same local date");
            require(HourlyForecastProvider.hourCaption(time("2026-11-17T23:00:00Z"), time("2026-11-17T20:00:00Z")).equals("2:00 +1"), "future card tomorrow");
            long fetched = time("2026-11-17T09:45:00Z"), boundary = time("2026-11-17T10:00:00Z");
            require(HourClock.next(fetched) == boundary, "12:45 fetch schedules 13:00");
            hmc.edit().putLong("fetched_at", fetched).commit();
            require(RefreshPolicy.hasFreshForecast(hmc, "26063", boundary - 1), "same-hour cache survives manual refresh");
            require(!RefreshPolicy.hasFreshForecast(hmc, "26063", boundary), "different hour reloads immediately");
            require(!RefreshPolicy.hasFreshForecast(hmc, "27612", fetched + 1), "new station reloads in same hour");
            require(RefreshPolicy.hasFreshForecast(WeatherSource.HMC.provider.cache(c), "26063", fetched + 1000),
                    "persisted timestamp is reused by reopened cache");
            test.runOnMainSync(() -> {
                View daytime = render(c, time("2026-11-16T12:14:00Z"), true);
                equal(daytime, R.id.weather_first_temperature, "+4°");
                equal(daytime, R.id.weather_second_temperature, "+2°");
                equal(daytime, R.id.weather_first_caption, "день");
                equal(daytime, R.id.weather_second_caption, "ночь 16→17");
                equal(daytime, R.id.weather_credit, "Данные Гидрометцентра");
                geometry(daytime);
                View evening = render(c, time("2026-11-16T18:00:00Z"), false);
                equal(evening, R.id.weather_first_temperature, "+2°");
                equal(evening, R.id.weather_second_temperature, "+5°");
                equal(evening, R.id.weather_first_caption, "ночь 16→17");
                equal(evening, R.id.weather_second_caption, "день 17");
                equal(evening, R.id.appwidgetr_time, "21:00");
                View midnight = render(c, time("2026-11-16T21:00:00Z"), true);
                equal(midnight, R.id.appwidgetr_data_day, "17");
                equal(midnight, R.id.weather_second_caption, "день");
                require(((TextView) daytime.findViewById(R.id.appwidget_data_month)).getTextSize() == 130, "OEM digits preserved");
                require(((TextView) daytime.findViewById(R.id.appwidget_week)).getTextSize() == 48, "OEM weekday preserved");
                WeatherConfig.settings(c).edit().putString("source", "yandex").commit();
                yandexCache.edit().putString("config_key", WeatherConfig.read(c).key)
                        .putString("forecast", YandexForecastChecks.overnightFixture()).commit();
                View yandex = render(c, time("2026-11-17T20:15:00Z"), true);
                equal(yandex, R.id.weather_first_caption, "23:00");
                equal(yandex, R.id.weather_second_caption, "2:00 +1");
                equal(yandex, R.id.weather_first_temperature, "−30°".replace('−', '-'));
                equal(yandex, R.id.weather_second_temperature, "+2°");
                equal(yandex, R.id.weather_credit, "Данные сервиса Яндекс Погода");
                geometry(yandex);
                require(daytime.findViewById(R.id.weather_first_card).getWidth() == yandex.findViewById(R.id.weather_first_card).getWidth(), "identical provider geometry");
                WeatherConfig.settings(c).edit().putString("source", "openmeteo").commit();
                WeatherConfig config = WeatherConfig.read(c);
                require(config.source == WeatherSource.OPEN_METEO && config.source.provider.canFetch(config),
                        "Open-Meteo works without credentials");
                SharedPreferences openMeteo = config.source.provider.cache(c);
                long start = time("2026-11-17T20:00:00Z") / 1000;
                String json = "{\"hourly\":{\"time\":[" + start + "," + (start + 10800)
                        + "],\"temperature_2m\":[-3.7,2.6],\"weather_code\":[71,0],\"is_day\":[1,0]}}";
                openMeteo.edit().clear().putString("config_key", config.key).putString("forecast", json).commit();
                long now = time("2026-11-17T20:59:00Z");
                View open = render(c, now, true);
                equal(open, R.id.weather_first_caption, "23:00");
                equal(open, R.id.weather_second_caption, "2:00 +1");
                equal(open, R.id.weather_first_temperature, "-4°");
                equal(open, R.id.weather_second_temperature, "+3°");
                equal(open, R.id.weather_credit, "Данные Open-Meteo");
                WeatherCards cards = config.source.provider.cards(c, config, now);
                require(cards.first.icon.equals("ovc_-sn") && cards.second.icon.equals("skc_n"), "WMO code and day/night icons");
                require(config.source.provider.cards(c, config, now + 60000).first.temperature == null,
                        "missing hourly sample never reuses an older temperature");
                openMeteo.edit().clear().commit();
                TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
                View west = render(c, time("2027-01-01T02:30:00Z"), true);
                equal(west, R.id.appwidget_data_month, "12");
                equal(west, R.id.appwidgetr_data_day, "31");
                TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"));
                equal(render(c, time("2028-02-28T21:01:00Z"), true), R.id.appwidgetr_data_day, "29");
            });
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kathmandu"));
            require(HourClock.next(time("2026-11-17T06:59:00Z")) == time("2026-11-17T07:15:00Z"), "fractional timezone hour boundary");
        } finally {
            TimeZone.setDefault(saved);
            hmc.edit().clear().commit();
            yandexCache.edit().clear().commit();
            WeatherConfig.settings(c).edit().clear().commit();
        }
        arrowAlignment(base);
        WeatherApiChecks.local(test);
    }

    private static void arrowAlignment(Context c) throws Exception {
        for (String caption : new String[]{"ночь 17→18", "ночь 31→1", "ночь 8→9"}) {
            checkArrowPixels(CaptionRenderer.render(c, caption, 0xffffffff));
        }
        android.graphics.Bitmap preview = android.graphics.Bitmap.createBitmap(264, 44, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(preview);
        canvas.drawColor(0xff1c1c1c);
        canvas.drawBitmap(CaptionRenderer.render(c, "ночь 17→18", 0xffffffff), 0, 0, null);
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(c.getExternalFilesDir(null), "arrow-alignment.png"))) {
            preview.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    private static void checkArrowPixels(android.graphics.Bitmap bitmap) {
        int arrowY = -1, arrowX = -1;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            int run = 0;
            for (int x = 0; x < bitmap.getWidth(); x++) {
                run = android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 80 ? run + 1 : 0;
                if (run >= 17) {
                    arrowY = y;
                    arrowX = x - run + 1;
                    break;
                }
            }
            if (arrowY >= 0) break;
        }
        require(arrowY >= 0, "horizontal arrow shaft rendered");
        int top = 44, bottom = -1;
        for (int y = 0; y < 44; y++)
            for (int x = 0; x < arrowX - 4; x++) {
                if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 80) {
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
        require(Math.abs((top + bottom) / 2f - arrowY) <= 1,
                "raster arrow shaft centered on text ink within one raster pixel");
        require(top > 2 && bottom < 41, "caption has vertical headroom");
    }

    private static View render(Context c, long now, boolean light) {
        View view = WidgetRenderer.views(c, now, light).apply(c, new FrameLayout(c));
        view.measure(View.MeasureSpec.makeMeasureSpec(832, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 832, 600);
        return view;
    }

    private static void equal(View view, int id, String value) {
        View target = view.findViewById(id);
        String actual = (target instanceof TextView ? ((TextView) target).getText() : target.getContentDescription()).toString();
        require(actual.equals(value), value + " != " + actual);
    }

    private static void geometry(View view) {
        View first = view.findViewById(R.id.weather_first_card), second = view.findViewById(R.id.weather_second_card);
        require(first.getWidth() == second.getWidth() && first.getHeight() == second.getHeight(), "two equal cards");
        require(second.getLeft() - first.getRight() == 24, "card spacing");
        View group = view.findViewById(R.id.weather_forecasts), weekday = view.findViewById(R.id.appwidget_week);
        require(group.getLeft() >= weekday.getRight() + 24, "no weekday collision");
        for (int id : new int[]{R.id.weather_first_temperature, R.id.weather_second_temperature}) {
            TextView text = view.findViewById(id);
            require(text.getPaint().measureText(text.getText().toString()) <= text.getWidth(), "text fits card " + text.getText());
            require(text.getHeight() >= text.getLineHeight(), "no clipped caption");
        }
        for (int id : new int[]{R.id.weather_first_caption, R.id.weather_second_caption}) {
            android.widget.ImageView image = view.findViewById(id);
            android.graphics.Bitmap bitmap = ((android.graphics.drawable.BitmapDrawable) image.getDrawable()).getBitmap();
            require(bitmap.getWidth() == image.getWidth() && bitmap.getHeight() == image.getHeight(), "caption displayed without scaling");
        }
        require(((View) view.findViewById(R.id.weather_attribution).getParent()).getBottom() <= 600, "footer inside widget");
    }

    private static String asset(Instrumentation test, String name) throws Exception {
        try (java.io.InputStream in = test.getContext().getAssets().open(name)) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
