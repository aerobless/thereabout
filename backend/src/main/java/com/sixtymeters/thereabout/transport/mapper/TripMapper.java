package com.sixtymeters.thereabout.transport.mapper;

import com.sixtymeters.thereabout.generated.model.GenTrip;
import com.sixtymeters.thereabout.model.TripEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface TripMapper {
    TripMapper INSTANCE = Mappers.getMapper(TripMapper.class);

    @Mapping(target = "visitedCountries", ignore = true)
    GenTrip mapToGenTrip(final TripEntity tripEntity);

    @Mapping(target = "id", ignore = true)
    TripEntity mapToTripEntity(final GenTrip genTrip);
}
