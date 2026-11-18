package me.elizarov.weather;

/**
 * The renderer knows only these two identically shaped cards.
 */
public final class WeatherCards {
    final Card first, second;
    final String city;

    public static final class Card {
        final Integer temperature;
        final String caption, icon, description;

        public Card(Integer temperature, String caption, String icon, String description) {
            this.temperature = temperature;
            this.caption = caption;
            this.icon = icon;
            this.description = description;
        }

        String temperatureText() {
            return temperature == null ? "—" : (temperature > 0 ? "+" : "") + temperature + "°";
        }
    }

    public WeatherCards(Card first, Card second, String city) {
        this.first = first;
        this.second = second;
        this.city = city;
    }
}
