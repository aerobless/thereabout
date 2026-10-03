package com.sixtymeters.thereabout.ai;

import com.sixtymeters.thereabout.generated.model.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/backend/api/v1/config/openai")
@RequiredArgsConstructor
public class OpenAiController {
  private final OpenAiService ai;

  @GetMapping
  public ResponseEntity<GenOpenAiSettings> get() {
    return uncached(ai.settings());
  }

  @PutMapping
  public ResponseEntity<GenOpenAiSettings> save(@Valid @RequestBody GenOpenAiSettingsInput input) {
    return uncached(ai.save(input));
  }

  @PostMapping("/test")
  public ResponseEntity<GenOpenAiTestResult> test() {
    return uncached(ai.test());
  }

  private <T> ResponseEntity<T> uncached(T result) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
  }
}
