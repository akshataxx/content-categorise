package com.app.categorise.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

@Configuration
@PropertySources({
        @PropertySource("classpath:application-secrets.properties"),
        @PropertySource("classpath:application.properties")
})
public class AppConfig {
    // Both property files are now loaded into the environment
}
