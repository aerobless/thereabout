package com.sixtymeters.thereabout.finance.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface FinanceCounterpartyRepository extends JpaRepository<FinanceCounterpartyEntity, Long> {
  @Query("select distinct c from FinanceCounterpartyEntity c left join c.aliases a where c.mergedIntoId is null and (lower(trim(c.name))=:name or lower(trim(a))=:name)")
  List<FinanceCounterpartyEntity> matching(String name);
}
