package com.seuapp.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({PolicyProperties.class, ExternalSimProperties.class})
public class AppConfig {}
