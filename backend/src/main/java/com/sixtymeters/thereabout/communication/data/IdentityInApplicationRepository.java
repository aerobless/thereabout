package com.sixtymeters.thereabout.communication.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdentityInApplicationRepository extends JpaRepository<IdentityInApplicationEntity, Long> {

    Optional<IdentityInApplicationEntity> findByApplicationAndIdentifier(String application, String identifier);
}
