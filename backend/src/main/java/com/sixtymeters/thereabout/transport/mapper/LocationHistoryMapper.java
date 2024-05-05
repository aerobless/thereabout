package com.sixtymeters.thereabout.transport.mapper;

import com.sixtymeters.thereabout.generated.model.GenLocationHistoryEntry;
import com.sixtymeters.thereabout.model.LocationHistoryEntry;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper
public interface LocationHistoryMapper {
    LocationHistoryMapper INSTANCE = Mappers.getMapper(LocationHistoryMapper.class);

    @Mapping(source = "timestamp", target = "timestamp", qualifiedByName = "localDateTimeToOffsetDateTime")
    GenLocationHistoryEntry map(final LocationHistoryEntry locationHistoryEntry);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "horizontalAccuracy", ignore = true)
    @Mapping(target = "verticalAccuracy", ignore = true)
    @Mapping(target = "altitude", ignore = true)
    @Mapping(target = "heading", ignore = true)
    @Mapping(target = "velocity", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "estimatedIsoCountryCode", ignore = true)
    @Mapping(source = "timestamp", target = "timestamp", qualifiedByName = "OffsetDateTimeToLocalDateTime")
    LocationHistoryEntry map(final GenLocationHistoryEntry locationHistoryEntry);

    @Named("localDateTimeToOffsetDateTime")
    default OffsetDateTime localDateTimeToOffsetDateTime(LocalDateTime localDateTime) {
        return localDateTime == null ? null : OffsetDateTime.of(localDateTime, ZoneOffset.UTC);
    }

    @Named("OffsetDateTimeToLocalDateTime")
    default LocalDateTime localDateTimeToOffsetDateTime(OffsetDateTime offsetDateTime) {
        return offsetDateTime == null ? null : offsetDateTime.toLocalDateTime();
    }
}
