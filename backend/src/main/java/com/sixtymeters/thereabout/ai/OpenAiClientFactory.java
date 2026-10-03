package com.sixtymeters.thereabout.ai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class OpenAiClientFactory {
  public OpenAIClient create(String key) {
    return OpenAIOkHttpClient.builder()
        .apiKey(key)
        .timeout(Duration.ofSeconds(120))
        .maxRetries(1)
        .build();
  }
}
