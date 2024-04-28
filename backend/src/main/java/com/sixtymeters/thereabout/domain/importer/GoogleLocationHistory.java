package com.sixtymeters.thereabout.domain.importer;

import java.util.List;

public record GoogleLocationHistory(List<GoogleLocationEntry> locations) {
}
