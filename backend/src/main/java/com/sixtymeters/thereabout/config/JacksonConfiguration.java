package com.sixtymeters.thereabout.config;

import com.sixtymeters.thereabout.generated.model.GenHealthMetricDataInner;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Configuration
public class JacksonConfiguration {

    @Bean
    public JsonMapperBuilderCustomizer healthObjectMapperCustomizer() {
        return builder -> builder.addModule(createHealthDeserializationModule());
    }

    private static SimpleModule createHealthDeserializationModule() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(GenHealthMetricDataInner.class, new HealthMetricDataDeserializer());
        module.addDeserializer(OffsetDateTime.class, new FlexibleOffsetDateTimeDeserializer());
        module.addDeserializer(LocalDate.class, new FlexibleLocalDateDeserializer());
        return module;
    }
}
