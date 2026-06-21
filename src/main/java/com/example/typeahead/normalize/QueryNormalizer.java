package com.example.typeahead.normalize;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class QueryNormalizer {
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public String normalize(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC);
        normalized = CONTROL.matcher(normalized).replaceAll(" ");
        normalized = WHITESPACE.matcher(normalized.trim()).replaceAll(" ");
        return normalized.toLowerCase(Locale.ROOT);
    }

    public boolean isValidSearchQuery(String query) {
        String normalized = normalize(query);
        return !normalized.isBlank() && normalized.length() <= 200;
    }
}
