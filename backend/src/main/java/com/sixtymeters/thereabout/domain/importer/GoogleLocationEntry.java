package com.sixtymeters.thereabout.domain.importer;

import java.time.ZonedDateTime;

public record GoogleLocationEntry(ZonedDateTime timestamp, long latitudeE7, long longitudeE7, int accuracy, int verticalAccuracy, int altitude, int heading, int velocity, String deviceTag, String source) {
}
