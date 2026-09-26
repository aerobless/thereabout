package com.sixtymeters.thereabout.finance.data;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface FinanceRateRepository extends JpaRepository<FinanceRateEntity, Long> {
  Optional<FinanceRateEntity> findByFromCurrencyAndToCurrencyAndRateDateAndSource(
      String from, String to, LocalDate date, String source);
}
