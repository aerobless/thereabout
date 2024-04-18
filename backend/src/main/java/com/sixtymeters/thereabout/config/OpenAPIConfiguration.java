package com.sixtymeters.thereabout.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(servers = @Server(url = "/", description = "Default Server URL"))
@Configuration
public class OpenAPIConfiguration {
}
