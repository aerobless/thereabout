package com.sixtymeters.thereabout.finance.service;

import static com.sixtymeters.thereabout.finance.domain.FinanceRules.*;

import java.io.*;
import java.math.BigDecimal;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.stereotype.Component;

/** Parsing establishes immutable evidence before any model interpretation. */
@Component
public class ImportCsvReader {
  public static final int MAX_BYTES = 2 * 1024 * 1024, MAX_ROWS = 2000;

  public record Row(String id, List<String> cells) {}

  public String decode(byte[] bytes) {
    require(bytes.length > 0 && bytes.length <= MAX_BYTES, "Select a CSV up to 2 MiB");
    try {
      if (bytes.length >= 2
          && ((bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe)
              || (bytes[0] == (byte) 0xfe && bytes[1] == (byte) 0xff)))
        return StandardCharsets.UTF_16
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString();
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException ex) {
      return Charset.forName("windows-1252").decode(ByteBuffer.wrap(bytes)).toString();
    }
  }

  public List<Row> parse(String text) {
    require(
        text != null && text.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES,
        "CSV exceeds 2 MiB");
    String value = text.startsWith("\ufeff") ? text.substring(1) : text;
    require(!value.contains("\0"), "Invalid CSV encoding");
    char delimiter = detectDelimiter(value);
    try (var parser =
        CSVFormat.DEFAULT
            .builder()
            .setDelimiter(delimiter)
            .setIgnoreEmptyLines(true)
            .get()
            .parse(new StringReader(value))) {
      var rows = new ArrayList<Row>();
      for (var record : parser) {
        var cells = record.toList();
        if (cells.stream().allMatch(String::isBlank)) continue;
        require(
            cells.size() <= 100 && cells.stream().allMatch(c -> c.length() <= 32768),
            "CSV row or field is too large");
        require(
            rows.size() < MAX_ROWS + 20,
            "CSV exceeds 2,000 data rows (plus up to 20 header/preamble rows)");
        rows.add(new Row("row-" + (rows.size() + 1), List.copyOf(cells)));
      }
      require(!rows.isEmpty(), "CSV is empty");
      return List.copyOf(rows);
    } catch (IOException | UncheckedIOException ex) {
      throw new IllegalArgumentException("Malformed CSV", ex);
    }
  }

  private char detectDelimiter(String text) {
    char best = ',';
    int score = -1;
    for (char candidate : new char[] {',', ';', '\t', '|'}) {
      boolean quoted = false;
      int line = 0, count = 0, total = 0;
      for (int i = 0; i < text.length() && line < 20; i++) {
        char c = text.charAt(i);
        if (c == '"') {
          if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') i++;
          else quoted = !quoted;
        } else if (!quoted && c == candidate) count++;
        else if (!quoted && c == '\n') {
          total += count;
          count = 0;
          line++;
        }
      }
      total += count;
      if (total > score) {
        score = total;
        best = candidate;
      }
    }
    return best;
  }

  public static Set<java.time.LocalDate> dates(String evidence) {
    var result = new HashSet<java.time.LocalDate>();
    String s = evidence.trim();
    try {
      result.add(java.time.LocalDateTime.parse(s.replace(' ', 'T')).toLocalDate());
    } catch (java.time.DateTimeException ignored) {
    }
    for (String pattern :
        List.of(
            "uuuu-M-d",
            "d.M.uuuu",
            "d/M/uuuu",
            "M/d/uuuu",
            "d-M-uuuu",
            "d MMM uuuu",
            "MMM d, uuuu",
            "uuuuMMdd")) {
      try {
        result.add(
            java.time.LocalDate.parse(
                s,
                java.time.format.DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
                    .withResolverStyle(java.time.format.ResolverStyle.STRICT)));
      } catch (java.time.DateTimeException ignored) {
      }
    }
    return result;
  }

  /** Both decimal conventions are candidates; ambiguous values need review rather than rounding. */
  public static Set<BigDecimal> numbers(String evidence) {
    var result = new HashSet<BigDecimal>();
    String s =
        evidence.trim().replace("\u00a0", "").replace(" ", "").replace("'", "").replace("’", "");
    s =
        s.replaceAll("^[A-Za-z]{3}|[A-Za-z]{3}$", "")
            .replace("€", "")
            .replace("$", "")
            .replace("£", "");
    if (s.endsWith("-")) s = "-" + s.substring(0, s.length() - 1);
    if (s.startsWith("(") && s.endsWith(")")) s = "-" + s.substring(1, s.length() - 1);
    var candidates = new ArrayList<String>();
    if (s.matches("-?\\d{1,12}(\\.\\d{1,24})?")) candidates.add(s);
    int comma = s.lastIndexOf(','), dot = s.lastIndexOf('.');
    if (comma >= 0 && dot >= 0) {
      candidates.add(comma > dot ? s.replace(".", "").replace(',', '.') : s.replace(",", ""));
    } else if (comma >= 0) {
      if (s.matches("-?\\d+,\\d{1,24}")) candidates.add(s.replace(',', '.'));
      if (s.matches("-?\\d{1,3}(,\\d{3})+")) candidates.add(s.replace(",", ""));
    } else if (dot >= 0 && s.matches("-?\\d{1,3}(\\.\\d{3})+")) candidates.add(s.replace(".", ""));
    for (String n : candidates)
      if (n.matches("-?\\d{1,12}(\\.\\d{1,24})?")) result.add(new BigDecimal(n).abs());
    return result;
  }
}
