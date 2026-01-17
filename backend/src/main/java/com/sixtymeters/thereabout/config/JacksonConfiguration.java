package com.sixtymeters.thereabout.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.sixtymeters.thereabout.generated.model.GenHealthMetricDataInner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Configuration
public class JacksonConfiguration {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer healthObjectMapperCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addDeserializer(GenHealthMetricDataInner.class, new HealthMetricDataDeserializer());
            module.addDeserializer(OffsetDateTime.class, new FlexibleOffsetDateTimeDeserializer());
            module.addDeserializer(LocalDate.class, new FlexibleLocalDateDeserializer());
            builder.modulesToInstall(module);
        };
    }
}
