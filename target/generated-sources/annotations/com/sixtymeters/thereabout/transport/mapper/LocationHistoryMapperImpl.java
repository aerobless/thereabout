package com.sixtymeters.thereabout.transport.mapper;

import com.sixtymeters.thereabout.generated.model.GenLocationHistoryEntry;
import com.sixtymeters.thereabout.model.LocationEntry;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2024-04-18T19:32:33+0200",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.1 (Eclipse Adoptium)"
)
public class LocationHistoryMapperImpl implements LocationHistoryMapper {

    @Override
    public GenLocationHistoryEntry map(LocationEntry locationEntry) {
        if ( locationEntry == null ) {
            return null;
        }

        GenLocationHistoryEntry.GenLocationHistoryEntryBuilder genLocationHistoryEntry = GenLocationHistoryEntry.builder();

        genLocationHistoryEntry.timestamp( localDateTimeToOffsetDateTime( locationEntry.getTimestamp() ) );
        genLocationHistoryEntry.longitude( locationEntry.getLongitude() );
        genLocationHistoryEntry.latitude( locationEntry.getLatitude() );

        return genLocationHistoryEntry.build();
    }
}
