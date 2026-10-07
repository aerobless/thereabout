package com.sixtymeters.thereabout.finance.data;

import static com.sixtymeters.thereabout.finance.domain.FinanceRules.*;
import com.sixtymeters.thereabout.generated.model.GenFinanceDateFilter;
import java.util.*;

final class FinanceDateSearch {
  private FinanceDateSearch() {}
  static String where(GenFinanceDateFilter filter, List<Object> args) {
    if (filter == null) return "";
    require(filter.getOperator() != null && filter.getRules() != null && !filter.getRules().isEmpty() && filter.getRules().size() <= 2, "Choose one or two complete date rules");
    var clauses = new ArrayList<String>();
    for (var rule : filter.getRules()) {
      require(rule != null && rule.getDate() != null && rule.getMode() != null, "Complete each date rule");
      var day = rule.getDate().atStartOfDay(); var next = day.plusDays(1);
      switch (rule.getMode().getValue()) {
        case "dateIs" -> { clauses.add("(t.occurred_at>=? AND t.occurred_at<?)"); args.add(day); args.add(next); }
        case "dateIsNot" -> { clauses.add("(t.occurred_at<? OR t.occurred_at>=?)"); args.add(day); args.add(next); }
        case "dateBefore" -> { clauses.add("t.occurred_at<?"); args.add(day); }
        case "dateAfter" -> { clauses.add("t.occurred_at>=?"); args.add(next); }
        case "dateOnOrBefore" -> { clauses.add("t.occurred_at<?"); args.add(next); }
        case "dateOnOrAfter" -> { clauses.add("t.occurred_at>=?"); args.add(day); }
        default -> throw new IllegalArgumentException("Unknown date operator");
      }
    }
    return " AND (" + String.join(filter.getOperator().getValue().equals("or") ? " OR " : " AND ", clauses) + ")";
  }
}
