package com.sixtymeters.thereabout.finance.transport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class FinanceOpenApiSchemasTest {
  @Test
  void suppliesSelfContainedMcpSchemasAcrossResourceFiles() throws Exception {
    var schemas = new FinanceOpenApiSchemas(new JsonMapper()).load();

    assertThat(schemas.size()).isEqualTo(53);
    assertThat(schemas.toString()).doesNotContain("\"$ref\"");
    assertThat(schemas.required("FinanceTransactionInput").at("/properties/sourceAmount/type").asString())
        .isEqualTo("string");
    assertThat(
            schemas
                .required("FinanceBulkCategoryInput")
                .at("/properties/items/items/properties/version/type")
                .asString())
        .isEqualTo("integer");
    assertThat(schemas.required("FinanceAccountInput").at("/properties/kind/enum").isArray())
        .isTrue();
  }
}
