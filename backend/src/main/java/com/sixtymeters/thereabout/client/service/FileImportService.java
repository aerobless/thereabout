package com.sixtymeters.thereabout.client.service;

import com.sixtymeters.thereabout.communication.service.importer.FileImporter;
import com.sixtymeters.thereabout.config.ThereaboutException;
import com.sixtymeters.thereabout.generated.model.GenImportType;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class FileImportService {
    private final List<FileImporter> importers;
    private final ImportProgressService progress;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("file-import").factory());

    public FileImportService(List<FileImporter> importers, ImportProgressService progress) {
        this.importers = List.copyOf(importers);
        this.progress = progress;
    }

    public void start(MultipartFile file, GenImportType type, Optional<String> receiver) {
        if (file.isEmpty()) throw error(400, "file is required");
        if (type == GenImportType.WHATSAPP_CHAT && receiver.filter(v -> !v.isBlank()).isEmpty()) {
            throw error(400, "receiver is required for WhatsApp imports");
        }
        var importer = importers.stream().filter(i -> i.getSupportedImportType() == type).findFirst()
                .orElseThrow(() -> error(400, "Unsupported import type"));
        if (!progress.begin()) throw error(409, "Another file import is in progress");
        Path directory = null;
        Path upload = null;
        try {
            directory = Files.createTempDirectory("thereabout-import-");
            // Never interpret an uploaded filename as a filesystem path.
            upload = directory.resolve("upload");
            file.transferTo(upload);
            final Path ownedDirectory = directory;
            final Path ownedFile = upload;
            executor.execute(() -> run(importer, ownedFile, ownedDirectory, receiver.orElse(null)));
        } catch (IOException | RuntimeException e) {
            cleanup(upload, directory);
            progress.fail();
            log.error("Could not start file import", e);
            throw error(500, "Could not start file import");
        }
    }

    private void run(FileImporter importer, Path file, Path directory, String receiver) {
        boolean succeeded = false;
        try {
            importer.importFile(file.toFile(), receiver);
            succeeded = true;
        } catch (Exception e) {
            log.error("File import failed; already committed records are retained", e);
        } finally {
            cleanup(file, directory);
            if (succeeded) progress.succeed(); else progress.fail();
        }
    }

    private void cleanup(Path file, Path directory) {
        try {
            if (file != null) Files.deleteIfExists(file);
            if (directory != null) Files.deleteIfExists(directory);
        } catch (IOException e) {
            log.warn("Could not remove temporary import files", e);
        }
    }

    private static ThereaboutException error(int status, String message) {
        return new ThereaboutException(HttpStatusCode.valueOf(status), message);
    }

    @PreDestroy public void close() { executor.close(); }
}
