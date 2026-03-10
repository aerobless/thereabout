package com.sixtymeters.thereabout.communication.telegram;

import it.tdlight.client.ClientInteraction;
import it.tdlight.client.InputParameter;
import it.tdlight.client.ParameterInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Supplies phone/code/password to TDLib when requested. Code and password are provided via
 * {@link #provideCode(String)} and {@link #providePassword(String)} from the REST API; never stored.
 */
@Slf4j
public class ThereaboutClientInteraction implements ClientInteraction {

    private final AtomicReference<CompletableFuture<String>> pendingCode = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<String>> pendingPassword = new AtomicReference<>();

    @Override
    public CompletableFuture<String> onParameterRequest(InputParameter parameter, ParameterInfo parameterInfo) {
        if (parameter == InputParameter.ASK_CODE) {
            CompletableFuture<String> future = new CompletableFuture<>();
            pendingCode.set(future);
            log.debug("Telegram auth waiting for code (will be provided via API, never stored)");
            return future;
        }
        if (parameter == InputParameter.ASK_PASSWORD) {
            CompletableFuture<String> future = new CompletableFuture<>();
            pendingPassword.set(future);
            log.debug("Telegram auth waiting for 2FA password (will be provided via API, never stored)");
            return future;
        }
        log.warn("Unhandled Telegram auth parameter: {}", parameter);
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Unsupported auth parameter: " + parameter));
    }

    /** Called from REST when user submits the login code. Password is not stored. */
    public void provideCode(String code) {
        CompletableFuture<String> f = pendingCode.getAndSet(null);
        if (f != null) {
            f.complete(code);
        }
    }

    /** Called from REST when user submits 2FA password. Password is used only for this step and never stored. */
    public void providePassword(String password) {
        CompletableFuture<String> f = pendingPassword.getAndSet(null);
        if (f != null) {
            f.complete(password);
        }
    }

    public boolean isWaitingForCode() {
        return pendingCode.get() != null;
    }

    public boolean isWaitingForPassword() {
        return pendingPassword.get() != null;
    }
}
