package me.elizarov.weather;

import android.text.Html;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

/**
 * Separate day/night conditions from the official compact forecast table.
 */
final class WeatherConditions {
    private static final String[] MONTHS = {"янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"};

    private WeatherConditions() {
    }

    static JSONObject parse(String page, List<WeatherForecast> forecasts) throws Exception {
        JSONObject result = new JSONObject();
        LocalDate date = null;
        Matcher tokens = Pattern.compile("<nobr>\\s*(\\d{1,2})\\s+([^<\\s]+)\\s*</nobr>|<img\\b[^>]*class=[\"']fc_short_img[\"'][^>]*>", Pattern.CASE_INSENSITIVE).matcher(page);
        while (tokens.find()) {
            if (tokens.group(1) != null) {
                date = null;
                for (WeatherForecast f : forecasts) {
                    if (f.date.getDayOfMonth() == Integer.parseInt(tokens.group(1)) && tokens.group(2).startsWith(MONTHS[f.date.getMonthValue() - 1])) {
                        date = f.date;
                        break;
                    }
                }
            } else if (date != null) {
                String image = tokens.group();
                Matcher period = Pattern.compile("/images/ico/[^\"']*?([dn])_s\\.png").matcher(image);
                Matcher title = Pattern.compile("\\btitle=[\"']([^\"']+)[\"']").matcher(image);
                if (period.find() && title.find()) {
                    String key = date + period.group(1);
                    if (result.has(key))
                        break; // Ignore the repeated horizontal version of the table.
                    result.put(key, Html.fromHtml(title.group(1), Html.FROM_HTML_MODE_LEGACY).toString());
                }
            }
        }
        if (result.length() == 0)
            throw new IllegalArgumentException("Missing day/night forecast conditions");
        return result;
    }

    static String pathFromRss(String xml) {
        Matcher link = Pattern.compile("forecasts(?:5000)?/([^<#\\s]+)").matcher(xml);
        if (!link.find()) throw new IllegalArgumentException("Missing official forecast page");
        return link.group(1);
    }

    static String get(android.content.Context c, LocalDate date, boolean night) {
        try {
            return new JSONObject(WeatherRepository.prefs(c).getString("conditions", "{}")).optString(date + (night ? "n" : "d"));
        } catch (Exception error) {
            return "";
        }
    }

    static boolean complete(String json, long now) {
        try {
            JSONObject data = new JSONObject(json);
            ForecastWindow window = ForecastWindow.at(now);
            return !data.optString(window.day + "d").isEmpty() && !data.optString(window.nightEnd + "n").isEmpty();
        } catch (Exception error) {
            return false;
        }
    }

    static String merge(String saved, String downloaded, List<WeatherForecast> forecasts) throws Exception {
        JSONObject old = new JSONObject(saved), fresh = new JSONObject(downloaded), result = new JSONObject();
        for (WeatherForecast forecast : forecasts)
            for (String period : new String[]{"d", "n"}) {
                String key = forecast.date + period;
                String value = fresh.optString(key, old.optString(key));
                if (!value.isEmpty()) result.put(key, value);
            }
        return result.toString();
    }

    static void remember(android.content.SharedPreferences prefs) {
        String station = prefs.getString("station", ""), conditions = prefs.getString("conditions", "{}");
        if (station.matches("[0-9]{5}") && !conditions.equals("{}"))
            prefs.edit().putString("conditions_cache_" + station, conditions).commit();
    }

    static String cached(android.content.SharedPreferences prefs, String station) {
        String saved = prefs.getString("conditions_cache_" + station, "{}");
        if (station.equals(prefs.getString("station", "")) && !prefs.getString("conditions", "{}").equals("{}"))
            saved = prefs.getString("conditions", "{}");
        return saved;
    }
}
