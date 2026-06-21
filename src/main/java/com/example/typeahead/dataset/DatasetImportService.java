package com.example.typeahead.dataset;

import com.example.typeahead.normalize.QueryNormalizer;
import com.example.typeahead.store.QueryRecord;
import com.example.typeahead.store.QueryStore;
import com.example.typeahead.suggestion.SuggestionIndex;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DatasetImportService {
    public static final String AMAZON_QAC_URL = "https://huggingface.co/datasets/amazon/AmazonQAC";

    private final QueryNormalizer normalizer;
    private final QueryStore queryStore;
    private final SuggestionIndex suggestionIndex;
    private final ObjectMapper objectMapper;

    public DatasetImportService(
            QueryNormalizer normalizer,
            QueryStore queryStore,
            SuggestionIndex suggestionIndex,
            ObjectMapper objectMapper) {
        this.normalizer = normalizer;
        this.queryStore = queryStore;
        this.suggestionIndex = suggestionIndex;
        this.objectMapper = objectMapper;
    }

    public DatasetImportResult importFile(Path path, int limit) throws IOException {
        Instant started = Instant.now();
        ImportCounters counters = path.toString().toLowerCase(Locale.ROOT).endsWith(".jsonl")
                ? importJsonl(path, limit)
                : importCsv(path, limit);
        return new DatasetImportResult(
                "AmazonQAC",
                AMAZON_QAC_URL,
                counters.rowsRead(),
                counters.uniqueQueriesLoaded(),
                started,
                Instant.now());
    }

    private ImportCounters importJsonl(Path path, int limit) throws IOException {
        long rowsRead = 0;
        Set<String> seen = new HashSet<>();
        List<QueryRecord> indexed = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null && seen.size() < limit) {
                rowsRead++;
                Map<String, Object> row = objectMapper.readValue(line, new TypeReference<>() {});
                QueryRecord record = importRow(
                        stringValue(row.get("final_search_term")),
                        longValue(row.get("popularity")),
                        stringValue(row.get("search_time")),
                        seen);
                if (record != null) {
                    indexed.add(record);
                }
            }
        }
        suggestionIndex.refresh(indexed);
        return new ImportCounters(rowsRead, seen.size());
    }

    private ImportCounters importCsv(Path path, int limit) throws IOException {
        long rowsRead = 0;
        Set<String> seen = new HashSet<>();
        List<QueryRecord> indexed = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return new ImportCounters(0, 0);
            }
            List<String> headers = parseCsvLine(headerLine);
            String line;
            while ((line = reader.readLine()) != null && seen.size() < limit) {
                rowsRead++;
                List<String> values = parseCsvLine(line);
                Map<String, String> row = toRow(headers, values);
                QueryRecord record = importRow(
                        row.get("final_search_term"),
                        longValue(row.get("popularity")),
                        row.get("search_time"),
                        seen);
                if (record != null) {
                    indexed.add(record);
                }
            }
        }
        suggestionIndex.refresh(indexed);
        return new ImportCounters(rowsRead, seen.size());
    }

    private QueryRecord importRow(String query, long popularity, String searchTime, Set<String> seen) {
        String normalized = normalizer.normalize(query);
        if (normalized.isBlank() || !seen.add(normalized)) {
            return null;
        }
        long count = Math.max(1, popularity);
        return queryStore.upsertImported(normalized, normalized, count, parseInstant(searchTime));
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static long longValue(Object value) {
        if (value == null) {
            return 1;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return longValue(String.valueOf(value));
    }

    private static long longValue(String value) {
        if (value == null || value.isBlank()) {
            return 1;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static Map<String, String> toRow(List<String> headers, List<String> values) {
        Map<String, String> row = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String value = i < values.size() ? values.get(i) : "";
            row.put(headers.get(i), value);
        }
        return row;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private record ImportCounters(long rowsRead, long uniqueQueriesLoaded) {
    }
}
