package com.sixtymeters.thereabout.access;

/** An eligible person identity that owns personal data; never a contact or group. */
public record UserId(long value) {
    public UserId { if (value <= 0) throw new IllegalArgumentException("Positive user ID required"); }
}
