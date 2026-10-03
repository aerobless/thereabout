package com.sixtymeters.thereabout.finance.service;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.charset.*;
import org.junit.jupiter.api.Test;

class ImportCsvReaderTest {
  private final ImportCsvReader reader = new ImportCsvReader();

  @Test
  void preservesQuotedMultilineBomsPreamblesAndExactNumbers() {
    var rows =
        reader.parse(
            "\ufeffStatement\n"
                + "Date;Description;Amount\n"
                + "03.10.2026;\"Coffee; shop\n"
                + "second line\";-12.123456789012345678901234\n");
    assertThat(rows).hasSize(3);
    assertThat(rows.get(2).cells())
        .containsExactly("03.10.2026", "Coffee; shop\nsecond line", "-12.123456789012345678901234");
    assertThat(rows.get(2).id()).isEqualTo("row-3");
    assertThat(ImportCsvReader.numbers("1'234,56")).contains(new BigDecimal("1234.56"));
    assertThat(ImportCsvReader.numbers("1,234"))
        .contains(new BigDecimal("1.234"), new BigDecimal("1234"));
  }

  @Test
  void supportsCommonEncodingsAndRejectsMalformedAndOversizedInputs() {
    String source = "Date,Description,Amount\n2026-01-01,Café,12.01";
    assertThat(reader.decode(source.getBytes(Charset.forName("windows-1252")))).isEqualTo(source);
    assertThat(reader.decode(source.getBytes(StandardCharsets.UTF_16))).isEqualTo(source);
    assertThat(reader.parse("date\tamount\n2026-01-01\t12.01").get(1).cells()).hasSize(2);
    assertThatThrownBy(() -> reader.parse("a,b\n\"unclosed,b"))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> reader.parse("a".repeat(ImportCsvReader.MAX_BYTES + 1)))
        .hasMessageContaining("2 MiB");
    assertThatThrownBy(() -> reader.parse("a,b\n".repeat(2022))).hasMessageContaining("2,000");
  }
}
