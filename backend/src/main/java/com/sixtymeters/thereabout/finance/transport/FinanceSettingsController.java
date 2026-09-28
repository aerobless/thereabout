package com.sixtymeters.thereabout.finance.transport;

import com.sixtymeters.thereabout.finance.service.FinanceMcpKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Protected by the finance security chain; deliberately excluded from general frontend config and MCP tools. */
@RestController
@RequiredArgsConstructor
public class FinanceSettingsController {
  private final FinanceMcpKeyService keys;

  @GetMapping(value = "/api/finances/configuration/mcp-key", produces = "text/plain")
  public ResponseEntity<String> financeGetMcpKey() {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(keys.getKey());
  }
}
