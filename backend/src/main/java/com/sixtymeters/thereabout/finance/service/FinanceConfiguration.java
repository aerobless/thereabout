package com.sixtymeters.thereabout.finance.service;

import java.time.*;
import org.springframework.context.annotation.*;

@Configuration
public class FinanceConfiguration {
  @Bean
  public Clock financeClock() {
    return Clock.system(ZoneId.of("Europe/Zurich"));
  }
}
