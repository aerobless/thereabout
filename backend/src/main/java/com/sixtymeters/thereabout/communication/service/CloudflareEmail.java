package com.sixtymeters.thereabout.communication.service;

import java.util.Locale;
import java.util.regex.Pattern;
import com.sixtymeters.thereabout.config.ThereaboutException;
import org.springframework.http.HttpStatus;

public final class CloudflareEmail {
    private CloudflareEmail() {}
    // Deliberately the same practical ASCII email rule as the Create User form.
    private static final Pattern EMAIL = Pattern.compile("^[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$");
    // ECMAScript trim whitespace, including non-breaking spaces copied from email clients.
    private static final Pattern EDGE_WHITESPACE = Pattern.compile("^[\\s\\p{Z}\\uFEFF]+|[\\s\\p{Z}\\uFEFF]+$");
    public static String normalize(String email) {
        String value = email == null ? "" : EDGE_WHITESPACE.matcher(email).replaceAll("").toLowerCase(Locale.ROOT);
        if (value.length() > 254 || !EMAIL.matcher(value).matches() || value.indexOf('@') > 64) {
            throw new ThereaboutException(HttpStatus.BAD_REQUEST, "Enter a valid Cloudflare email address.");
        }
        return value;
    }
}
