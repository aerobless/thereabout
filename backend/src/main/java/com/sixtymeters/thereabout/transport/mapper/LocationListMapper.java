package com.sixtymeters.thereabout.transport.mapper;

import com.sixtymeters.thereabout.generated.model.GenLocationHistoryList;
import com.sixtymeters.thereabout.model.location.LocationListEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(uses = LocationHistoryMapper.class)
public interface LocationListMapper {
    LocationListMapper INSTANCE = Mappers.getMapper(LocationListMapper.class);

    GenLocationHistoryList map(final LocationListEntity locationHistoryList);

    @Mapping(target = "id", ignore = true)
    LocationListEntity map(final GenLocationHistoryList genLocationHistoryList);
}