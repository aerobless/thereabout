package com.sixtymeters.thereabout.finance.data;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface FinancePostingRepository extends JpaRepository<FinancePostingEntity, Long> {
  @Query("select p from FinancePostingEntity p join fetch p.transaction t where p.accountId = :account and p.deleted = false and t.deleted = false and t.occurredAt >= :from and t.occurredAt <= :to")
  List<FinancePostingEntity> importMatches(long account, java.time.LocalDateTime from, java.time.LocalDateTime to);

  boolean existsByAccountId(Long accountId);
}
