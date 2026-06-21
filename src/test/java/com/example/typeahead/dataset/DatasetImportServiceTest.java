package com.example.typeahead.dataset;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.typeahead.normalize.QueryNormalizer;
import com.example.typeahead.ranking.RankMode;
import com.example.typeahead.store.InMemoryQueryStore;
import com.example.typeahead.store.QueryRecord;
import com.example.typeahead.suggestion.SuggestionIndex;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatasetImportServiceTest {
    @TempDir
    private Path tempDir;

    @Test
    void importsAssignmentQueryCountCsvFormat() throws Exception {
        Path csv = tempDir.resolve("queries.csv");
        Files.writeString(csv, """
                query,count
                Laptop,23222
                iphone 15,45222
                """);
        InMemoryQueryStore store = new InMemoryQueryStore();
        CapturingSuggestionIndex suggestionIndex = new CapturingSuggestionIndex();
        DatasetImportService service = new DatasetImportService(
                new QueryNormalizer(),
                store,
                suggestionIndex,
                new ObjectMapper());

        DatasetImportResult result = service.importFile(csv, 100_000);

        assertThat(result.uniqueQueriesLoaded()).isEqualTo(2);
        assertThat(store.find("laptop")).get().extracting(QueryRecord::historicalCount).isEqualTo(23_222L);
        assertThat(store.find("iphone 15")).get().extracting(QueryRecord::historicalCount).isEqualTo(45_222L);
        assertThat(suggestionIndex.refreshed).hasSize(2);
    }

    private static final class CapturingSuggestionIndex implements SuggestionIndex {
        private final List<QueryRecord> refreshed = new ArrayList<>();

        @Override
        public List<com.example.typeahead.model.Suggestion> search(
                String normalizedPrefix,
                RankMode rankMode,
                int limit,
                boolean includeDebug) {
            return List.of();
        }

        @Override
        public void refresh(Collection<QueryRecord> records) {
            refreshed.addAll(records);
        }
    }
}
