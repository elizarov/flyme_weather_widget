package me.elizarov.weather;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

final class ForecastWindow {
    final LocalDate day, nightEnd;

    private ForecastWindow(LocalDate day, LocalDate nightEnd) {
        this.day = day;
        this.nightEnd = nightEnd;
    }

    static ForecastWindow at(long now) {
        ZonedDateTime time = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault());
        LocalDate today = time.toLocalDate();
        return new ForecastWindow(today, time.getHour() < 6 ? today : today.plusDays(1));
    }

    String nightRange() {
        LocalDate start = nightEnd.minusDays(1);
        DateTimeFormatter format = DateTimeFormatter.ofPattern(start.getMonth() == nightEnd.getMonth() ? "d" : "dd.MM");
        return start.format(format) + "→" + nightEnd.format(format);
    }

    String description() {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        return "День: " + day.format(format) + " · ночь: " + nightEnd.minusDays(1).format(format) + " → " + nightEnd.format(format);
    }
}
