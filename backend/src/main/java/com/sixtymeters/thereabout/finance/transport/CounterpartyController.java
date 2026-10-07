package com.sixtymeters.thereabout.finance.transport;

import com.sixtymeters.thereabout.access.UserContext;
import com.sixtymeters.thereabout.finance.service.CounterpartyService;
import com.sixtymeters.thereabout.generated.model.*;
import com.sixtymeters.thereabout.shared.icons.WebsiteIconService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import static com.sixtymeters.thereabout.finance.domain.FinanceRules.missing;

@RestController
@RequestMapping("/api/finances/counterparties")
@RequiredArgsConstructor
public class CounterpartyController {
  private final CounterpartyService counterparties;
  private final UserContext users;
  private final WebsiteIconService icons;
  @GetMapping public GenFinanceCounterpartyPage list(@Valid @ModelAttribute GenFinanceCounterpartyQuery query) { return counterparties.list(users.current(), query); }
  @GetMapping("/{id}") public GenFinanceCounterparty get(@PathVariable long id) { return counterparties.get(users.current(), id); }
  @PutMapping("/{id}") public GenFinanceCounterparty save(@PathVariable long id, @Valid @RequestBody GenFinanceCounterpartyInput input) { return counterparties.save(users.current(), id, input); }
  @PostMapping("/merge/preview") public GenFinanceCounterpartyMergeResult preview(@Valid @RequestBody GenFinanceCounterpartyMergePreviewInput input) { return counterparties.preview(users.current(), input); }
  @PostMapping("/merge") public GenFinanceCounterpartyMergeResult merge(@Valid @RequestBody GenFinanceCounterpartyMergeInput input) { return counterparties.merge(users.current(), input); }
  @GetMapping("/{id}/icon") public ResponseEntity<byte[]> icon(@PathVariable long id) {
    var c = counterparties.get(users.current(), id);
    if (c.getWebsiteUrl() == null) throw missing("Counterparty website");
    var icon = icons.icon(c.getWebsiteUrl()).orElseThrow(() -> missing("Website icon"));
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(icon.type()))
        .header("X-Content-Type-Options", "nosniff").header("Content-Security-Policy", "default-src 'none'; sandbox").body(icon.bytes());
  }
}
