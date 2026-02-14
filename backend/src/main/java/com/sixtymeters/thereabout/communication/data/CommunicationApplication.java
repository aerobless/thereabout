package com.sixtymeters.thereabout.communication.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enum representing supported communication applications.
 * The enum name (e.g. WHATSAPP) is stored in the database,
 * while the displayName (e.g. "WhatsApp") is used for frontend display.
 */
@Getter
@RequiredArgsConstructor
public enum CommunicationApplication {
    WHATSAPP("WhatsApp"),
    TELEGRAM("Telegram"),
    SIGNAL("Signal");

    private final String displayName;
}
