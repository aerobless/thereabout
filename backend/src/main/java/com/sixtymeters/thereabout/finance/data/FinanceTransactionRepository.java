package com.sixtymeters.thereabout.finance.data;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface FinanceTransactionRepository
    extends JpaRepository<FinanceTransactionEntity, Long> {
  @EntityGraph(attributePaths = "postings")
  @Query("select distinct t from FinanceTransactionEntity t order by t.id")
  List<FinanceTransactionEntity> findAllWithPostings();
}
