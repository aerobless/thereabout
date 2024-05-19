package com.sixtymeters.thereabout.domain;

import com.sixtymeters.thereabout.model.ConfigurationEntity;
import com.sixtymeters.thereabout.model.ConfigurationKey;
import com.sixtymeters.thereabout.model.ConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigurationService {

    private final ConfigurationRepository configurationRepository;

    private final Map<ConfigurationKey, String> configurationCache = new HashMap<>();

    public String getThereaboutApiKey() {
        return configurationCache.computeIfAbsent(ConfigurationKey.THEREABOUT_API_KEY, k ->
                getConfiguration(ConfigurationKey.THEREABOUT_API_KEY).orElseGet(this::persistNewThereaboutApiKey));
    }

    private Optional<String> getConfiguration(ConfigurationKey key) {
        return configurationRepository.findById(key).map(ConfigurationEntity::getConfigValue);
    }

    private String persistNewThereaboutApiKey() {
        ConfigurationEntity configurationEntity = ConfigurationEntity.builder()
                .configKey(ConfigurationKey.THEREABOUT_API_KEY)
                .configValue(UUID.randomUUID().toString())
                .build();
        return configurationRepository.save(configurationEntity).getConfigValue();
    }

}
