package me.elizarov.weather.hmc.forecast;

import me.elizarov.weather.WeatherCondition;

import android.util.Xml;

import java.io.StringReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xmlpull.v1.XmlPullParser;

/**
 * Public Hydrometcentre RSS. These are daily forecasts, not current observations.
 */
final class HmcForecast {
    private static final Pattern TITLE = Pattern.compile("^(.+), (\\d{1,2}) ([а-яё]+)$");
    private static final Pattern TEMPERATURE = Pattern.compile("Температура ночью\\s+([+−-]?\\d+)°.*,\\s*дн[её]м\\s+([+−-]?\\d+)°");
    private static final Pattern UPDATED = Pattern.compile("обновл[её]н (\\d{2}\\.\\d{2}\\.\\d{4}) в (\\d{2}:\\d{2})\\(UTC\\)");
    private static final String[] MONTHS = {"января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"};
    public final String city, description;
    public final LocalDate date;
    public final int night, day;
    public final long issuedAt;

    private HmcForecast(String title, String description, String source) {
        Matcher t = TITLE.matcher(title), temp = TEMPERATURE.matcher(description), issue = UPDATED.matcher(source);
        if (!t.find() || !temp.find() || !issue.find())
            throw new IllegalArgumentException("Unsupported HMC RSS item");
        city = t.group(1);
        this.description = description.substring(0, description.indexOf("Температура")).trim().replaceFirst("\\.$", "");
        night = Integer.parseInt(temp.group(1).replace('−', '-'));
        day = Integer.parseInt(temp.group(2).replace('−', '-'));
        if (day < -90 || day > 65 || night < -90 || night > 65)
            throw new IllegalArgumentException("Invalid temperature");
        LocalDateTime issued = LocalDateTime.parse(issue.group(1) + " " + issue.group(2), DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.ROOT));
        issuedAt = issued.toInstant(ZoneOffset.UTC).toEpochMilli();
        int month = -1;
        for (int i = 0; i < MONTHS.length; i++) if (MONTHS[i].equals(t.group(3))) month = i + 1;
        if (month == -1) throw new IllegalArgumentException("Unknown forecast month");
        int year = issued.getYear();
        if (month < issued.getMonthValue() - 6) year++;
        if (month > issued.getMonthValue() + 6) year--;
        date = LocalDate.of(year, month, Integer.parseInt(t.group(2)));
    }

    public static List<HmcForecast> parse(String xml) throws Exception {
        if (xml.length() > 131072 || xml.contains("<!DOCTYPE"))
            throw new IllegalArgumentException("Unexpected XML document");
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new StringReader(xml));
        List<HmcForecast> results = new ArrayList<>();
        boolean item = false;
        String title = "", description = "", source = "";
        for (int event = parser.next(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
            if (event == XmlPullParser.START_TAG) {
                String name = parser.getName();
                if (name.equals("item")) {
                    item = true;
                    title = "";
                    description = "";
                    source = "";
                } else if (item && name.equals("title")) title = parser.nextText();
                else if (item && name.equals("description")) description = parser.nextText();
                else if (item && name.equals("source")) source = parser.nextText();
            } else if (event == XmlPullParser.END_TAG && parser.getName().equals("item")) {
                results.add(new HmcForecast(title, description, source));
                item = false;
            }
        }
        if (results.isEmpty()) throw new IllegalArgumentException("Empty HMC RSS");
        return results;
    }

    public static HmcForecast forDate(List<HmcForecast> forecasts, LocalDate today) {
        for (HmcForecast f : forecasts) if (f.date.equals(today)) return f;
        return null; // Never present yesterday's forecast as today's weather.
    }

    public static HmcForecast forTime(List<HmcForecast> forecasts, LocalDate date, int hour) {
        // The RSS minimum for day D describes the night leading into D.
        // In the evening, show the coming night's minimum from D+1.
        return forDate(forecasts, hour >= 20 ? date.plusDays(1) : date);
    }

    public String shortCondition() {
        WeatherCondition condition = WeatherCondition.fromHmc(description);
        return condition == WeatherCondition.UNKNOWN ? description : condition.description;
    }

    public String summary(int localHour) {
        boolean daytime = localHour >= 8 && localHour < 20;
        int temp = temperature(localHour);
        return (daytime ? "днём " : "ночью ") + (temp > 0 ? "+" : "") + temp + "°C · " + shortCondition();
    }

    public int temperature(int localHour) {
        return localHour >= 8 && localHour < 20 ? day : night;
    }
}
