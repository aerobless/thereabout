package com.sixtymeters.thereabout.communication.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdentityRepository extends JpaRepository<IdentityEntity, Long> {

    Optional<IdentityEntity> findByShortName(String shortName);
}
