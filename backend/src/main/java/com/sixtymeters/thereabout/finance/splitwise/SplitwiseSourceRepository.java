package com.sixtymeters.thereabout.finance.splitwise;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SplitwiseSourceRepository extends JpaRepository<SplitwiseSource, String>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<SplitwiseSource> {
  List<SplitwiseSource> findByTransactionId(long id);
  List<SplitwiseSource> findByGroupId(long groupId);
}
