package com.sixtymeters.thereabout.finance.data;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface FinanceValuationRepository extends JpaRepository<FinanceValuationEntity, Long> {
  boolean existsByTransactionId(Long transactionId);

  boolean existsByAccountIdAndReference(Long accountId, String reference);
}
