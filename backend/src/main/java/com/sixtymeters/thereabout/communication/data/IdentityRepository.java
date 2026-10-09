package com.sixtymeters.thereabout.communication.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdentityRepository extends JpaRepository<IdentityEntity, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<IdentityEntity> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select i from IdentityEntity i where i.id = :id")
    Optional<IdentityEntity> findForUpdateById(Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select i from IdentityEntity i where i.role = com.sixtymeters.thereabout.communication.data.UserRole.ADMIN order by i.id")
    java.util.List<IdentityEntity> lockAdmins();

    @org.springframework.data.jpa.repository.Query("select i from IdentityEntity i where trim(concat(i.firstName, ' ', i.lastName)) = :name")
    java.util.List<IdentityEntity> findByFullName(String name);

    default Optional<IdentityEntity> findUniqueByFullName(String name) {
        var matches = findByFullName(name == null ? "" : name.strip());
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    @org.springframework.data.jpa.repository.Query("select count(i) > 0 from IdentityEntity i where i.role is not null")
    boolean anyUserExists();
}
