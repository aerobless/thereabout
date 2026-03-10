package com.sixtymeters.thereabout.communication.telegram;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Configuration for TDLib Telegram sync. api_id and api_hash from https://my.telegram.org.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "thereabout.telegram.tdlib")
public class TelegramProperties {

    /**
     * Application API ID from Telegram.
     */
    private int apiId;

    /**
     * Application API hash from Telegram.
     */
    private String apiHash = "";

    /**
     * Directory for TDLib database and session (writable by the app). Session/token stored here only; no password stored.
     */
    private Path databaseDirectory = Path.of("data/telegram-tdlib");

    /**
     * Max messages to fetch per chat in one getChatHistory call.
     */
    private int historyBatchSize = 100;

    /**
     * Whether Telegram sync is enabled (requires apiId and apiHash to be set).
     */
    public boolean isConfigured() {
        return apiId != 0 && apiHash != null && !apiHash.isBlank();
    }
}
