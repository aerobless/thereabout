package com.sixtymeters.thereabout.communication.telegram;

import com.sixtymeters.thereabout.communication.data.TelegramConnectionEntity;
import com.sixtymeters.thereabout.communication.data.TelegramConnectionRepository;
import com.sixtymeters.thereabout.config.ThereaboutException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Application service for Telegram connection state and auth. Does not store the Telegram password.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramConnectionService {

    private final TelegramConnectionRepository connectionRepository;
    private final TelegramTdlibService tdlibService;
    private final TelegramProperties properties;

    @Transactional(readOnly = true)
    public Optional<TelegramConnectionEntity> getConnection() {
        return connectionRepository.findFirstByOrderByIdAsc();
    }

    /** Status for frontend: DISCONNECTED, WAIT_PHONE, WAIT_CODE, WAIT_PASSWORD, READY, SYNCING, ERROR. */
    @Transactional(readOnly = true)
    public String getStatus() {
        return connectionRepository.findFirstByOrderByIdAsc()
                .map(TelegramConnectionEntity::getAuthStatus)
                .orElse("DISCONNECTED");
    }

    /** Start connection with phone number. Code/password requested later via API; password never stored. */
    @Transactional
    public void connect(String phoneNumber) {
        if (!properties.isConfigured()) {
            throw new ThereaboutException(HttpStatusCode.valueOf(400),
                    "Telegram api_id and api_hash must be set in configuration");
        }
        connectionRepository.findFirstByOrderByIdAsc().ifPresent(conn -> {
            conn.setAuthStatus("CONNECTING");
            conn.setPhoneNumber(phoneNumber);
            conn.setEnabled(false);
            connectionRepository.save(conn);
        });
        tdlibService.connect(phoneNumber);
    }

    /** Submit login code (from user). Not stored. */
    public void submitCode(String code) {
        tdlibService.provideCode(code);
    }

    /** Submit 2FA password (from user). Used only for this step and never stored. */
    public void submitPassword(String password) {
        tdlibService.providePassword(password);
    }

    @Transactional
    public void disconnect() {
        tdlibService.disconnect();
    }

    /** Trigger a resync of recent messages (no re-auth). Runs asynchronously. */
    public void triggerResync() {
        if ("READY".equals(getStatus())) {
            tdlibService.triggerResyncAsync();
        }
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /** If DB says we were connected (READY) and have a phone number, resume the Telegram client so syncing continues after restart. */
    @Transactional(readOnly = true)
    public void resumeConnectionIfReady() {
        if (!properties.isConfigured()) return;
        connectionRepository.findFirstByOrderByIdAsc().ifPresent(conn -> {
            if ("READY".equals(conn.getAuthStatus()) && conn.getPhoneNumber() != null && !conn.getPhoneNumber().isBlank()) {
                log.info("Resuming Telegram connection for phone {} after restart", conn.getPhoneNumber());
                tdlibService.resume(conn.getPhoneNumber());
            }
        });
    }
}
