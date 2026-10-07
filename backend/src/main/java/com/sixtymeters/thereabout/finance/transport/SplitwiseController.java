package com.sixtymeters.thereabout.finance.transport;

import com.sixtymeters.thereabout.finance.splitwise.SplitwiseService;
import com.sixtymeters.thereabout.generated.model.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/finances/configuration/splitwise") @RequiredArgsConstructor
public class SplitwiseController {
  private final SplitwiseService splitwise;
  private <T> ResponseEntity<T> response(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
  @GetMapping("/settings") public ResponseEntity<GenSplitwiseSettings> settings() { return response(splitwise.settings()); }
  @PutMapping("/settings") public ResponseEntity<GenSplitwiseSettings> save(@Valid @RequestBody GenSplitwiseSettingsInput input) { return response(splitwise.save(input)); }
  @PutMapping("/categories") public ResponseEntity<GenSplitwiseSettings> categories(@Valid @RequestBody GenSplitwiseCategorySaveInput input) { return response(splitwise.saveCategories(input)); }
  @PostMapping("/test") public ResponseEntity<GenSplitwiseCatalog> test() { return response(splitwise.test()); }
  @GetMapping("/catalog") public ResponseEntity<GenSplitwiseCatalog> catalog() { return response(splitwise.catalog()); }
  @PostMapping("/preview") public ResponseEntity<GenSplitwiseStatus> preview() { return response(splitwise.preview()); }
  @PostMapping("/initialize") public ResponseEntity<GenSplitwiseStatus> initialize(@Valid @RequestBody GenSplitwiseInitializeInput input) { return response(splitwise.initialize(input)); }
  @PostMapping("/sync") public ResponseEntity<GenSplitwiseStatus> sync() { return response(splitwise.sync()); }
  @GetMapping("/status") public ResponseEntity<GenSplitwiseStatus> status() { return response(splitwise.status()); }
  @PostMapping("/resolve") public ResponseEntity<GenSplitwiseSourceDetail> resolve(@Valid @RequestBody GenSplitwiseResolveInput input) { return response(splitwise.resolve(input)); }
}
