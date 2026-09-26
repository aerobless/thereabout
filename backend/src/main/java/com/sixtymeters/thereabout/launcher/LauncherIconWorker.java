package com.sixtymeters.thereabout.launcher;

import com.sixtymeters.thereabout.shared.icons.WebsiteIconFetcher;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name="thereabout.launcher.fetch-icons",havingValue="true",matchIfMissing=true)
public class LauncherIconWorker {
    private final LauncherStore store;
    private final WebsiteIconFetcher fetcher;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon(true).name("launcher-icons").factory());

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        worker.scheduleWithFixedDelay(this::capturePending,5,2,TimeUnit.SECONDS);
    }
    private void capturePending() {
        try {
            for(var pending:store.pendingIcons()) store.finishIcon(pending,fetcher.fetch(pending.url()));
        } catch(RuntimeException ignored) {
            log.warn("Unable to update the launcher icon cache; will retry.");
        }
    }
    @PreDestroy public void stop() { worker.shutdownNow(); }
}
