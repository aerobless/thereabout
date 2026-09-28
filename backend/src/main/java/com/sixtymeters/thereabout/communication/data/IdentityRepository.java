package com.sixtymeters.thereabout.communication.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdentityRepository extends JpaRepository<IdentityEntity, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select i from IdentityEntity i where i.id = :id")
    Optional<IdentityEntity> findForUpdateById(Long id);

    Optional<IdentityEntity> findByShortName(String shortName);

    @org.springframework.data.jpa.repository.Query("select count(i) > 0 from IdentityEntity i where i.isUser = true")
    boolean anyUserExists();
}
