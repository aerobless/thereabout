package com.sixtymeters.thereabout.client.service;

import com.sixtymeters.thereabout.access.UserId;
import com.sixtymeters.thereabout.communication.service.importer.FileImporter;
import com.sixtymeters.thereabout.generated.model.GenImportType;
import com.sixtymeters.thereabout.config.ThereaboutException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileImportServiceTest {
    private final ImportProgressService progress = new ImportProgressService();
    private final FileImporter importer = mock(FileImporter.class);
    private final com.sixtymeters.thereabout.access.UserContext users = mock(com.sixtymeters.thereabout.access.UserContext.class);
    private final FileImportService service = new FileImportService(List.of(importer), progress, users);
    { when(users.integration()).thenReturn(new UserId(1)); }
    private final MockMultipartFile file = new MockMultipartFile("file", "../../outside.json", "application/json", "{}".getBytes());

    @Test void ownsFilesAndRejectsConcurrentImports() throws Exception {
        when(importer.getSupportedImportType()).thenReturn(GenImportType.HEALTH_AUTO_EXPORT_JSON);
        var started = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var path = new AtomicReference<Path>();
        doAnswer(call -> {
            path.set(call.<File>getArgument(0).toPath());
            started.countDown();
            assertThat(finish.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(importer).importFile(any(), isNull(), eq(new UserId(1)));
        try {
            service.start(file, GenImportType.HEALTH_AUTO_EXPORT_JSON, Optional.empty());
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(path.get().getFileName().toString()).isEqualTo("upload");
            assertThat(path.get()).exists();
            assertThatThrownBy(() -> service.start(file, GenImportType.HEALTH_AUTO_EXPORT_JSON, Optional.empty()))
                    .isInstanceOf(ThereaboutException.class).hasMessageContaining("in progress");
        } finally {
            finish.countDown();
            service.close();
        }
        assertThat(progress.snapshot().status()).isEqualTo(ImportProgressService.State.SUCCEEDED);
        assertThat(path.get()).doesNotExist();
        assertThat(path.get().getParent()).doesNotExist();
    }

    @Test void failureHasAnExplicitSafeResultAndCleansUp() {
        when(importer.getSupportedImportType()).thenReturn(GenImportType.HEALTH_AUTO_EXPORT_JSON);
        var path = new AtomicReference<Path>();
        doAnswer(call -> {
            path.set(call.<File>getArgument(0).toPath());
            progress.setProgress(50);
            throw new IllegalArgumentException("private source contents");
        }).when(importer).importFile(any(), isNull(), eq(new UserId(1)));
        service.start(file, GenImportType.HEALTH_AUTO_EXPORT_JSON, Optional.empty());
        service.close();
        assertThat(progress.snapshot().status()).isEqualTo(ImportProgressService.State.FAILED);
        assertThat(progress.snapshot().progress()).isEqualTo(50);
        assertThat(progress.snapshot().error()).contains("already").doesNotContain("private source contents");
        assertThat(path.get().getParent()).doesNotExist();
        assertThat(progress.begin()).isTrue();
        assertThat(progress.snapshot().error()).isNull();
    }

    @Test void invalidInputDoesNotClaimAnImport() {
        try {
            assertThatThrownBy(() -> service.start(file, GenImportType.WHATSAPP_CHAT, Optional.empty()))
                    .isInstanceOf(ThereaboutException.class).hasMessageContaining("receiver");
            assertThat(progress.snapshot().status()).isEqualTo(ImportProgressService.State.IDLE);
        } finally { service.close(); }
    }
}
