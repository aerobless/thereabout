package com.sixtymeters.thereabout.calendar.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableScheduling
public class CalendarConfiguration {
    @Bean
    TransactionTemplate calendarTransactions(PlatformTransactionManager manager) { return new TransactionTemplate(manager); }
}
