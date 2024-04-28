package com.sixtymeters.thereabout.support;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

public class ThereaboutException extends ResponseStatusException {

    public ThereaboutException(HttpStatusCode status, String reason) {
        super(status, reason);
    }
}
