package com.sixtymeters.thereabout.config;

import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.filter.ShallowEtagHeaderFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class FrontendRoutingConfiguration implements WebMvcConfigurer {

    @Value("${thereabout.config.routing.frontend-path:#{'/'}}")
    private String frontendLocation;

    @Value("${thereabout.config.routing.frontend-path:#{'/'}}#{'index.html'}")
    @Getter
    private String defaultResource;

    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> shallowEtagHeaderFilter() {
        final var etagHeaderFilter = new ShallowEtagHeaderFilter();
        etagHeaderFilter.setWriteWeakETag(true);
        final var filterRegistrationBean = new FilterRegistrationBean<>(etagHeaderFilter);
        filterRegistrationBean.addUrlPatterns("/assets/*");
        filterRegistrationBean.setName("etagFilter");
        return filterRegistrationBean;
    }

    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        log.info("Enabling frontend routing configuration");

        registry.addResourceHandler("/**")
            .addResourceLocations("file:" + frontendLocation) // Ensure proper "file:" prefix for file paths
            .resourceChain(true)
            .addResolver(new PathResourceResolver() {
                @Override
                protected Resource getResource(@Nonnull final String resourcePath, @Nonnull final Resource location)
                        throws IOException {
                    final var requestedResource = location.createRelative(resourcePath);
                    if (requestedResource.exists() && requestedResource.isReadable()) {
                        return requestedResource;
                    } else {
                        log.info("Falling back to default resource: {}", defaultResource);
                        return new FileSystemResource(defaultResource);
                    }
                }
            });
    }

    @Override
    public void addViewControllers(final ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.setOrder(Ordered.HIGHEST_PRECEDENCE);
    }
}
