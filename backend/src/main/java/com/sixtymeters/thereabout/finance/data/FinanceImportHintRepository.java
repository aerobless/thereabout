package com.sixtymeters.thereabout.finance.data;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceImportHintRepository extends JpaRepository<FinanceImportHintEntity, Long> {
  List<FinanceImportHintEntity> findByAccountIdOrderById(long accountId);
  long countByAccountId(long accountId);
}
