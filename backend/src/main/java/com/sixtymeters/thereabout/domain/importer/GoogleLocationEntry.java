package com.sixtymeters.thereabout.domain.importer;

import java.time.ZonedDateTime;

public record GoogleLocationEntry(ZonedDateTime timestamp, long latitudeE7, long longitudeE7, double accuracy) {
}
