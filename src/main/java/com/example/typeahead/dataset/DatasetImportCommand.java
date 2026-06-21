package com.example.typeahead.dataset;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DatasetImportCommand implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DatasetImportCommand.class);

    private final DatasetImportProperties properties;
    private final DatasetImportService importService;

    public DatasetImportCommand(DatasetImportProperties properties, DatasetImportService importService) {
        this.properties = properties;
        this.importService = importService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (properties.importPath() == null || properties.importPath().isBlank()) {
            return;
        }
        DatasetImportResult result = importService.importFile(Path.of(properties.importPath()), properties.importLimit());
        log.info(
                "Imported dataset {} from {}: rowsRead={}, uniqueQueriesLoaded={}",
                result.datasetName(),
                result.source(),
                result.rowsRead(),
                result.uniqueQueriesLoaded());
    }
}
