package com.sixtymeters.thereabout.communication.transport.mapper;

import com.sixtymeters.thereabout.communication.data.IdentityInApplicationEntity;
import com.sixtymeters.thereabout.communication.data.MessageEntity;
import com.sixtymeters.thereabout.generated.model.GenMessage;
import com.sixtymeters.thereabout.generated.model.GenMessageParticipant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper
public interface MessageMapper {
    MessageMapper INSTANCE = Mappers.getMapper(MessageMapper.class);

    @Mapping(source = "timestamp", target = "timestamp", qualifiedByName = "localDateTimeToOffsetDateTime")
    @Mapping(source = "sender", target = "sender", qualifiedByName = "toMessageParticipant")
    @Mapping(source = "receiver", target = "receiver", qualifiedByName = "toMessageParticipant")
    GenMessage mapToGenMessage(MessageEntity entity);

    @Named("toMessageParticipant")
    default GenMessageParticipant toMessageParticipant(IdentityInApplicationEntity entity) {
        if (entity == null) return null;

        String name;
        BigDecimal identityId;

        if (entity.getIdentity() != null) {
            name = entity.getIdentity().getShortName();
            identityId = BigDecimal.valueOf(entity.getIdentity().getId());
        } else {
            name = entity.getIdentifier();
            identityId = null;
        }

        return GenMessageParticipant.builder()
                .name(name)
                .identityId(identityId)
                .build();
    }

    @Named("localDateTimeToOffsetDateTime")
    default OffsetDateTime localDateTimeToOffsetDateTime(LocalDateTime localDateTime) {
        return localDateTime == null ? null : OffsetDateTime.of(localDateTime, ZoneOffset.UTC);
    }
}
