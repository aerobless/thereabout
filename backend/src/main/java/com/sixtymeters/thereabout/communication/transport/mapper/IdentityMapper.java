package com.sixtymeters.thereabout.communication.transport.mapper;

import com.sixtymeters.thereabout.communication.data.CommunicationApplication;
import com.sixtymeters.thereabout.communication.data.IdentityEntity;
import com.sixtymeters.thereabout.communication.data.IdentityInApplicationEntity;
import com.sixtymeters.thereabout.generated.model.GenIdentity;
import com.sixtymeters.thereabout.generated.model.GenIdentityInApplication;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface IdentityMapper {
    IdentityMapper INSTANCE = Mappers.getMapper(IdentityMapper.class);

    @Mapping(target = "identityInApplications", source = "identityInApplications")
    GenIdentity mapToGenIdentity(IdentityEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "identityInApplications", source = "identityInApplications")
    IdentityEntity mapToIdentityEntity(GenIdentity genIdentity);

    @Mapping(source = "application", target = "application", qualifiedByName = "enumToDisplayName")
    GenIdentityInApplication mapToGenIdentityInApplication(IdentityInApplicationEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "identity", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(source = "application", target = "application", qualifiedByName = "displayNameToEnum")
    IdentityInApplicationEntity mapToIdentityInApplicationEntity(GenIdentityInApplication genIdentityInApplication);

    List<GenIdentityInApplication> mapToGenIdentityInApplicationList(List<IdentityInApplicationEntity> entities);

    List<IdentityInApplicationEntity> mapToIdentityInApplicationEntityList(List<GenIdentityInApplication> genList);

    @Named("enumToDisplayName")
    default String enumToDisplayName(CommunicationApplication application) {
        return application == null ? null : application.getDisplayName();
    }

    @Named("displayNameToEnum")
    default CommunicationApplication displayNameToEnum(String displayName) {
        if (displayName == null) return null;
        for (CommunicationApplication app : CommunicationApplication.values()) {
            if (app.getDisplayName().equalsIgnoreCase(displayName) || app.name().equalsIgnoreCase(displayName)) {
                return app;
            }
        }
        throw new IllegalArgumentException("Unknown communication application: " + displayName);
    }
}
