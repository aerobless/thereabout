package com.sixtymeters.thereabout.communication.service.importer;

import com.sixtymeters.thereabout.generated.model.GenImportType;

import java.io.File;

/**
 * Strategy interface for file importers. Each implementation handles a specific import type.
 */
public interface FileImporter {

    /**
     * Import data from the given file.
     *
     * @param file the file to import
     */
    void importFile(File file);

    /**
     * @return the import type this importer supports
     */
    GenImportType getSupportedImportType();
}
