package com.sixtymeters.thereabout.finance.service;

import static com.sixtymeters.thereabout.finance.domain.FinanceRules.*;

import com.sixtymeters.thereabout.access.UserId;
import com.sixtymeters.thereabout.finance.data.*;
import com.sixtymeters.thereabout.generated.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CategoryService {
  private final FinanceCategoryRepository categories;
  private final FinanceReadRepository reads;
  private final FinanceWriteCoordinator writes;

  public Long validate(Long id) {
    if (id == null || id == 0) return null;
    require(
        !categories.findById(id).orElseThrow(() -> missing("Category")).isDeleted(),
        "Deleted category");
    return id;
  }

  public GenFinanceCategoryResult save(UserId user, GenFinanceCategoryInput input) {
    return writes.write(user,
        "categories.save",
        input.getRequestKey(),
        input,
        GenFinanceCategoryResult.class,
        () -> {
          var category =
              input.getId() == null
                  ? new FinanceCategoryEntity()
                  : categories.findById(input.getId()).orElseThrow(() -> missing("Category"));
          GenFinanceCategory before =
              category.getId() == null ? null : reads.category(category.getId());
          if (before != null) version(input.getVersion(), category.getVersion());
          category.setName(required(input.getName(), "name"));
          categories.saveAndFlush(category);
          var after = reads.category(category.getId());
          writes.audit(user, "categories.save", category.getId(), before, after);
          return new GenFinanceCategoryResult().category(after);
        });
  }
}
