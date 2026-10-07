package com.sixtymeters.thereabout.ai;

import com.openai.models.ReasoningEffort;
import com.sixtymeters.thereabout.generated.model.GenOpenAiUseCase;
import java.util.List;

/** Application choices are code-reviewed, never inferred from credentials or editable settings. */
public final class AiUseCases {
  private AiUseCases() {}
  public record UseCase(String id, String name, String model, ReasoningEffort reasoning) {
    GenOpenAiUseCase metadata() {
      return new GenOpenAiUseCase().id(id).name(name).model(model).reasoning(reasoning.asString());
    }
  }
  public static final UseCase TRANSACTION_IMPORT = new UseCase("transaction-import", "Transaction import", "gpt-6-luna", ReasoningEffort.MEDIUM);
  public static List<GenOpenAiUseCase> metadata() { return List.of(TRANSACTION_IMPORT.metadata()); }
}
