package com.sixtymeters.thereabout.communication.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * After the application is ready, resume the Telegram client if the DB shows we were previously connected,
 * so syncing continues across restarts.
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class TelegramConnectionStartupRunner implements ApplicationRunner {

    private final TelegramConnectionService telegramConnectionService;

    @Override
    public void run(ApplicationArguments args) {
        telegramConnectionService.resumeConnectionIfReady();
    }
}
