package com.example.typeahead.trending;

import java.time.Duration;
import java.util.Locale;

public enum TrendWindow {
    ONE_HOUR("1h", Duration.ofHours(1)),
    TWENTY_FOUR_HOURS("24h", Duration.ofHours(24)),
    SEVEN_DAYS("7d", Duration.ofDays(7));

    private final String wireName;
    private final Duration duration;

    TrendWindow(String wireName, Duration duration) {
        this.wireName = wireName;
        this.duration = duration;
    }

    public static TrendWindow from(String value) {
        if (value == null || value.isBlank()) {
            return ONE_HOUR;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (TrendWindow window : values()) {
            if (window.wireName.equals(normalized)) {
                return window;
            }
        }
        return ONE_HOUR;
    }

    public String wireName() {
        return wireName;
    }

    public Duration duration() {
        return duration;
    }
}
