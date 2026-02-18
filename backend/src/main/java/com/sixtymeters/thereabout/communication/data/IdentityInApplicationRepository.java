package com.sixtymeters.thereabout.communication.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IdentityInApplicationRepository extends JpaRepository<IdentityInApplicationEntity, Long> {

    Optional<IdentityInApplicationEntity> findByApplicationAndIdentifier(CommunicationApplication application, String identifier);

    List<IdentityInApplicationEntity> findByIdentityIsNull();

    List<IdentityInApplicationEntity> findByApplication(CommunicationApplication application);
}
