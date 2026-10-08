package com.sixtymeters.thereabout.finance.data;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface FinanceValuationRepository extends JpaRepository<FinanceValuationEntity, Long> {
  boolean existsByTransactionId(Long transactionId);

  boolean existsByAccountIdAndReference(Long accountId, String reference);
  Optional<FinanceValuationEntity> findByTransactionId(Long transactionId);
  boolean existsByAccountIdAndReferenceAndIdNot(Long accountId, String reference, Long id);
  boolean existsByAccountIdAndDeletedFalseAndOccurredAtAfterAndIdNot(Long accountId, java.time.LocalDateTime date, Long id);
}
