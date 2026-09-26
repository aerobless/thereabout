package com.sixtymeters.thereabout.finance.data;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface FinancePostingRepository extends JpaRepository<FinancePostingEntity, Long> {
  boolean existsByAccountId(Long accountId);
}
