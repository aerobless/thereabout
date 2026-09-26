package com.sixtymeters.thereabout.finance.data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** An externally supplied dated reference rate; never a booked transaction amount. */
public record ReferenceRate(String from, String to, LocalDate date, BigDecimal rate) {}
