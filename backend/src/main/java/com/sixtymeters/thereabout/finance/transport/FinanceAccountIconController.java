package com.sixtymeters.thereabout.finance.transport;

import static com.sixtymeters.thereabout.finance.domain.FinanceRules.missing;

import com.sixtymeters.thereabout.finance.service.AccountService;
import com.sixtymeters.thereabout.shared.icons.WebsiteIconService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class FinanceAccountIconController {
  private final AccountService accounts;
  private final com.sixtymeters.thereabout.access.UserContext users;
  private final WebsiteIconService icons;

  @GetMapping("/api/finances/accounts/{id}/icon")
  public ResponseEntity<byte[]> icon(@PathVariable long id) {
    String website = accounts.requireAccount(users.current(), id).getWebsiteUrl();
    if (website == null) throw missing("Account website");
    var icon = icons.icon(website).orElseThrow(() -> missing("Website icon"));
    return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).contentType(MediaType.parseMediaType(icon.type()))
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", "default-src 'none'; sandbox")
        .body(icon.bytes());
  }
}
