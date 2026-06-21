package com.example.typeahead.dataset;

import com.example.typeahead.store.QueryRecord;
import com.example.typeahead.store.QueryStore;
import com.example.typeahead.suggestion.SuggestionIndex;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class SampleDataInitializer {
    private final QueryStore queryStore;
    private final SuggestionIndex suggestionIndex;

    public SampleDataInitializer(QueryStore queryStore, SuggestionIndex suggestionIndex) {
        this.queryStore = queryStore;
        this.suggestionIndex = suggestionIndex;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedIfEmpty() {
        if (queryStore.count() > 0) {
            return;
        }
        Instant now = Instant.now();
        List<QueryRecord> records = new ArrayList<>();
        records.add(queryStore.upsertImported("iphone", "iphone", 1_000_000, now));
        records.add(queryStore.upsertImported("iphone 15", "iphone 15", 850_000, now));
        records.add(queryStore.upsertImported("iphone 15 pro", "iphone 15 pro", 820_000, now));
        records.add(queryStore.upsertImported("iphone charger", "iphone charger", 600_000, now));
        records.add(queryStore.upsertImported("ipad", "ipad", 740_000, now));
        records.add(queryStore.upsertImported("airpods", "airpods", 700_000, now));
        records.add(queryStore.upsertImported("ai tools", "ai tools", 410_000, now));
        records.add(queryStore.upsertImported("ai image generator", "ai image generator", 370_000, now));
        records.add(queryStore.upsertImported("java tutorial", "java tutorial", 360_000, now));
        records.add(queryStore.upsertImported("java spring boot", "java spring boot", 290_000, now));
        records.add(queryStore.upsertImported("python tutorial", "python tutorial", 350_000, now));
        records.add(queryStore.upsertImported("python fastapi", "python fastapi", 180_000, now));
        records.add(queryStore.upsertImported("cricket score", "cricket score", 500_000, now));
        records.add(queryStore.upsertImported("zomato offers", "zomato offers", 270_000, now));
        records.add(queryStore.upsertImported("swiggy coupon", "swiggy coupon", 260_000, now));
        records.add(queryStore.upsertImported("laptop under 50000", "laptop under 50000", 240_000, now));
        records.add(queryStore.upsertImported("low latency search", "low latency search", 20_000, now));
        records.add(queryStore.upsertImported("low cost flights", "low cost flights", 190_000, now));
        suggestionIndex.refresh(records);
    }
}
