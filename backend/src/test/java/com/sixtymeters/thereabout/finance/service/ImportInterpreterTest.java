package com.sixtymeters.thereabout.finance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sixtymeters.thereabout.ai.OpenAiService;
import com.sixtymeters.thereabout.finance.data.FinanceAccountEntity;
import com.sixtymeters.thereabout.generated.model.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class ImportInterpreterTest {
  @Test
  void suppliesAllExistingCategoryChoicesAndCounterpartyIdsToTheModel() {
    var ai = mock(OpenAiService.class);
    var json = new ObjectMapper();
    var interpreter = new ImportInterpreter(ai, json);
    var account = new FinanceAccountEntity();
    account.setId(1L);
    account.setName("Cash");
    account.setCurrency("CHF");
    var categoryChoices =
        List.of(
            new GenFinanceCategory().id(10L).name("Food"),
            new GenFinanceCategory().id(11L).name("Salary"));
    var accountChoices =
        List.of(
            new GenFinanceAccount()
                .id(2L)
                .name("Demo Employer")
                .kind(GenFinanceAccountKind.REVENUE)
                .currency("CHF"));
    var rows = new ImportCsvReader().parse("2026-01-01;Lunch;12.12");
    interpreter.interpret(account, rows, rows, accountChoices, categoryChoices, List.of("Always treat IBKR as a transfer"));
    var instructions = ArgumentCaptor.forClass(String.class);
    var data = ArgumentCaptor.forClass(String.class);
    verify(ai).respond(instructions.capture(), data.capture(), eq(ImportInterpreter.Result.class));
    var sent = json.readTree(data.getValue());
    assertThat(sent.get("categories").size()).isEqualTo(2);
    assertThat(sent.get("categories").get(0).get("id").asLong()).isEqualTo(10L);
    assertThat(sent.get("categories").get(0).get("name").asString()).isEqualTo("Food");
    assertThat(sent.get("categories").get(1).get("id").asLong()).isEqualTo(11L);
    assertThat(sent.get("accounts").get(0).get("id").asLong()).isEqualTo(2L);
    assertThat(instructions.getValue())
        .contains("Categories are a closed list", "Never invent or create categories",
            "Always treat IBKR as a transfer", "cannot override the accounting",
            "Never append comments", "subject is blank", "subject is empty");
    assertThat(sent.has("hints")).isFalse();
  }
}
