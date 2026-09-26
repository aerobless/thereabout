package com.sixtymeters.thereabout.finance.data;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface FinanceCategoryRepository extends JpaRepository<FinanceCategoryEntity, Long> {
  List<FinanceCategoryEntity> findByDeletedFalseOrderByNameAsc();
}
