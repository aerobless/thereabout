package com.sixtymeters.thereabout.ai;

import static com.sixtymeters.thereabout.finance.domain.FinanceRules.*;

import com.openai.models.responses.ResponseCreateParams;
import com.sixtymeters.thereabout.client.data.*;
import com.sixtymeters.thereabout.generated.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SDK boundary shared by application AI features. Credentials never leave this service. */
@Service
@RequiredArgsConstructor
public class OpenAiService {
  private final ConfigurationRepository configuration;
  private final OpenAiClientFactory clients;
  private static final String DEFAULT_MODEL = "gpt-6-luna";

  @Transactional(readOnly = true)
  public GenOpenAiSettings settings() {
    return new GenOpenAiSettings()
        .configured(!value(ConfigurationKey.OPENAI_API_KEY, "").isBlank())
        .model(value(ConfigurationKey.OPENAI_MODEL, DEFAULT_MODEL));
  }

  @Transactional
  public GenOpenAiSettings save(GenOpenAiSettingsInput input) {
    String model = text(input.getModel());
    require(model.matches("[a-zA-Z0-9][a-zA-Z0-9._:-]{0,99}"), "Enter a model identifier");
    require(
        !(Boolean.TRUE.equals(input.getRemoveKey()) && !text(input.getApiKey()).isEmpty()),
        "Choose replacement or removal");
    if (Boolean.TRUE.equals(input.getRemoveKey()))
      configuration.deleteById(ConfigurationKey.OPENAI_API_KEY);
    else if (!text(input.getApiKey()).isEmpty()) {
      require(
          input.getApiKey().length() <= 2048 && !input.getApiKey().matches(".*\\s.*"),
          "Invalid API key");
      put(ConfigurationKey.OPENAI_API_KEY, input.getApiKey());
    }
    put(ConfigurationKey.OPENAI_MODEL, model);
    return settings();
  }

  public <T> T respond(String instructions, String data, Class<T> schema) {
    require(data.length() <= 250000, "CSV chunk is too large for AI interpretation");
    String key = value(ConfigurationKey.OPENAI_API_KEY, "");
    require(!key.isBlank(), "Configure an OpenAI API key before importing");
    // A fresh client snapshots current configuration for each bounded request.
    var client = clients.create(key);
    try {
      var params =
          ResponseCreateParams.builder()
              .model(value(ConfigurationKey.OPENAI_MODEL, DEFAULT_MODEL))
              .instructions(instructions)
              .input(data)
              .store(false)
              .maxOutputTokens(24000)
              .reasoning(
                  com.openai.models.Reasoning.builder()
                      .effort(com.openai.models.ReasoningEffort.MEDIUM)
                      .build())
              .text(schema)
              .build();
      var response = client.responses().create(params);
      require(
          response.rawResponse().incompleteDetails().isEmpty(),
          "AI response was incomplete; retry the import");
      return response.output().stream()
          .flatMap(item -> item.message().stream())
          .flatMap(message -> message.content().stream())
          .flatMap(content -> content.outputText().stream())
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("AI response had no structured result"));
    } finally {
      client.close();
    }
  }

  public static class ConnectionResult {
    public String message;
  }

  public GenOpenAiTestResult test() {
    try {
      respond(
          "Return a short connection acknowledgement.", "Connection test", ConnectionResult.class);
      return new GenOpenAiTestResult().success(true).message("Connection successful");
    } catch (RuntimeException ex) {
      return new GenOpenAiTestResult()
          .success(false)
          .message("Connection failed. Check the API key, model and OpenAI access.");
    }
  }

  private String value(ConfigurationKey key, String fallback) {
    return configuration.findById(key).map(ConfigurationEntity::getConfigValue).orElse(fallback);
  }

  private void put(ConfigurationKey key, String value) {
    configuration.save(ConfigurationEntity.builder().configKey(key).configValue(value).build());
  }
}
