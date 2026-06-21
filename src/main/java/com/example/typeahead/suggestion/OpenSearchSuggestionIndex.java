package com.example.typeahead.suggestion;

import com.example.typeahead.model.Suggestion;
import com.example.typeahead.ranking.RankMode;
import com.example.typeahead.ranking.RankingService;
import com.example.typeahead.store.QueryRecord;
import com.example.typeahead.trending.TrendBucketService;
import com.example.typeahead.trending.TrendWindow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "typeahead.search-index", havingValue = "opensearch")
public class OpenSearchSuggestionIndex implements SuggestionIndex {
    private static final int BULK_CHUNK_SIZE = 1_000;

    private final OpenSearchProperties properties;
    private final ObjectMapper objectMapper;
    private final TrendBucketService trendBucketService;
    private final RankingService rankingService;
    private final AtomicBoolean indexReady = new AtomicBoolean(false);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public OpenSearchSuggestionIndex(
            OpenSearchProperties properties,
            ObjectMapper objectMapper,
            TrendBucketService trendBucketService,
            RankingService rankingService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.trendBucketService = trendBucketService;
        this.rankingService = rankingService;
    }

    @Override
    public List<Suggestion> search(String normalizedPrefix, RankMode rankMode, int limit, boolean includeDebug) {
        String field = rankMode == RankMode.COUNT ? "suggest_count" : "suggest_hybrid";
        Map<String, Object> body = Map.of(
                "suggest", Map.of(
                        "query-suggest", Map.of(
                                "prefix", normalizedPrefix,
                                "completion", Map.of(
                                        "field", field,
                                        "size", limit,
                                        "skip_duplicates", true))));
        try {
            HttpRequest request = HttpRequest.newBuilder(searchUri())
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("OpenSearch suggest failed with status " + response.statusCode());
            }
            return parseSuggestions(response.body(), includeDebug);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to call OpenSearch", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenSearch request interrupted", ex);
        }
    }

    @Override
    public void refresh(Collection<QueryRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        ensureIndex();
        List<QueryRecord> batch = new ArrayList<>(BULK_CHUNK_SIZE);
        for (QueryRecord record : records) {
            batch.add(record);
            if (batch.size() == BULK_CHUNK_SIZE) {
                bulkIndex(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            bulkIndex(batch);
        }
        refreshIndex();
    }

    private void bulkIndex(Collection<QueryRecord> records) {
        StringBuilder bulk = new StringBuilder();
        String now = Instant.now().toString();
        for (QueryRecord record : records) {
            long recent1h = trendBucketService.recentCount(record.normalizedQuery(), TrendWindow.ONE_HOUR);
            long recent24h = trendBucketService.recentCount(record.normalizedQuery(), TrendWindow.TWENTY_FOUR_HOURS);
            long hybridScore = rankingService.score(RankMode.HYBRID, record.historicalCount(), recent1h, recent24h);
            try {
                bulk.append(objectMapper.writeValueAsString(Map.of(
                        "index", Map.of("_index", properties.index(), "_id", record.normalizedQuery())))).append('\n');
                bulk.append(objectMapper.writeValueAsString(Map.of(
                        "normalized_query", record.normalizedQuery(),
                        "display_query", record.displayQuery(),
                        "suggest_count", Map.of("input", record.displayQuery(), "weight", Math.max(1, record.historicalCount())),
                        "suggest_hybrid", Map.of("input", record.displayQuery(), "weight", Math.max(1, hybridScore)),
                        "historical_count", record.historicalCount(),
                        "recent_count_1h", recent1h,
                        "recent_count_24h", recent24h,
                        "hybrid_score", hybridScore,
                        "updated_at", now))).append('\n');
            } catch (IOException ex) {
                throw new IllegalStateException("failed to serialize OpenSearch bulk request", ex);
            }
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(bulkUri())
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/x-ndjson")
                    .POST(HttpRequest.BodyPublishers.ofString(bulk.toString()))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("OpenSearch bulk refresh failed with status " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (root.path("errors").asBoolean(false)) {
                throw new IllegalStateException("OpenSearch bulk refresh returned item errors: "
                        + response.body().substring(0, Math.min(response.body().length(), 1_000)));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("failed to update OpenSearch", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenSearch bulk request interrupted", ex);
        }
    }

    private void ensureIndex() {
        if (indexReady.get()) {
            return;
        }
        synchronized (indexReady) {
            if (indexReady.get()) {
                return;
            }
            try {
                HttpRequest head = HttpRequest.newBuilder(indexUri())
                        .timeout(Duration.ofSeconds(5))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .build();
                HttpResponse<Void> headResponse = httpClient.send(head, HttpResponse.BodyHandlers.discarding());
                if (headResponse.statusCode() == 404) {
                    HttpRequest create = HttpRequest.newBuilder(indexUri())
                            .timeout(Duration.ofSeconds(20))
                            .header("Content-Type", "application/json")
                            .PUT(HttpRequest.BodyPublishers.ofString(loadMapping()))
                            .build();
                    HttpResponse<String> createResponse = httpClient.send(create, HttpResponse.BodyHandlers.ofString());
                    if (createResponse.statusCode() >= 300) {
                        throw new IllegalStateException("OpenSearch index creation failed with status "
                                + createResponse.statusCode() + ": " + createResponse.body());
                    }
                } else if (headResponse.statusCode() >= 300) {
                    throw new IllegalStateException("OpenSearch index check failed with status " + headResponse.statusCode());
                }
                indexReady.set(true);
            } catch (IOException ex) {
                throw new IllegalStateException("failed to prepare OpenSearch index", ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("OpenSearch index preparation interrupted", ex);
            }
        }
    }

    private String loadMapping() throws IOException {
        ClassPathResource resource = new ClassPathResource("opensearch/query_suggestions_mapping.json");
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void refreshIndex() {
        try {
            HttpRequest request = HttpRequest.newBuilder(refreshUri())
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("OpenSearch index refresh failed with status " + response.statusCode());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("failed to refresh OpenSearch index", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenSearch index refresh interrupted", ex);
        }
    }

    private List<Suggestion> parseSuggestions(String json, boolean includeDebug) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode options = root.path("suggest").path("query-suggest").path(0).path("options");
        List<Suggestion> suggestions = new ArrayList<>();
        for (JsonNode option : options) {
            JsonNode source = option.path("_source");
            long historical = source.path("historical_count").asLong();
            long recent1h = source.path("recent_count_1h").asLong();
            long recent24h = source.path("recent_count_24h").asLong();
            long score = source.path("hybrid_score").asLong(option.path("_score").asLong());
            Map<String, Long> breakdown = includeDebug
                    ? rankingService.scoreBreakdown(historical, recent1h, recent24h)
                    : Map.of();
            suggestions.add(new Suggestion(
                    source.path("display_query").asText(option.path("text").asText()),
                    historical,
                    recent1h,
                    recent24h,
                    score,
                    breakdown));
        }
        return suggestions;
    }

    private URI searchUri() {
        return URI.create("%s/%s/_search".formatted(properties.baseUrl(), properties.index()));
    }

    private URI indexUri() {
        return URI.create("%s/%s".formatted(properties.baseUrl(), properties.index()));
    }

    private URI refreshUri() {
        return URI.create("%s/%s/_refresh".formatted(properties.baseUrl(), properties.index()));
    }

    private URI bulkUri() {
        return URI.create("%s/_bulk".formatted(properties.baseUrl()));
    }
}
