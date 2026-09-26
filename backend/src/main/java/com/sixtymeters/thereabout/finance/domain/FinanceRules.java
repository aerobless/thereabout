package com.sixtymeters.thereabout.finance.domain;

import com.sixtymeters.thereabout.config.ThereaboutException;
import java.math.BigDecimal;
import java.time.*;
import org.springframework.http.HttpStatus;

/** Shared boundary rules. Monetary arithmetic never uses floating point. */
public final class FinanceRules {
  private FinanceRules() {}

  public static void require(boolean valid, String message) {
    if (!valid) throw new ThereaboutException(HttpStatus.BAD_REQUEST, message);
  }

  public static void conflict(boolean valid, String message) {
    if (!valid) throw new ThereaboutException(HttpStatus.CONFLICT, message);
  }

  public static ThereaboutException missing(String resource) {
    return new ThereaboutException(HttpStatus.NOT_FOUND, resource + " not found");
  }

  public static String required(String value, String field) {
    require(
        value != null && !value.isBlank() && value.trim().length() <= 1024,
        field + " is required (max 1024 characters)");
    return value.trim();
  }

  public static String text(String value) {
    return value == null ? "" : value.trim();
  }

  public static BigDecimal decimal(String value) {
    require(
        value != null && value.matches("-?\\d{1,12}(\\.\\d{1,24})?"),
        "Amount must be a decimal string (12 integer, 24 fractional digits maximum)");
    return new BigDecimal(value);
  }

  public static String money(BigDecimal value) {
    return value == null ? null : value.stripTrailingZeros().toPlainString();
  }

  public static LocalDateTime dateTime(String value, LocalDateTime fallback, boolean endOfDay) {
    if (value == null || value.isBlank()) return fallback;
    try {
      return value.length() == 10
          ? LocalDate.parse(value).atTime(endOfDay ? LocalTime.MAX : LocalTime.MIN)
          : LocalDateTime.parse(value.replace(' ', 'T'));
    } catch (DateTimeException ex) {
      throw new ThereaboutException(
          HttpStatus.BAD_REQUEST, "Use an ISO date or local date-time (Europe/Zurich)");
    }
  }

  public static LocalDate date(String value) {
    try {
      return LocalDate.parse(required(value, "date"));
    } catch (DateTimeException ex) {
      throw new ThereaboutException(HttpStatus.BAD_REQUEST, "Use YYYY-MM-DD");
    }
  }

  public static void version(Long expected, long actual) {
    conflict(expected != null && expected == actual, "Record changed; reload before saving");
  }

  public static int page(Integer value) {
    int page = value == null ? 0 : value;
    require(page >= 0 && page < 100000, "Invalid page");
    return page;
  }

  public static int pageSize(Integer value) {
    int size = value == null ? 50 : value;
    require(size > 0 && size <= 200, "Page size must be 1 to 200");
    return size;
  }
}
