package me.elizarov.weather.hmc.forecast;

import me.elizarov.weather.HourClock;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * RSS night minimum belongs to its END date, not to the preceding daytime.
 */
final class HmcForecastWindow {
    final HmcPeriod first, second;
    final LocalDate day, nightEnd;

    static final class HmcPeriod {
        final LocalDate date;
        final boolean night;
        final String caption;

        HmcPeriod(LocalDate date, boolean night, LocalDate today) {
            this.date = date;
            this.night = night;
            caption = night ? "ночь " + date.minusDays(1).getDayOfMonth() + "→" + date.getDayOfMonth()
                    : "день" + (date.equals(today) ? "" : " " + date.getDayOfMonth());
        }
    }

    private HmcForecastWindow(HmcPeriod first, HmcPeriod second) {
        this.first = first;
        this.second = second;
        day = first.night ? second.date : first.date;
        nightEnd = first.night ? first.date : second.date;
    }

    static HmcForecastWindow at(long now) {
        ZonedDateTime time = HourClock.at(now);
        LocalDate today = time.toLocalDate();
        if (time.getHour() < 8)
            return new HmcForecastWindow(new HmcPeriod(today, true, today), new HmcPeriod(today, false, today));
        if (time.getHour() < 20)
            return new HmcForecastWindow(new HmcPeriod(today, false, today), new HmcPeriod(today.plusDays(1), true, today));
        return new HmcForecastWindow(new HmcPeriod(today.plusDays(1), true, today), new HmcPeriod(today.plusDays(1), false, today));
    }

    String nightRange() {
        return nightEnd.minusDays(1).getDayOfMonth() + "→" + nightEnd.getDayOfMonth();
    }

    String description() {
        return first.caption + ", " + second.caption;
    }
}
