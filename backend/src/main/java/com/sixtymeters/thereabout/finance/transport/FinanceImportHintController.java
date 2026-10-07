package com.sixtymeters.thereabout.finance.transport;

import com.sixtymeters.thereabout.access.UserContext;
import com.sixtymeters.thereabout.finance.service.FinanceImportHintService;
import com.sixtymeters.thereabout.generated.model.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/finances/accounts/{accountId}/import-hints")
@RequiredArgsConstructor
public class FinanceImportHintController {
  private final FinanceImportHintService hints;
  private final UserContext users;

  @GetMapping
  public GenFinanceImportHintList list(@PathVariable long accountId) {
    return hints.list(users.current(), accountId);
  }

  @PostMapping
  public GenFinanceImportHint add(@PathVariable long accountId, @Valid @RequestBody GenFinanceImportHintInput input) {
    return hints.add(users.current(), input.accountId(accountId));
  }

  @DeleteMapping("/{hintId}")
  public GenFinanceImportHint remove(@PathVariable long accountId, @PathVariable long hintId,
      @Valid @RequestBody GenFinanceImportHintRemoveInput input) {
    return hints.remove(users.current(), input.accountId(accountId).id(hintId));
  }
}
