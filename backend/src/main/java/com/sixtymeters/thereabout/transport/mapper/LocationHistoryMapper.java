package com.sixtymeters.thereabout.transport.mapper;

import com.sixtymeters.thereabout.generated.model.GenGeoJsonLocation;
import com.sixtymeters.thereabout.generated.model.GenLocationHistoryEntry;
import com.sixtymeters.thereabout.generated.model.GenSparseLocationHistoryEntry;
import com.sixtymeters.thereabout.model.LocationHistoryEntity;
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

    GenSparseLocationHistoryEntry mapToSparseEntry(final LocationHistoryEntity locationHistoryEntity);

    @Mapping(source = "timestamp", target = "timestamp", qualifiedByName = "localDateTimeToOffsetDateTime")
    GenLocationHistoryEntry map(final LocationHistoryEntity locationHistoryEntity);

    @Mapping(target = "ignoreEntry", ignore = true)
    @Mapping(target = "deviceTag", ignore = true)
    @Mapping(target = "sensorSource", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "estimatedIsoCountryCode", ignore = true)
    @Mapping(source = "timestamp", target = "timestamp", qualifiedByName = "OffsetDateTimeToLocalDateTime")
    LocationHistoryEntity map(final GenLocationHistoryEntry locationHistoryEntry);

    @Named("localDateTimeToOffsetDateTime")
    default OffsetDateTime localDateTimeToOffsetDateTime(LocalDateTime localDateTime) {
        return localDateTime == null ? null : OffsetDateTime.of(localDateTime, ZoneOffset.UTC);
    }

    @Named("OffsetDateTimeToLocalDateTime")
    default LocalDateTime localDateTimeToOffsetDateTime(OffsetDateTime offsetDateTime) {
        return offsetDateTime == null ? null : offsetDateTime.toLocalDateTime();
    }

    @Mapping(target = "ignoreEntry", ignore = true)
    @Mapping(target = "deviceTag", ignore = true)
    @Mapping(target = "sensorSource", ignore = true)
    @Mapping(target = "note", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "estimatedIsoCountryCode", ignore = true)
    @Mapping(target = "timestamp", source = "properties.timestamp", qualifiedByName = "OffsetDateTimeToLocalDateTime")
    @Mapping(target = "latitude", expression = "java(locationHistoryEntry.getGeometry().getCoordinates().get(1).doubleValue())")
    @Mapping(target = "longitude", expression = "java(locationHistoryEntry.getGeometry().getCoordinates().get(0).doubleValue())")
    @Mapping(target = "horizontalAccuracy", source = "properties.horizontalAccuracy")
    @Mapping(target = "verticalAccuracy", source = "properties.verticalAccuracy")
    @Mapping(target = "altitude", source = "properties.altitude")
    @Mapping(target = "heading", source = "properties.course")
    @Mapping(target = "velocity", source = "properties.speed")
    @Mapping(target = "source", expression = "java(LocationHistorySource.THEREABOUT_API)")
    LocationHistoryEntity map(final GenGeoJsonLocation locationHistoryEntry);
}
