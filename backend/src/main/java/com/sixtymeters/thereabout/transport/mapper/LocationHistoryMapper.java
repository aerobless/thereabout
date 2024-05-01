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

    @Named("localDateTimeToOffsetDateTime")
    default OffsetDateTime localDateTimeToOffsetDateTime(LocalDateTime localDateTime) {
        return localDateTime == null ? null : OffsetDateTime.of(localDateTime, ZoneOffset.UTC);
    }
}
