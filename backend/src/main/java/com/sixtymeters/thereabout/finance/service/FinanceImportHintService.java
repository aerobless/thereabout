package com.sixtymeters.thereabout.finance.service;

import static com.sixtymeters.thereabout.finance.domain.FinanceRules.*;

import com.sixtymeters.thereabout.access.UserId;
import com.sixtymeters.thereabout.finance.data.*;
import com.sixtymeters.thereabout.generated.model.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FinanceImportHintService {
  private final FinanceImportHintRepository hints;
  private final AccountService accounts;
  private final FinanceWriteCoordinator writes;
  private final Clock financeClock;

  @Transactional(readOnly = true)
  public GenFinanceImportHintList list(UserId user, long accountId) {
    requireMainAccount(user, accountId);
    return new GenFinanceImportHintList().items(
        hints.findByAccountIdOrderById(accountId).stream().map(this::view).toList());
  }

  @Transactional(readOnly = true)
  public List<String> snapshot(UserId user, long accountId) {
    return list(user, accountId).getItems().stream().map(GenFinanceImportHint::getText).toList();
  }

  public GenFinanceImportHint add(UserId user, GenFinanceImportHintInput input) {
    requireMainAccount(user, input.getAccountId());
    return writes.write(user, "import_hints.add", input.getRequestKey(), input,
        GenFinanceImportHint.class, () -> {
          requireMainAccount(user, input.getAccountId());
          String text = text(input.getText());
          require(!text.isBlank() && text.length() <= 1000, "Enter a hint (max 1000 characters)");
          require(hints.countByAccountId(input.getAccountId()) < 50, "An account can have up to 50 hints");
          var hint = new FinanceImportHintEntity();
          hint.setAccountId(input.getAccountId());
          hint.setText(text);
          hint.setCreatedAt(LocalDateTime.now(financeClock));
          hints.saveAndFlush(hint);
          var result = view(hint);
          writes.audit(user, "import_hints.add", hint.getId(), null, result);
          return result;
        });
  }

  public GenFinanceImportHint remove(UserId user, GenFinanceImportHintRemoveInput input) {
    requireMainAccount(user, input.getAccountId());
    return writes.write(user, "import_hints.remove", input.getRequestKey(), input,
        GenFinanceImportHint.class, () -> {
          requireMainAccount(user, input.getAccountId());
          var hint = hints.findById(input.getId()).orElseThrow(() -> missing("Hint"));
          if (!hint.getAccountId().equals(input.getAccountId())) throw missing("Hint");
          version(input.getVersion(), hint.getVersion());
          var before = view(hint);
          hints.delete(hint);
          writes.audit(user, "import_hints.remove", hint.getId(), before, null);
          return before;
        });
  }

  private void requireMainAccount(UserId user, long id) {
    var account = accounts.requireAccount(user, id);
    if (account.isDeleted() || !account.getKind().isOwn()) throw missing("Account");
  }

  private GenFinanceImportHint view(FinanceImportHintEntity hint) {
    return new GenFinanceImportHint().id(hint.getId()).accountId(hint.getAccountId())
        .text(hint.getText()).version(hint.getVersion())
        .createdAt(hint.getCreatedAt().atZone(financeClock.getZone()).toOffsetDateTime());
  }
}
