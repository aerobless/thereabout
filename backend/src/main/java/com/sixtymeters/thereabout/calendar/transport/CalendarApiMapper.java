package com.sixtymeters.thereabout.calendar.transport;

import com.sixtymeters.thereabout.calendar.data.CalendarStore.Guest;
import com.sixtymeters.thereabout.calendar.service.CalendarOccurrences.Occurrence;
import com.sixtymeters.thereabout.generated.model.*;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

import java.time.*;

@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CalendarApiMapper {
    CalendarApiMapper INSTANCE = Mappers.getMapper(CalendarApiMapper.class);
    GenCalendarOccurrence occurrence(Occurrence source);
    GenCalendarGuest guest(Guest source);
    GenCalendarInfo calendar(CalendarController.CalendarInfo source);
    default OffsetDateTime offset(Instant instant) { return instant == null ? null : instant.atOffset(ZoneOffset.UTC); }
    default OffsetDateTime offset(String instant) { return instant == null ? null : OffsetDateTime.parse(instant); }
}
