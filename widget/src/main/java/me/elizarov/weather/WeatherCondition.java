package me.elizarov.weather;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public enum WeatherCondition {
    // Hydrometcentre descriptions may name several conditions. The first match wins.
    THUNDERSTORM("Гроза", "ovc_ts_ra",
            List.of("THUNDERSTORM", "THUNDERSTORM_WITH_RAIN", "THUNDERSTORM_WITH_HAIL"), "гроз"),
    SLEET("Дождь со снегом", "ovc_ra_sn", "ovc_ra_sn", List.of("SLEET"), List.of(List.of("снег", "дожд"))),
    HAIL("Град", "ovc_h", List.of("HAIL"), "град"),
    LIGHT_SNOW("Небольшой снег", "ovc_-sn", List.of("LIGHT_SNOW")),
    SNOWFALL("Снегопад", "ovc_+sn", List.of("SNOWFALL")),
    SNOW("Снег", "ovc_sn", List.of("SNOW"), "снег", "метел"),
    LIGHT_RAIN("Небольшой дождь", "ovc_-ra", List.of("LIGHT_RAIN")),
    HEAVY_RAIN("Ливень", "ovc_+ra", List.of("HEAVY_RAIN", "SHOWERS"), "ливен", "ливн"),
    RAIN("Дождь", "ovc_ra", List.of("RAIN"), "дожд", "морось"),
    PRECIPITATION("Осадки", "ovc_ra", List.of(), "осадки"),
    FOG("Туман", "fg_d", "fg_n", List.of(), List.of(List.of("туман"), List.of("дымк"))),
    PARTLY_CLOUDY("Малооблачно", "bkn_d", "bkn_n", List.of("PARTLY_CLOUDY"), List.of(List.of("малооблачно"))),
    CLOUDY("Облачно с прояснениями", "bkn_d", "bkn_n", List.of("CLOUDY"),
            List.of(List.of("переменная"), List.of("прояснен"))),
    CLEAR("Ясно", "skc_d", "skc_n", List.of("CLEAR"), List.of(List.of("ясно"))),
    OVERCAST("Пасмурно", "ovc", List.of("OVERCAST"), "облач", "пасмурно"),
    UNKNOWN("", "", List.of());

    public final String description;
    private final String dayIcon;
    private final String nightIcon;
    private final List<String> yandexCodes;
    private final List<List<String>> hmcSubstrings;

    private static final WeatherCondition[] CONDITIONS = values();
    private static final Map<String, WeatherCondition> BY_YANDEX_CODE = new HashMap<>();

    static {
        for (WeatherCondition condition : CONDITIONS) {
            for (String code : condition.yandexCodes) BY_YANDEX_CODE.put(code, condition);
        }
    }

    WeatherCondition(String description, String icon, List<String> yandexCodes, String... hmcSubstrings) {
        this(description, icon, icon, yandexCodes, Arrays.stream(hmcSubstrings).map(List::of).collect(Collectors.toList()));
    }

    WeatherCondition(String description, String dayIcon, String nightIcon, List<String> yandexCodes,
                     List<List<String>> hmcSubstrings) {
        this.description = description;
        this.dayIcon = dayIcon;
        this.nightIcon = nightIcon;
        this.yandexCodes = yandexCodes;
        this.hmcSubstrings = hmcSubstrings;
    }

    public static WeatherCondition fromYandex(String code) {
        return BY_YANDEX_CODE.getOrDefault(code, UNKNOWN);
    }

    public static WeatherCondition fromWmo(int code) {
        return switch (code) {
            case 0 -> CLEAR;
            case 1 -> PARTLY_CLOUDY;
            case 2 -> CLOUDY;
            case 3 -> OVERCAST;
            case 45, 48 -> FOG;
            case 51, 53, 55, 56, 57, 61, 80 -> LIGHT_RAIN;
            case 63, 66, 67, 81 -> RAIN;
            case 65, 82 -> HEAVY_RAIN;
            case 71, 77, 85 -> LIGHT_SNOW;
            case 73 -> SNOW;
            case 75, 86 -> SNOWFALL;
            case 95, 96, 99 -> THUNDERSTORM;
            default -> UNKNOWN;
        };
    }

    public static WeatherCondition fromHmc(String description) {
        String normalized = description.toLowerCase(Locale.ROOT).replace('ё', 'е');
        for (WeatherCondition condition : CONDITIONS) {
            for (List<String> required : condition.hmcSubstrings) {
                if (required.stream().allMatch(normalized::contains)) return condition;
            }
        }
        return UNKNOWN;
    }

    public String icon(boolean night) {
        return night ? nightIcon : dayIcon;
    }
}
