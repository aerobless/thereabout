package com.sixtymeters.thereabout.finance.splitwise;

import com.sixtymeters.thereabout.generated.model.GenFinanceTransaction;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Called inside the ledger's write lock, so a racing source update cannot undo a local edit. */
@Service @RequiredArgsConstructor
public class SplitwiseLocalChanges {
  private final SplitwiseSourceRepository sources;
  public void changed(GenFinanceTransaction before, GenFinanceTransaction after) {
    if (before == null) return;
    if (!fields(before).equals(fields(after))) detach(after.getId());
    else for (var source : sources.findByTransactionId(after.getId())) {
      // A no-op save still advances the ledger version, but is not a local override.
      if (!source.isLocallyManaged() && Objects.equals(source.getAppliedVersion(), before.getVersion())) {
        source.setAppliedVersion(after.getVersion());
        sources.saveAndFlush(source);
      }
    }
  }
  public void detach(long transaction) {
    for (var source : sources.findByTransactionId(transaction)) {
      source.setLocallyManaged(true);
      source.setState("LOCAL");
      source.setMessage("Changed in Thereabout; automatic updates are disabled.");
      sources.saveAndFlush(source);
    }
  }
  private List<Object> fields(GenFinanceTransaction t) {
    return Arrays.asList(t.getType(), t.getEffect(), t.getDescription(), t.getOccurredAt(), t.getCategoryId(),
        Objects.toString(t.getNotes(), ""), Objects.toString(t.getExternalReference(), ""), t.getDeleted(),
        t.getSourceAccountId(), t.getDestinationAccountId(), t.getSourceAmount(), t.getDestinationAmount(),
        t.getSourceCurrency(), t.getDestinationCurrency(), t.getForeignAmount(), t.getForeignCurrency());
  }
}
