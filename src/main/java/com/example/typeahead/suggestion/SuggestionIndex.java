package com.example.typeahead.suggestion;

import com.example.typeahead.model.Suggestion;
import com.example.typeahead.ranking.RankMode;
import com.example.typeahead.store.QueryRecord;
import java.util.Collection;
import java.util.List;

public interface SuggestionIndex {
    List<Suggestion> search(String normalizedPrefix, RankMode rankMode, int limit, boolean includeDebug);

    void refresh(Collection<QueryRecord> records);
}
