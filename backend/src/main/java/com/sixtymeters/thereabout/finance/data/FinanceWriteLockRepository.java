package com.sixtymeters.thereabout.finance.data;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface FinanceWriteLockRepository extends JpaRepository<FinanceWriteLockEntity, Integer> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select l from FinanceWriteLockEntity l where l.id=1")
  FinanceWriteLockEntity acquire();
}
