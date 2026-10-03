package com.sixtymeters.thereabout.finance.data;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceImportSourceRepository
    extends JpaRepository<FinanceImportSourceEntity, Long> {
  List<FinanceImportSourceEntity> findByAccountId(Long accountId);
}
