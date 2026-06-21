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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "typeahead.search-index", havingValue = "opensearch")
public class OpenSearchSuggestionIndex implements SuggestionIndex {
    private final OpenSearchProperties properties;
    private final ObjectMapper objectMapper;
    private final TrendBucketService trendBucketService;
    private final RankingService rankingService;
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
        StringBuilder bulk = new StringBuilder();
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
                        "hybrid_score", hybridScore))).append('\n');
            } catch (IOException ex) {
                throw new IllegalStateException("failed to serialize OpenSearch bulk request", ex);
            }
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(bulkUri())
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-ndjson")
                    .POST(HttpRequest.BodyPublishers.ofString(bulk.toString()))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("OpenSearch bulk refresh failed with status " + response.statusCode());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("failed to update OpenSearch", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenSearch bulk request interrupted", ex);
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

    private URI bulkUri() {
        return URI.create("%s/_bulk".formatted(properties.baseUrl()));
    }
}
