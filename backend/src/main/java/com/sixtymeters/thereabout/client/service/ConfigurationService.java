package com.sixtymeters.thereabout.client.service;

import com.sixtymeters.thereabout.client.data.ConfigurationEntity;
import com.sixtymeters.thereabout.client.data.ConfigurationKey;
import com.sixtymeters.thereabout.client.data.ConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigurationService {
    private final ConfigurationRepository configurationRepository;
    private final Map<ConfigurationKey, String> configurationCache = new ConcurrentHashMap<>();

    public String getThereaboutApiKey() {
        return configurationCache.computeIfAbsent(ConfigurationKey.THEREABOUT_API_KEY, k ->
                getConfiguration(ConfigurationKey.THEREABOUT_API_KEY).orElseGet(this::persistNewThereaboutApiKey));
    }

    /** Ingestion clients send the key as a bearer token; older configurations send the bare key. */
    public boolean acceptsThereaboutApiKey(String authorization) {
        if (authorization == null) return false;
        String key = getThereaboutApiKey();
        byte[] presented = authorization.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(("Bearer " + key).getBytes(StandardCharsets.UTF_8), presented)
                | MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), presented);
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
