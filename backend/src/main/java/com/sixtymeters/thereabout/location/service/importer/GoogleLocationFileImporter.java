package com.sixtymeters.thereabout.location.service.importer;

import com.sixtymeters.thereabout.communication.service.importer.FileImporter;
import com.sixtymeters.thereabout.generated.model.GenImportType;
import com.sixtymeters.thereabout.location.service.LocationHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.io.File;

@Service
@RequiredArgsConstructor
public class GoogleLocationFileImporter implements FileImporter {
    private final LocationHistoryService locations;
    @Override public GenImportType getSupportedImportType() { return GenImportType.GOOGLE_MAPS_RECORDS; }
    @Override public void importFile(File file, String receiver) { locations.importGoogleLocationHistory(file); }
}
