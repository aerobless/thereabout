package com.sixtymeters.thereabout.finance.data;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface FinanceAccountRepository extends JpaRepository<FinanceAccountEntity, Long> {
  Optional<FinanceAccountEntity> findFirstByNameAndKindAndCurrencyAndDeletedFalseOrderByIdAsc(
      String name, AccountKind kind, String currency);
  java.util.List<FinanceAccountEntity> findByCounterpartyIdOrderByIdAsc(Long counterpartyId);
}
